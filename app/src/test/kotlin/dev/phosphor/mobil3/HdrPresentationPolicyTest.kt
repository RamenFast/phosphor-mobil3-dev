package dev.phosphor.mobil3

import org.junit.Test
import kotlin.test.*

class HdrPresentationPolicyTest {
    @Test fun missingKeyIsOffAndDoesNotAttempt() {
        assertFalse(HdrPresentationPolicy.requested(emptyMap<String, Any>()))
        assertFalse(HdrPresentationPolicy.attemptLinear(false, true, 34, true))
        assertEquals("SDR · HDR off", HdrPresentationPolicy.reason(false, true, 34, true))
        assertEquals("SDR", HdrPresentationPolicy.activeLabel(false, true, true))
    }
    @Test fun api34VulkanFp16MayAttempt() {
        assertTrue(HdrPresentationPolicy.attemptLinear(true, true, 34, true))
        assertEquals("attempt linear HDR", HdrPresentationPolicy.reason(true, true, 34, true))
        assertEquals("linear HDR configured", HdrPresentationPolicy.activeLabel(true, true, true))
        assertEquals("SDR · linear not proven", HdrPresentationPolicy.activeLabel(true, false, true))
    }
    @Test fun olderApiOrMissingPairStayExplicitSdr() {
        assertFalse(HdrPresentationPolicy.attemptLinear(true, true, 33, true))
        assertEquals("SDR · HDR metadata needs API 34", HdrPresentationPolicy.reason(true, true, 33, true))
        assertFalse(HdrPresentationPolicy.attemptLinear(true, false, 34, true))
        assertEquals("HDR requested · waiting for surface report", HdrPresentationPolicy.reason(true, false, 34, true))
        assertFalse(HdrPresentationPolicy.attemptLinear(true, true, 34, false))
    }
}
