package dev.phosphor.mobil3.ui

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

private data class CornerRadii(
    val topLeft: Int = 0,
    val topRight: Int = 0,
    val bottomLeft: Int = 0,
    val bottomRight: Int = 0,
)

// ── Chrome insets — system-safe plus all four physical panel corners. ──
// Rotation moves the S25's physical corner arcs onto the left/right chrome edges;
// only accounting for the portrait bottom pair clips cards in landscape.
@Composable
fun chromeSafeDrawingInsets(horizontalPadding: Dp, verticalPadding: Dp): WindowInsets {
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
    val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
    val verticalPaddingPx = with(density) { verticalPadding.toPx() }

    // Android exposes the corner radius, not the panel's exact clip path. Model
    // each side as a quarter circle and solve its sagitta at the content gutter;
    // ordinary edge padding supplies the rest without pushing chrome inward by all of r.
    fun requiredInset(radiusPx: Int, perpendicularClearancePx: Float, edgePaddingPx: Float): Float {
        if (radiusPx <= 0) return 0f
        val x = perpendicularClearancePx.coerceIn(0f, radiusPx.toFloat())
        val distanceFromCenter = radiusPx - x
        val sagitta = radiusPx - sqrt(radiusPx.toFloat() * radiusPx - distanceFromCenter * distanceFromCenter)
        return (sagitta - edgePaddingPx).coerceAtLeast(0f)
    }

    val safeLeft = safeDrawing.getLeft(density, layoutDirection).toFloat()
    val safeTop = safeDrawing.getTop(density).toFloat()
    val safeRight = safeDrawing.getRight(density, layoutDirection).toFloat()
    val safeBottom = safeDrawing.getBottom(density).toFloat()
    val physicalLeft = ceil(
        maxOf(
            requiredInset(corners.topLeft, safeTop + verticalPaddingPx, horizontalPaddingPx),
            requiredInset(corners.bottomLeft, safeBottom + verticalPaddingPx, horizontalPaddingPx),
        )
    ).toInt()
    val physicalTop = ceil(
        maxOf(
            requiredInset(corners.topLeft, safeLeft + horizontalPaddingPx, verticalPaddingPx),
            requiredInset(corners.topRight, safeRight + horizontalPaddingPx, verticalPaddingPx),
        )
    ).toInt()
    val physicalRight = ceil(
        maxOf(
            requiredInset(corners.topRight, safeTop + verticalPaddingPx, horizontalPaddingPx),
            requiredInset(corners.bottomRight, safeBottom + verticalPaddingPx, horizontalPaddingPx),
        )
    ).toInt()
    val physicalBottom = ceil(
        maxOf(
            requiredInset(corners.bottomLeft, safeLeft + horizontalPaddingPx, verticalPaddingPx),
            requiredInset(corners.bottomRight, safeRight + horizontalPaddingPx, verticalPaddingPx),
        )
    ).toInt()
    return safeDrawing.union(
        WindowInsets(
            left = physicalLeft,
            top = physicalTop,
            right = physicalRight,
            bottom = physicalBottom,
        )
    )
}

private fun View.cornerRadii(): CornerRadii {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return CornerRadii()
    val insets = rootWindowInsets ?: return CornerRadii()
    return CornerRadii(
        topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0,
        topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0,
        bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
    )
}
