package dev.phosphor.mobil3.ui

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.sqrt

internal data class CornerRadii(
    val topLeft: Int = 0,
    val topRight: Int = 0,
    val bottomLeft: Int = 0,
    val bottomRight: Int = 0,
)

internal val LocalChromeInsetQuadrant = compositionLocalOf { 0 }

// ── Chrome insets — system-safe plus all four physical panel corners. ──
// Rotation moves the S25's physical corner arcs onto the left/right chrome edges;
// only accounting for the portrait bottom pair clips cards in landscape.
@Composable
fun chromeSafeDrawingInsets(
    horizontalPadding: Dp,
    verticalPadding: Dp,
    appliedQuadrant: Int = LocalChromeInsetQuadrant.current,
): WindowInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    var corners by remember(view) { mutableStateOf(view.cornerRadii()) }
    DisposableEffect(view) {
        val listener = View.OnLayoutChangeListener { changedView, _, _, _, _, _, _, _, _ ->
            corners = changedView.cornerRadii()
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    val insets = ChromeInsetPolicy.safeInsets(
        physicalSafe = listOf(
            safeDrawing.getLeft(density, layoutDirection), safeDrawing.getTop(density),
            safeDrawing.getRight(density, layoutDirection), safeDrawing.getBottom(density),
        ),
        corners = corners,
        horizontalPaddingPx = with(density) { horizontalPadding.toPx() },
        verticalPaddingPx = with(density) { verticalPadding.toPx() },
        quadrant = appliedQuadrant,
    )
    return WindowInsets(left = insets[0], top = insets[1], right = insets[2], bottom = insets[3])
}

internal object ChromeInsetPolicy {
    // Physical L/T/R/B into the local CCW frame, shared with the explicit popout path.
    fun rotatedInsets(physical: List<Int>, quadrant: Int): List<Int> =
        List(4) { physical[Math.floorMod(it - quadrant, 4)] }

    fun safeInsets(
        physicalSafe: List<Int>,
        corners: CornerRadii,
        horizontalPaddingPx: Float,
        verticalPaddingPx: Float,
        quadrant: Int,
    ): List<Int> {
        val odd = quadrant % 2 != 0
        val physicalHorizontalPadding = if (odd) verticalPaddingPx else horizontalPaddingPx
        val physicalVerticalPadding = if (odd) horizontalPaddingPx else verticalPaddingPx
        val safeLeft = physicalSafe[0].toFloat()
        val safeTop = physicalSafe[1].toFloat()
        val safeRight = physicalSafe[2].toFloat()
        val safeBottom = physicalSafe[3].toFloat()
        val physicalLeft = ceil(
            maxOf(
                requiredInset(corners.topLeft, safeTop + physicalVerticalPadding, physicalHorizontalPadding),
                requiredInset(corners.bottomLeft, safeBottom + physicalVerticalPadding, physicalHorizontalPadding),
            )
        ).toInt()
        val physicalTop = ceil(
            maxOf(
                requiredInset(corners.topLeft, safeLeft + physicalHorizontalPadding, physicalVerticalPadding),
                requiredInset(corners.topRight, safeRight + physicalHorizontalPadding, physicalVerticalPadding),
            )
        ).toInt()
        val physicalRight = ceil(
            maxOf(
                requiredInset(corners.topRight, safeTop + physicalVerticalPadding, physicalHorizontalPadding),
                requiredInset(corners.bottomRight, safeBottom + physicalVerticalPadding, physicalHorizontalPadding),
            )
        ).toInt()
        val physicalBottom = ceil(
            maxOf(
                requiredInset(corners.bottomLeft, safeLeft + physicalHorizontalPadding, physicalVerticalPadding),
                requiredInset(corners.bottomRight, safeRight + physicalHorizontalPadding, physicalVerticalPadding),
            )
        ).toInt()
        return rotatedInsets(
            listOf(
                maxOf(physicalSafe[0], physicalLeft), maxOf(physicalSafe[1], physicalTop),
                maxOf(physicalSafe[2], physicalRight), maxOf(physicalSafe[3], physicalBottom),
            ),
            quadrant,
        )
    }

    // Android exposes the corner radius, not the panel's exact clip path. Model
    // each side as a quarter circle and solve its sagitta at the content gutter;
    // ordinary edge padding supplies the rest without pushing chrome inward by all of r.
    private fun requiredInset(radiusPx: Int, perpendicularClearancePx: Float, edgePaddingPx: Float): Float {
        if (radiusPx <= 0) return 0f
        val x = perpendicularClearancePx.coerceIn(0f, radiusPx.toFloat())
        val distanceFromCenter = radiusPx - x
        val sagitta = radiusPx - sqrt(radiusPx.toFloat() * radiusPx - distanceFromCenter * distanceFromCenter)
        return (sagitta - edgePaddingPx).coerceAtLeast(0f)
    }
}

private fun View.cornerRadii(): CornerRadii {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return CornerRadii()
    return cornerRadiiApi31()
}

@RequiresApi(Build.VERSION_CODES.S)
private fun View.cornerRadiiApi31(): CornerRadii {
    val insets = rootWindowInsets ?: return CornerRadii()
    return CornerRadii(
        topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0,
        topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0,
        bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
    )
}
