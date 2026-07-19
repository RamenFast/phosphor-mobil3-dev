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

private data class BottomCornerRadii(val left: Int = 0, val right: Int = 0)

// ── Chrome insets — system-safe plus the panel's physical bottom-corner clip. ──
@Composable
fun chromeSafeDrawingInsets(horizontalPadding: Dp, bottomPadding: Dp): WindowInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    var corners by remember(view) { mutableStateOf(view.bottomCornerRadii()) }
    DisposableEffect(view) {
        val listener = View.OnLayoutChangeListener { changedView, _, _, _, _, _, _, _, _ ->
            corners = changedView.bottomCornerRadii()
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }

    // Android exposes the corner radius, not the panel's exact clip path. Model
    // each side as a quarter circle and solve its sagitta at the content gutter;
    // ordinary bottom padding supplies the rest without lifting chrome by all of r.
    fun requiredInset(radiusPx: Int, sideSafeInsetPx: Int): Float {
        if (radiusPx <= 0) return 0f
        val x = (sideSafeInsetPx + horizontalPaddingPx).coerceIn(0f, radiusPx.toFloat())
        val distanceFromCenter = radiusPx - x
        val sagitta = radiusPx - sqrt(radiusPx.toFloat() * radiusPx - distanceFromCenter * distanceFromCenter)
        return (sagitta - bottomPaddingPx).coerceAtLeast(0f)
    }

    val physicalCornerBottom = ceil(
        maxOf(
            requiredInset(corners.left, safeDrawing.getLeft(density, layoutDirection)),
            requiredInset(corners.right, safeDrawing.getRight(density, layoutDirection)),
        )
    ).toInt()
    return safeDrawing.union(WindowInsets(bottom = physicalCornerBottom))
}

private fun View.bottomCornerRadii(): BottomCornerRadii {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BottomCornerRadii()
    val insets = rootWindowInsets ?: return BottomCornerRadii()
    return BottomCornerRadii(
        left = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        right = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
    )
}
