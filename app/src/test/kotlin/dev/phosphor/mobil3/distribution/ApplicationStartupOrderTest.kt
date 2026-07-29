package dev.phosphor.mobil3.distribution

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The process-startup ordering rule, guarded at the source level.
 *
 * `PhosphorApplication` needs a real Android context, so it cannot be constructed on the
 * host. What CAN be checked is the shape that made it fail: a ContentProvider's
 * `onCreate` runs BEFORE `Application.onCreate`, so any `lateinit` field the provider
 * reaches for is unset on a cold process. pm3 hit exactly that and refused with
 * `UninitializedPropertyAccessException` instead of answering, which read as a broken
 * provider rather than an early one.
 *
 * A source check is admittedly blunt. It is here because the alternative is no check at
 * all, and this is a failure that only appears when the app is NOT already running,
 * which is precisely when nobody is watching.
 */
class ApplicationStartupOrderTest {

    @Test
    fun theCausalStoreIsNotLateinitBecauseProvidersRunBeforeApplicationOnCreate() {
        val source = applicationSource()
        val storeDeclaration = source.lines().firstOrNull { it.contains("causalStore") && it.contains("val") }
            ?: fail("could not find the causalStore declaration in PhosphorApplication.kt")

        assertTrue(
            !storeDeclaration.contains("lateinit"),
            "causalStore must not be lateinit: a ContentProvider (pm3) can reach it before " +
                "Application.onCreate runs, and an unset lateinit refuses with a stack trace. " +
                "Found: ${storeDeclaration.trim()}",
        )
        assertTrue(
            storeDeclaration.contains("by lazy"),
            "causalStore should be built on first use so provider-first access works. " +
                "Found: ${storeDeclaration.trim()}",
        )
    }

    @Test
    fun theStoreIsStillBuiltEagerlyOnANormalLaunch() {
        // Lazy must not become "only when something asks", or a normal launch would move
        // the migration cost to the first HUD write instead of doing it up front.
        val source = applicationSource()
        val onCreate = source.substringAfter("override fun onCreate()").substringBefore("private fun")
        assertTrue(
            onCreate.contains("causalStore"),
            "onCreate must touch causalStore so a normal launch still initialises eagerly",
        )
    }

    private fun applicationSource(): String {
        // Walk up to the module root: tests may run from either the repo or app dir.
        var dir = File("").absoluteFile
        repeat(4) {
            val candidate = File(dir, "src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt")
            if (candidate.isFile) return candidate.readText()
            val fromRepo = File(dir, "app/src/main/kotlin/dev/phosphor/mobil3/PhosphorApplication.kt")
            if (fromRepo.isFile) return fromRepo.readText()
            dir = dir.parentFile ?: return@repeat
        }
        fail("could not locate PhosphorApplication.kt from ${File("").absolutePath}")
    }
}
