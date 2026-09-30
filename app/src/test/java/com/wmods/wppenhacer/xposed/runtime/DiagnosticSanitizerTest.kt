package com.wmods.wppenhacer.xposed.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {
    @Test
    fun redactsJidsAndLongNumbers() {
        val sanitized = DiagnosticSanitizer.sanitize(
            "failure for 491761234567@s.whatsapp.net and +49 176 1234567"
        )

        assertFalse(sanitized.contains("491761234567"))
        assertFalse(sanitized.contains("1234567"))
        assertTrue(sanitized.contains("<redacted-id>"))
        assertTrue(sanitized.contains("<redacted-number>"))
    }
}
