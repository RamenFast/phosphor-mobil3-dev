package dev.phosphor.mobil3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.phosphor.mobil3.settings.appearance.AppearanceDocument
import dev.phosphor.mobil3.settings.appearance.AppearanceValue

/** One picker for every look: curated, the classic rooms and saved looks (REDESIGN §2). */
internal object LookTiles {
    enum class Kind { CURATED, ROOM, SAVED }
    data class Tile(val key: String, val label: String, val kind: Kind, val value: AppearanceValue, val room: Palette? = null)

    /** Curated order leads: the four looks Ben named first. */
    private val curatedOrder = listOf("curated:glass", "curated:amoled", "curated:dark", "curated:light")

    fun build(document: AppearanceDocument?, roomValue: (Palette) -> AppearanceValue): List<Tile> {
        val curated = curatedOrder.mapNotNull { id -> AppearanceDocument.CURATED.find { it.id == id } }
            .map { Tile(it.id, it.name, Kind.CURATED, it.value) }
        val curatedValues = curated.map { it.value }.toSet()
        val curatedNames = curated.map { it.label.lowercase() }.toSet()
        // A classic room identical to a curated look is the same look: show it once.
        // A different room with a taken name keeps its colors under a distinct name.
        val rooms = Rooms.mapNotNull { room ->
            val value = roomValue(room)
            if (value in curatedValues) null
            else Tile("legacy:${room.id}", if (room.label.lowercase() in curatedNames) "${room.label} classic" else room.label,
                Kind.ROOM, value, room)
        }
        val saved = document?.users.orEmpty().map { Tile(it.id, it.name, Kind.SAVED, it.value) }
        return curated + rooms + saved
    }

    fun active(tile: Tile, document: AppearanceDocument?, displayedRoomId: String): Boolean {
        val activeId = document?.activeId.orEmpty()
        return when {
            activeId.isNotEmpty() -> activeId == tile.key
            tile.kind == Kind.ROOM -> tile.room?.id == displayedRoomId
            else -> false
        }
    }

    val feelWords = listOf(ChromeCharacter.Carved to "carved", ChromeCharacter.Engraved to "engraved",
        ChromeCharacter.Annotated to "bench", ChromeCharacter.Glass to "glass")
    val motionWords = listOf(MotionFeel.Eased to "eased", MotionFeel.Cut to "cut",
        MotionFeel.Detented to "steps", MotionFeel.Springy to "spring")
    val cornerWords = listOf(0 to "sharp", 8 to "soft", 12 to "round")
    val labelWords = listOf(false to "plain", true to "part numbers")

    /** The nearest named corner, so a custom radius still shows a choice. */
    fun corner(radiusDp: Int): Int = cornerWords.map { it.first }.minBy { kotlin.math.abs(it - radiusDp) }
}

@Composable
fun LookSheet(
    state: ScopeUiState,
    p: Palette,
    reduced: Boolean,
    onPickRoom: (Palette) -> Unit,
    onPickLook: (String) -> Unit,
    onStyle: (StyleOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = p.sheetText()
    val document = state.appearanceDocument
    val tiles = remember(document) {
        LookTiles.build(document) { AppearancePalette.legacy(LegacyAppearanceInput(it.id)).value }
    }
    CompositionLocalProvider(LocalSettingsControlAccess provides true) {
    SheetHost(p, "LOOK", reduced, onDismiss, glyph = SettingsGlyph.Room) {
        Column(Modifier.verticalScroll(rememberScrollState(), overscrollEffect = null)) {
            GroupHeading("LOOKS", p, first = true)
            tiles.chunked(2).forEachIndexed { row, pair ->
                if (row > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { tile ->
                        LookTile(tile, LookTiles.active(tile, document, state.room.id), p, Modifier.weight(1f)) {
                            if (tile.kind == LookTiles.Kind.ROOM) tile.room?.let(onPickRoom) else onPickLook(tile.key)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            GroupHeading("STYLE", p)
            val style = state.appearanceStyle
            SettingChoice("feel", LookTiles.feelWords, style.character, p) { onStyle(StyleOverride(character = it)) }
            SettingChoice("motion", LookTiles.motionWords, style.motion, p) { onStyle(StyleOverride(motion = it)) }
            SettingChoice("corners", LookTiles.cornerWords, LookTiles.corner(style.cornerRadius.value.toInt()), p) {
                onStyle(StyleOverride(radiusDp = it))
            }
            SettingChoice("labels", LookTiles.labelWords, style.designators, p) { onStyle(StyleOverride(designators = it)) }
            Spacer(Modifier.height(12.dp))
        }
    }
    }
}

/** A live preview: the look's own plane, surface, ink, accent and corner. */
@Composable
private fun LookTile(tile: LookTiles.Tile, active: Boolean, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    val look = remember(tile) { AppearancePalette.palette(tile.value, tile.key, tile.label) }
    val lookStyle = remember(tile) { AppearancePalette.style(tile.value) }
    val corner = RoundedCornerShape(lookStyle.cornerRadius.coerceAtMost(12.dp))
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = tile.label
                selected = active
                role = Role.RadioButton
            }
            .border(if (active) 2.dp else Dim.hairline, if (active) p.accent else p.lineStrong)
            .padding(if (active) 2.dp else 1.dp)
            .background(look.plane)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(corner)
                .background(if (lookStyle.character == ChromeCharacter.Glass) look.surface.copy(alpha = 0.7f) else look.surface)
                .border(Dim.hairline, look.line, corner)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Mono(tile.label, look.readableColor(look.ink), Type.value, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeGlyph(2, look.accent)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(width = 22.dp, height = 10.dp).clip(corner).background(look.stone)
                .border(Dim.hairline, look.stoneHi, corner))
            Spacer(Modifier.weight(1f))
            if (active) Box(Modifier.size(10.dp).background(look.plane).padding(0.dp).background(p.accent))
        }
    }
}
