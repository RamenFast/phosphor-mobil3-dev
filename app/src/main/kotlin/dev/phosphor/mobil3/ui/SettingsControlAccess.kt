package dev.phosphor.mobil3.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected

internal val LocalSettingsControlAccess = staticCompositionLocalOf { false }

internal fun Modifier.settingsChoice(active: Boolean): Modifier = semantics {
    role = Role.Button
    selected = active
}

@Composable
internal fun Modifier.settingsFocusBorder(p: Palette): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }.drawBehind {
        if (focused) drawRect(p.accent, style = Stroke(2.dp.toPx()))
    }
}

internal class SettingsRangeAction(
    val value: Float,
    val minimum: Float,
    val maximum: Float,
    private val publish: (Float) -> Unit,
) {
    init {
        require(value.isFinite() && minimum.isFinite() && maximum.isFinite())
        require(minimum <= maximum && value in minimum..maximum)
    }

    fun set(requested: Float): Boolean {
        if (!requested.isFinite()) return false
        val next = requested.coerceIn(minimum, maximum)
        if (next == value) return false
        publish(next)
        return true
    }

    fun step(increase: Boolean): Boolean =
        set(value + (maximum - minimum) * if (increase) 0.01f else -0.01f)
}

internal fun Modifier.settingsRange(
    label: String,
    display: String,
    action: SettingsRangeAction,
): Modifier = semantics {
    contentDescription = label
    stateDescription = display
    progressBarRangeInfo = ProgressBarRangeInfo(action.value, action.minimum..action.maximum)
    setProgress { action.set(it) }
}.onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
        Key.DirectionRight, Key.DirectionUp -> { action.step(true); true }
        Key.DirectionLeft, Key.DirectionDown -> { action.step(false); true }
        Key.MoveHome -> { action.set(action.minimum); true }
        Key.MoveEnd -> { action.set(action.maximum); true }
        else -> false
    }
}.focusable()
