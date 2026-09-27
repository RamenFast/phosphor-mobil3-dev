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
    enum class Kind { CURRENT, CURATED, ROOM, CLASSIC, SAVED }
    data class Tile(val key: String, val label: String, val kind: Kind, val value: AppearanceValue, val room: Palette? = null)

    /** Curated order leads: the four looks Ben named first. */
    private val curatedOrder = listOf("curated:glass", "curated:amoled", "curated:dark", "curated:light")

    fun build(document: AppearanceDocument?, roomValue: (Palette) -> AppearanceValue): List<Tile> {
        val curated = curatedOrder.mapNotNull { id -> AppearanceDocument.CURATED.find { it.id == id } }
            .map { Tile(it.id, it.name, Kind.CURATED, it.value) }
        val curatedValues = curated.map { it.value }.toSet()
        // A classic room identical to a curated look is the same look: show it once.
        // The older versions of the curated four stay reachable in one quiet CLASSIC row.
        val rooms = Rooms.mapNotNull { room ->
            val value = roomValue(room)
            when {
                value in curatedValues -> null
                room.id in classicIds -> Tile("legacy:${room.id}", classicLabel(room), Kind.CLASSIC, value, room)
                else -> Tile("legacy:${room.id}", room.label, Kind.ROOM, value, room)
            }
        }
        val main = curated + rooms.filter { it.kind == Kind.ROOM }
        val taken = main.map { it.label.lowercase() }.toSet()
        // A saved look never shares a name (or a TalkBack announcement) with a built-in one.
        val saved = document?.users.orEmpty().map {
            Tile(it.id, if (it.name.lowercase() in taken) "${it.name} · saved" else it.name, Kind.SAVED, it.value)
        }
        return main + saved + rooms.filter { it.kind == Kind.CLASSIC }
    }

    /**
     * Exactly one tile is marked: the committed id, else the tile whose values equal the
     * active look, else the displayed room. Null means the look is custom: show [current].
     */
    fun activeKey(tiles: List<Tile>, document: AppearanceDocument?, displayedRoomId: String): String? {
        val activeId = document?.activeId.orEmpty()
        tiles.firstOrNull { activeId.isNotEmpty() && it.key == activeId }?.let { return it.key }
        document?.active?.let { value -> tiles.firstOrNull { it.value == value }?.let { return it.key } }
        if (activeId.isEmpty()) tiles.firstOrNull { it.room?.id == displayedRoomId }?.let { return it.key }
        return null
    }

    /** The custom, unsaved look as its own tile, so something is always marked. */
    fun current(document: AppearanceDocument?): Tile? =
        document?.active?.let { Tile(CURRENT_KEY, "current", Kind.CURRENT, it) }

    const val CURRENT_KEY = "current"

    /** Rooms that are the older versions of the curated four. */
    val classicIds = setOf("light", "dark", "amoled", "glass")
    private fun classicLabel(room: Palette) = if (room.id == "glass") "Glass" else room.label

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
            val activeKey = LookTiles.activeKey(tiles, document, state.room.id)
            val current = if (activeKey == null) LookTiles.current(document) else null
            val marked = activeKey ?: current?.key
            GroupHeading("LOOKS", p, first = true)
            LookGrid(listOfNotNull(current) + tiles.filter { it.kind != LookTiles.Kind.CLASSIC }, marked, p, onPickRoom, onPickLook)
            val classic = tiles.filter { it.kind == LookTiles.Kind.CLASSIC }
            if (classic.isNotEmpty()) {
                GroupHeading("CLASSIC", p)
                LookGrid(classic, marked, p, onPickRoom, onPickLook)
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

@Composable
private fun LookGrid(tiles: List<LookTiles.Tile>, marked: String?, p: Palette,
    onPickRoom: (Palette) -> Unit, onPickLook: (String) -> Unit) {
    tiles.chunked(2).forEachIndexed { row, pair ->
        if (row > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { tile ->
                LookTile(tile, tile.key == marked, p, Modifier.weight(1f)) {
                    when {
                        tile.kind == LookTiles.Kind.CURRENT -> {} // already worn
                        tile.room != null -> onPickRoom(tile.room)
                        else -> onPickLook(tile.key)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
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
