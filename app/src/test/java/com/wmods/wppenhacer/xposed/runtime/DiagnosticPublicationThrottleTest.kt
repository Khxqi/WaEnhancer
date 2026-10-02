package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticPublicationThrottleTest {
    @Test
    fun firstPublicationIsImmediate() {
        val throttle = DiagnosticPublicationThrottle(minimumIntervalMs = 500)

        assertEquals(0L, throttle.delayMs(nowMs = 1000))
    }

    @Test
    fun laterPublicationIsBoundedByMinimumInterval() {
        val throttle = DiagnosticPublicationThrottle(minimumIntervalMs = 500)
        throttle.markPublished(nowMs = 1000)

        assertEquals(400L, throttle.delayMs(nowMs = 1100))
        assertEquals(0L, throttle.delayMs(nowMs = 1500))
        assertEquals(0L, throttle.delayMs(nowMs = 2000))
    }
}
