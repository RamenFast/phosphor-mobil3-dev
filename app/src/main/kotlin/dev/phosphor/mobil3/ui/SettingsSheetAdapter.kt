package dev.phosphor.mobil3.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Only Settings uses this animation and direct-input adapter. Legacy sheets keep their owner. */
internal class SettingsSheetDismiss(private val scope: CoroutineScope) {
    val gesture = SettingsGestureAdapter()
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var returning by mutableStateOf(false)
        private set
    var interrupted by mutableStateOf(false)
        private set
    private var returnJob: Job? = null
    private var revision = 0L

    fun observeInterruption() { interrupted = gesture.requiresReopen }

    fun follow(density: Float) {
        revision++
        returnJob?.cancel()
        returning = false
        offsetPx = gesture.offsetDp * density
    }

    fun returnToRest(reduced: Boolean) {
        val expected = ++revision
        returnJob?.cancel()
        if (reduced || offsetPx == 0f) {
            offsetPx = 0f
            returning = false
            return
        }
        returning = true
        returnJob = scope.launch {
            Animatable(offsetPx).animateTo(0f, tween(160)) {
                if (revision == expected) offsetPx = value
            }
            if (revision == expected) returning = false
        }
    }

    fun cancel(reduced: Boolean) {
        gesture.cancel()
        observeInterruption()
        if (!gesture.committed) returnToRest(reduced)
    }

    fun retire() {
        gesture.retire()
        revision++
        returnJob?.cancel()
        returning = false
    }

    fun nestedScroll(scroll: ScrollState, density: Float, onInput: () -> Unit): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) onInput()
                val consumed = gesture.reverse(available.y / density, source == NestedScrollSource.UserInput)
                if (consumed != 0f) follow(density)
                return Offset(0f, consumed * density)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f || available.y != 0f) onInput()
                val taken = gesture.remainder(
                    available.y / density, source == NestedScrollSource.UserInput, scroll.value == 0,
                    childConsumedDp = consumed.y / density,
                )
                if (taken != 0f) follow(density)
                return Offset(0f, taken * density)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                gesture.postFling()
                return Velocity.Zero
            }
        }
}

internal fun Modifier.settingsPointerObserver(
    owner: SettingsSheetDismiss,
    density: Float,
    geometryKey: Any,
    reduced: Boolean,
    onInput: () -> Unit,
    onClose: () -> Unit,
): Modifier = pointerInput(owner, density, geometryKey, reduced) {
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                onInput()
                val change = event.changes.firstOrNull()
                val pointers = event.changes.count { it.pressed || it.previousPressed }
                if (change == null) {
                    owner.cancel(reduced)
                } else {
                    owner.gesture.initial(
                        change.id.value, change.position.y / density, change.uptimeMillis,
                        change.pressed, change.previousPressed, pointers,
                        change.type == PointerType.Touch || change.type == PointerType.Stylus,
                    )
                    owner.observeInterruption()
                    if (change.pressed && !change.previousPressed) owner.follow(density)
                }
                val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                when (owner.gesture.final(finalEvent.changes.any { it.isConsumed })) {
                    SettingsDismissOwner.Release.CLOSE -> onClose()
                    SettingsDismissOwner.Release.RETURN -> owner.returnToRest(reduced)
                    SettingsDismissOwner.Release.NONE -> {
                        if (owner.gesture.rawDp == 0f && owner.offsetPx > 0f && !owner.gesture.committed) {
                            owner.returnToRest(reduced)
                        }
                    }
                }
            }
        }
    } finally {
        owner.cancel(reduced)
    }
}

internal fun Modifier.settingsHeaderDrag(
    owner: SettingsSheetDismiss,
    density: Float,
    reduced: Boolean,
): Modifier = pointerInput(owner, density, reduced) {
    detectVerticalDragGestures(
        onDragCancel = { owner.cancel(reduced) },
        // The ancestor's final pointer pass owns release and measured velocity.
        onDragEnd = {},
    ) { change, delta ->
        val consumed = owner.gesture.header(delta / density)
        if (consumed != 0f) {
            change.consume()
            owner.follow(density)
        }
    }
}

/** Lives beside the screen's sheet switch, not inside SettingsSheet. */
internal class SettingsPresentationState(val scroll: ScrollState) {
    val owner = SettingsPresentationOwner()
    val anchors = SettingsAnchorAdapter(owner)
    var expanded by mutableStateOf(owner.expanded)
        private set
    var request by mutableStateOf<SettingsAnchorAdapter.Request?>(null)
        private set
    var viewport: LayoutCoordinates? = null
    private val headers = mutableMapOf<SettingsSectionId, LayoutCoordinates>()

    private fun viewportY(section: SettingsSectionId): Int? {
        val parent = viewport?.takeIf { it.isAttached } ?: return null
        val header = headers[section]?.takeIf { it.isAttached } ?: return null
        return parent.localPositionOf(header, Offset.Zero).y.roundToInt()
    }

    fun header(section: SettingsSectionId, coordinates: LayoutCoordinates) { headers[section] = coordinates }

    fun toggle(section: SettingsSectionId) {
        request = anchors.toggle(section, viewportY(section) ?: 0)
        expanded = owner.expanded
    }

    fun cancelAnchor() {
        anchors.cancel()
        request = null
    }

    fun retire() {
        cancelAnchor()
        owner.rememberScroll(scroll.value)
        viewport = null
        headers.clear()
    }

    fun applyAnchor(expected: SettingsAnchorAdapter.Request) {
        val y = viewportY(expected.section) ?: return
        val correction = anchors.correction(expected, y, scroll.value, scroll.maxValue) ?: return
        // No suspension between ticket validation and the actual ScrollState mutation.
        scroll.dispatchRawDelta(correction.toFloat())
        owner.rememberScroll(scroll.value)
        if (request === expected) request = null
    }
}

@Composable
internal fun rememberSettingsPresentationState(): SettingsPresentationState {
    val scroll = rememberScrollState()
    return remember(scroll) { SettingsPresentationState(scroll) }
}

/** A fresh placement receipt precedes the one-frame deferred, revision-checked correction. */
internal fun Modifier.settingsAnchorLayout(
    state: SettingsPresentationState,
    request: SettingsAnchorAdapter.Request?,
    scope: CoroutineScope,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
        if (request != null && state.anchors.laidOut(request)) {
            scope.launch {
                withFrameNanos { }
                state.applyAnchor(request)
            }
        }
    }
}

@Composable
internal fun SettingsExpandableSection(
    id: SettingsSectionId,
    title: String,
    summary: String,
    glyph: SettingsGlyph,
    p: Palette,
    presentation: SettingsPresentationState,
    content: @Composable () -> Unit,
) {
    val expanded = id in presentation.expanded
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(Dim.hairline).background(p.lineStrong))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .onGloballyPositioned { presentation.header(id, it) }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse $title" else "Expand $title") {
                    presentation.toggle(id)
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsGlyphIcon(glyph, p, 20.dp)
            Column(Modifier.weight(1f)) {
                Mono(title, p.ink, Type.data, maxLines = Int.MAX_VALUE)
                Prose(summary, p.ink2, modifier = Modifier.padding(top = 4.dp))
            }
            Mono(if (expanded) "▴" else "▾", p.ink2, modifier = Modifier.clearAndSetSemantics { })
        }
        if (expanded) {
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}
