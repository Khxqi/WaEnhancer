package com.wmods.wppenhacer.xposed.core

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.wmods.wppenhacer.xposed.runtime.RuntimeBootstrap

/** Compatibility facade for existing feature code. New startup work lives in RuntimeBootstrap. */
class FeatureLoader {
    companion object {
        const val PACKAGE_WPP = "com.whatsapp"

        /** Retained only for old companion data; the Phase 2 runtime never targets this package. */
        @Deprecated("WhatsApp Business is not supported by the Phase 2 runtime")
        const val PACKAGE_BUSINESS = "com.whatsapp.w4b"

        @JvmField
        var mApp: Application? = null

        @SuppressLint("StaticFieldLeak")
        lateinit var moduleContext: Context

        @JvmStatic
        fun start(loader: ClassLoader, sourceDir: String, processName: String) {
            RuntimeBootstrap.install(loader, sourceDir, processName)
        }

        @JvmStatic
        fun disableExpirationVersion(classLoader: ClassLoader) {
            RuntimeBootstrap.installExpirationFallback(classLoader)
        }
    }
}
