package dev.phosphor.mobil3.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face by animateColorAsState(
        if (pressed) p.stoneLo else p.stone, motionSpec(reduced, Motion.press), label = "face"
    )
    val sink by animateDpAsState(
        if (pressed) 1.dp else 0.dp, motionSpec(reduced, Motion.press), label = "sink"
    )
    val hi = if (pressed) p.stoneLo else p.stoneHi
    val lo = if (pressed) p.stoneHi else p.stoneLo
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawRect(face)
                val s = 2.dp.toPx()
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
            Mono(label, p.ink, Type.dataXl)
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
    val face by animateColorAsState(
        if (down) p.stoneLo else p.stone, motionSpec(reduced, Motion.press), label = "face"
    )
    val hi = if (down) p.stoneLo else p.stoneHi
    val lo = if (down) p.stoneHi else p.stoneLo
    Box(
        modifier
            .height(Dim.stoneKey)
            .drawBehind {
                drawRect(face)
                val s = 2.dp.toPx()
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
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(Dim.flatKey)
            .border(Dim.hairline, if (active) p.accent else p.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Mono(label, if (active) p.accent else p.ink2, Type.data) }
}

@Composable
fun SheetRow(
    label: String,
    p: Palette,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(Dim.hairline, if (checked) p.accent else p.line)
            .padding(Dim.rowPad),
    ) {
        Mono(
            (if (checked) "✓ " else "") + label,
            if (checked) p.accent else p.ink,
            Type.dataLg,
        )
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
    Mono(text, p.muted, Type.dataSm, modifier.padding(top = 14.dp, bottom = 6.dp))
}

// Semantic constant for swatch color type (moved from ScopeModel usage sites).
typealias BeamSwatchColor = Color
