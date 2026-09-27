package dev.phosphor.mobil3.ui

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class ManualFiguresTest {
    private fun correlation(points: List<Pair<Float, Float>>): Double {
        val n = points.size
        val mx = points.sumOf { it.first.toDouble() } / n
        val my = points.sumOf { it.second.toDouble() } / n
        val cov = points.sumOf { (it.first - mx) * (it.second - my) }
        val vx = points.sumOf { (it.first - mx) * (it.first - mx) }
        val vy = points.sumOf { (it.second - my) * (it.second - my) }
        return cov / sqrt(vx * vy)
    }

    @Test fun figuresDrawWhatTheManualSays() {
        val mono = ManualFigures.points(ManualFigures.Kind.MONO)
        assertTrue(mono.all { abs(it.first - it.second) < 1e-6f })
        val anti = ManualFigures.points(ManualFigures.Kind.ANTI)
        assertTrue(anti.all { abs(it.first + it.second) < 1e-6f })
        val circle = ManualFigures.points(ManualFigures.Kind.CIRCLE)
        assertTrue(circle.all { abs(hypot(it.first, it.second) - 0.85f) < 1e-3f })
        val stereo = ManualFigures.points(ManualFigures.Kind.STEREO)
        assertTrue("wide stereo is neither a line nor noise", correlation(stereo) in 0.2..0.9)
        val knot = ManualFigures.points(ManualFigures.Kind.KNOT)
        assertTrue(abs(correlation(knot)) < 0.2)
    }

    @Test fun everyFigureStaysOnTheGlassAndSpeaksForTalkBack() {
        for (kind in ManualFigures.Kind.entries) {
            val points = ManualFigures.points(kind)
            assertEquals(ManualFigures.POINTS, points.size)
            assertTrue(points.all { it.first in -1f..1f && it.second in -1f..1f })
            assertTrue(kind.description.length > 20)
            assertTrue(kind.caption.isNotBlank())
        }
    }
}
