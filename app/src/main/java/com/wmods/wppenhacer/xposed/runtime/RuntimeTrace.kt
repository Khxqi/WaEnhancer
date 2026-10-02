package com.wmods.wppenhacer.xposed.runtime

import de.robv.android.xposed.XposedBridge

object RuntimeTrace {
    fun event(name: String, summary: String? = null) {
        val detail = summary
            ?.let(DiagnosticSanitizer::sanitize)
            ?.takeIf { it.isNotBlank() }
            ?.let { ": $it" }
            .orEmpty()
        XposedBridge.log("WaEnhancer runtime [$name]$detail")
    }
}
