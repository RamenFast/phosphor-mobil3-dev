package dev.phosphor.mobil3.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phosphor.mobil3.settings.appearance.AppearancePresentationPolicy

internal fun Modifier.sliderTrack(p: Palette, lo: Float, hi: Float? = null): Modifier = drawBehind {
    val geometry = SliderGeometry(size.width, density)
    val midY = size.height / 2f
    drawLine(p.line, Offset(geometry.start, midY), Offset(geometry.end, midY), geometry.trackPx)
    val xLo = geometry.xAt(lo)
    val xHi = hi?.let(geometry::xAt)
    drawLine(
        sliderAccent(p), Offset(if (xHi == null) geometry.start else xLo, midY),
        Offset(xHi ?: xLo, midY), geometry.trackPx,
    )
    for (x in listOfNotNull(xLo, xHi)) {
        drawRect(
            p.ink,
            Offset(x - geometry.thumbPx / 2f, midY - geometry.thumbPx / 2f),
            androidx.compose.ui.geometry.Size(geometry.thumbPx, geometry.thumbPx),
        )
    }
}

@Composable
internal fun SliderLane(
    p: Palette,
    fraction: Float,
    modifier: Modifier = Modifier,
    highFraction: Float? = null,
    onStart: (Float) -> Unit = {},
    onChange: (Float) -> Unit,
) {
    // A slider tap after a scroll or pull is not a tap: the lane never sees the parent's
    // consumption, so the sheet's gesture guard decides. Sideways scrubs never scroll.
    val guard = LocalSheetGestureGuard.current
    Box(
        modifier.height(if (LocalSettingsControlAccess.current) 48.dp else SliderGeometry.HIT_LANE_DP.dp)
            .consoleSeekGesture(
                durationMs = 1L,
                onStart = { if (guard.allowsTap()) onStart(it) },
                onScrub = { if (guard.allowsTap()) onChange(it) },
                onCommit = { if (guard.allowsTap()) onChange(it) },
                onCancel = {},
            )
            .sliderTrack(p, fraction, highFraction),
    )
}

// ── Haptics map (UX-SPEC §3, low intensities) ─────────────────────────────────
object Haptics {
    fun light(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun medium(v: View) = v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun doubleTick(v: View) { // the postcard "postmark"
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        v.postDelayed({ v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, 90)
    }
}

// ── The carved stone — the dimensional control (exactly three exist app-wide) ──
// A carve_with_face port: stone fill, stone_hi catch-light top/left, stone_lo shadow
// bottom/right; pressing SINKS it (bevel inverts, face darkens, content offsets 1dp).
@Composable
fun StoneKey(
    label: String,
    p: Palette,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dim.stoneKey,
    reduced: Boolean = false,
    designator: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Character comes from the room's RoomStyle (the framework absorbed the old
    // id check). Engraved (the Void): no stone exists on a panel that is pure
    // light — importance is an engraved double-hairline, presses invert to
    // accent. Annotated/Glass refine atop the carved base in their own souls.
    val style = LocalRoomStyle.current
    val void = style.character == ChromeCharacter.Engraved
    val glass = style.character == ChromeCharacter.Glass
    val face by animateColorAsState(
        when {
            glass -> if (pressed) p.surface2.copy(alpha = 0.88f) else p.surface2.copy(alpha = 0.55f)
            void -> if (pressed) p.accent.copy(alpha = 0.18f) else Color.Transparent
            pressed -> p.stoneLo
            else -> p.stone
        },
        styleSpec(reduced, style, Motion.press), label = "face",
    )
    val sink by animateDpAsState(
        if (pressed && !void && !glass) 1.dp else 0.dp, styleSpec(reduced, style, Motion.press), label = "sink"
    )
    val hi = if (pressed) p.stoneLo else p.stoneHi
    val lo = if (pressed) p.stoneHi else p.stoneLo
    Box(
        modifier
            .size(AppearancePresentationPolicy.target(size.value).dp)
            .drawBehind {
                if (glass) {
                    // A glass slab: translucent fill, iOS-6 gloss (top sheen),
                    // specular rim. Press deepens the pane — light through glass.
                    val r = style.cornerRadius.toPx()
                    val rad = androidx.compose.ui.geometry.CornerRadius(r, r)
                    drawRoundRect(face, cornerRadius = rad)
                    drawRoundRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.45f to Color.Transparent,
                        ),
                        cornerRadius = rad,
                    )
                    drawRoundRect(
                        p.stoneHi.copy(alpha = if (pressed) 1f else 0.85f),
                        cornerRadius = rad,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    return@drawBehind
                }
                drawRect(face)
                val s = 2.dp.toPx()
                if (void) {
                    // engraved: outer accent hairline + inner quiet hairline
                    drawRect(
                        if (pressed) p.accent else p.lineStrong,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    drawRect(
                        p.line,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(
                            this.size.width - 6.dp.toPx(), this.size.height - 6.dp.toPx(),
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    return@drawBehind
                }
                // catch-light: top + left
                drawRect(hi, size = androidx.compose.ui.geometry.Size(this.size.width, s))
                drawRect(hi, size = androidx.compose.ui.geometry.Size(s, this.size.height))
                // shadow: bottom + right
                drawRect(
                    lo,
                    topLeft = Offset(0f, this.size.height - s),
                    size = androidx.compose.ui.geometry.Size(this.size.width, s),
                )
                drawRect(
                    lo,
                    topLeft = Offset(this.size.width - s, 0f),
                    size = androidx.compose.ui.geometry.Size(s, this.size.height),
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { IntOffset(sink.roundToPx(), sink.roundToPx()) }) {
            // The transport glyph (▶ / ❚❚) uprights to the viewing edge.
            UprightCell {
                Mono(label, p.readableColor(if (void && pressed) p.accent else p.ink), Type.dataXl,
                    Modifier.background(p.surface))
            }
        }
        // Silk-screened part number (`S1` — the main switch), bench rooms only.
        if (style.designators && designator != null) {
            Mono(
                designator, p.readableColor(p.muted), Type.dataXs,
                Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 2.dp).background(p.surface),
                letterSpacing = 1.2.sp,
            )
        }
    }
}

// ── Flat hairline chrome (everything that is not one of the three stones) ─────
@Composable
fun FlatKey(
    label: String,
    p: Palette,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    designator: String? = null,
    onClick: () -> Unit,
) {
    // Instant press feedback (the responsiveness pass): tint + accent rim on
    // finger-down, zero animation delay — flat keys answer immediately.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = LocalRoomStyle.current
    val accessible = LocalSettingsControlAccess.current
    val p = p.readableOn(p.controlBackplate(pressed))
    val tap = sheetTap(onClick)
    Box(
        modifier
            .heightIn(min = 48.dp).widthIn(min = 48.dp)
            .then(if (accessible) Modifier.settingsChoice(active) else Modifier)
            .background(p.surface)
            .border(Dim.hairline, if (active || pressed) p.accent else p.line)
            .then(if (accessible) Modifier.settingsFocusBorder(p) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = tap)
            .padding(horizontal = style.space(14.dp), vertical = style.space(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // UI-locked mode: the label stays upright toward the viewing edge, through
        // the one correct primitive (re-measures, never clips) — not a naive spin.
        UprightCell {
            Mono(label, if (active || pressed) p.accent else p.ink2, Type.data)
        }
        // Service-bench designator (`V2` for the tube, `J1` for the input jack):
        // a silk-screened part number in the corner, Annotated rooms only.
        if (style.designators && designator != null) {
            Mono(
                designator, p.muted, Type.dataXs,
                Modifier.align(Alignment.TopStart).padding(top = 1.dp),
                letterSpacing = 1.2.sp,
            )
        }
    }
}

internal enum class SheetChromeVector { Close, Tick, Search }

/** Closed sheet silhouettes, independent of installed fonts and fontScale. */
@Composable
internal fun SheetChromeMark(kind: SheetChromeVector, ink: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val box = minOf(this.size.width, this.size.height)
        when (kind) {
            SheetChromeVector.Close -> drawPath(Path().apply {
                moveTo(box * .20f, box * .08f)
                lineTo(box * .50f, box * .38f)
                lineTo(box * .80f, box * .08f)
                lineTo(box * .92f, box * .20f)
                lineTo(box * .62f, box * .50f)
                lineTo(box * .92f, box * .80f)
                lineTo(box * .80f, box * .92f)
                lineTo(box * .50f, box * .62f)
                lineTo(box * .20f, box * .92f)
                lineTo(box * .08f, box * .80f)
                lineTo(box * .38f, box * .50f)
                lineTo(box * .08f, box * .20f)
                close()
            }, ink)
            SheetChromeVector.Tick -> drawPath(Path().apply {
                moveTo(box * .10f, box * .50f)
                lineTo(box * .22f, box * .38f)
                lineTo(box * .40f, box * .60f)
                lineTo(box * .80f, box * .14f)
                lineTo(box * .94f, box * .28f)
                lineTo(box * .42f, box * .88f)
                close()
            }, ink)
            SheetChromeVector.Search -> {
                drawPath(Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(Offset(box * .08f, box * .08f), Size(box * .60f, box * .60f)))
                    addOval(Rect(Offset(box * .20f, box * .20f), Size(box * .36f, box * .36f)))
                }, ink)
                drawPath(Path().apply {
                    moveTo(box * .54f, box * .62f)
                    lineTo(box * .62f, box * .54f)
                    lineTo(box * .92f, box * .84f)
                    lineTo(box * .84f, box * .92f)
                    close()
                }, ink)
            }
        }
    }
}

// Semantic constant for swatch color type (moved from ScopeModel usage sites).
typealias BeamSwatchColor = Color
