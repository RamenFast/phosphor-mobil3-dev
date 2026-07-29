package dev.phosphor.mobil3.ui

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The detent must actually RUN in the default configuration.
 *
 * This exists because of a real miss: the detent was correct and fully tested, but the
 * sensor that feeds it was only registered when a rotation or placement lock was on, and
 * with no lock the Activity was `SCREEN_ORIENTATION_UNSPECIFIED` — rotation handed
 * wholesale to Android. So for anyone who had not turned a lock on, which is the
 * default, none of it ran. Ben reported rotation "still super sensitive" and was right.
 *
 * Unit-testing `RotationDetent` proved the arithmetic. It could not prove the arithmetic
 * was reachable. That is the gap this guards.
 */
class RotationDetentReachabilityTest {

    @Test
    fun theGravitySensorIsNotGatedBehindALock() {
        val source = mainActivitySource()
        val fn = source.substringAfter("private fun updateOrientationSensor()")
            .substringBefore("private fun routeOrientation")
        assertTrue(
            !fn.contains("scopeRotationLockState || uiPlacementLockState"),
            "the orientation sensor must not be gated behind a lock: with no lock the " +
                "detent would never run, which is the default configuration",
        )
    }

    @Test
    fun routingDoesNotReturnEarlyWhenNoLockIsSet() {
        val source = mainActivitySource()
        val fn = source.substringAfter("private fun routeOrientation")
            .substringBefore("private fun applyDetentedOrientation")
        assertTrue(
            !fn.contains("if (!(scopeRotationLockState || uiPlacementLockState)) return"),
            "routeOrientation must handle the no-lock case rather than returning early; " +
                "that early return is what made the detent dead code by default",
        )
        assertTrue(
            fn.contains("applyDetentedOrientation"),
            "the no-lock branch must pin the Activity to the detented orientation",
        )
    }

    @Test
    fun theSystemRotationLockIsStillHonoured() {
        // Pinning orientation ourselves must not override a user who told their PHONE
        // not to rotate. That would be a worse bug than the one being fixed.
        val source = mainActivitySource()
        val fn = source.substringAfter("private fun applyDetentedOrientation")
            .substringBefore("private fun exactCurrentOrientation")
        assertTrue(
            fn.contains("ACCELEROMETER_ROTATION"),
            "must read the system auto-rotate setting before pinning an orientation",
        )
    }

    private fun mainActivitySource(): String {
        var dir = File("").absoluteFile
        repeat(4) {
            val candidate = File(dir, "src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt")
            if (candidate.isFile) return candidate.readText()
            val fromRepo = File(dir, "app/src/main/kotlin/dev/phosphor/mobil3/MainActivity.kt")
            if (fromRepo.isFile) return fromRepo.readText()
            dir = dir.parentFile ?: return@repeat
        }
        fail("could not locate MainActivity.kt")
    }
}
