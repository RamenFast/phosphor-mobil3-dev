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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .size(size)
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
        Box(Modifier.offset(x = sink, y = sink)) {
            // The transport glyph (▶ / ❚❚) uprights to the viewing edge.
            UprightCell { Mono(label, if (void && pressed) p.accent else p.ink, Type.dataXl) }
        }
        // Silk-screened part number (`S1` — the main switch), bench rooms only.
        if (style.designators && designator != null) {
            Mono(
                designator, p.muted, Type.dataXs,
                Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 2.dp),
                letterSpacing = 1.2.sp,
            )
        }
    }
}

// A carved stone that carries a state (the LIVE key, the theme stone): lit accent rim
// when engaged, sunken face while on — capture is DOWN into the machine.
@Composable
fun StoneToggle(
    label: String,
    engaged: Boolean,
    p: Palette,
    modifier: Modifier = Modifier,
    reduced: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed || engaged
    val style = LocalRoomStyle.current
    val void = style.character == ChromeCharacter.Engraved
    val glass = style.character == ChromeCharacter.Glass
    val face by animateColorAsState(
        when {
            glass -> if (down) p.accent.copy(alpha = 0.30f) else p.surface2.copy(alpha = 0.55f)
            void -> if (down) p.accent.copy(alpha = 0.14f) else Color.Transparent
            down -> p.stoneLo
            else -> p.stone
        },
        styleSpec(reduced, style, Motion.press), label = "face",
    )
    val hi = if (down) p.stoneLo else p.stoneHi
    val lo = if (down) p.stoneHi else p.stoneLo
    Box(
        modifier
            .height(Dim.stoneKey)
            .drawBehind {
                if (glass) {
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
                        if (engaged) p.accent else p.stoneHi.copy(alpha = 0.85f),
                        cornerRadius = rad,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    return@drawBehind
                }
                drawRect(face)
                val s = 2.dp.toPx()
                if (void) {
                    drawRect(
                        if (engaged) p.accent else p.lineStrong,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    return@drawBehind
                }
                drawRect(hi, size = androidx.compose.ui.geometry.Size(this.size.width, s))
                drawRect(hi, size = androidx.compose.ui.geometry.Size(s, this.size.height))
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
                if (engaged) {
                    drawRect(
                        p.accent,
                        size = androidx.compose.ui.geometry.Size(this.size.width, 1.dp.toPx()),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Mono(label, if (engaged) p.accent else p.ink, Type.data)
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
    Box(
        modifier
            .height(Dim.flatKey)
            .background(if (pressed) p.accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(Dim.hairline, if (active || pressed) p.accent else p.line)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
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

@Composable
fun SheetRow(
    label: String,
    p: Palette,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    trailing: String? = null,
    glyph: SettingsGlyph? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(Dim.hairline, if (checked) p.accent else p.line)
            .padding(Dim.rowPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            glyph?.let {
                SettingsGlyphIcon(it, p, 20.dp)
                Spacer(Modifier.width(Dim.gap))
            }
            Mono(
                (if (checked) "✓ " else "") + label,
                if (checked) p.accent else p.ink,
                Type.dataLg,
            )
        }
        trailing?.let {
            Mono(it, p.muted, Type.data, Modifier.align(Alignment.CenterEnd))
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.height(Dim.gap))
}

@Composable
fun ChipCell(
    label: String,
    active: Boolean,
    p: Palette,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .height(if (small) 40.dp else 48.dp)
            .border(Dim.hairline, if (active) p.accent else p.line)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) { Mono(label, if (active) p.accent else p.ink2, if (small) Type.dataSm else Type.data) }
}

@Composable
fun SwatchCell(sw: BeamSwatch, active: Boolean, p: Palette, onClick: () -> Unit) {
    Column(
        Modifier.padding(4.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(sw.color)
                .border(if (active) 2.dp else Dim.hairline, if (active) p.accent else p.line),
        )
        Mono(
            sw.label, if (active) p.accent else p.muted, Type.dataXs,
            Modifier.padding(top = 3.dp),
        )
    }
}

// A hairline section heading — hierarchy, never a box.
@Composable
fun SectionHeading(text: String, p: Palette, modifier: Modifier = Modifier) {
    val style = LocalRoomStyle.current
    if (style.designators) {
        // Service-manual annotation: the heading extends a dotted leader line to
        // the margin, like a callout in an exploded diagram.
        Row(
            modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mono(text, p.muted, Type.dataSm, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.weight(1f).height(1.dp)) {
                drawLine(
                    p.line,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(2.dp.toPx(), 4.dp.toPx())
                    ),
                )
            }
        }
    } else {
        Mono(text, p.muted, Type.dataSm, modifier.padding(top = 14.dp, bottom = 6.dp))
    }
}

// Semantic constant for swatch color type (moved from ScopeModel usage sites).
typealias BeamSwatchColor = Color
