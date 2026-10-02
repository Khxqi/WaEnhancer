package com.wmods.wppenhacer.xposed.runtime

import android.content.Context
import android.content.SharedPreferences
import com.crossbowffs.remotepreferences.RemotePreferences
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.WppXposed
import de.robv.android.xposed.SELinuxHelper
import de.robv.android.xposed.XposedBridge

object RuntimeConfigKeys {
    const val DISABLE_ALL_HOOKS = "runtime_disable_all_hooks"
    const val DISABLE_VISUAL_MODIFICATIONS = "runtime_disable_visual_modifications"
    const val SAFE_MODE = "runtime_safe_mode"
    const val ENABLE_LOGS = "enablelogs"
    const val ENABLE_LIQUID_GLASS_PROTOTYPE = "runtime_enable_liquid_glass_prototype"
    const val ENABLE_IOS_HOME_REDESIGN = "runtime_enable_ios_home_redesign"
}

enum class ConfigTransportStatus {
    XSHARED_PREFERENCES,
    REMOTE_PROVIDER,
    FAILED_CLOSED
}

data class RuntimeConfigSnapshot(
    val disableAllHooks: Boolean,
    val disableVisualModifications: Boolean,
    val safeMode: Boolean,
    val enableLogs: Boolean,
    val transportStatus: ConfigTransportStatus,
    val enableLiquidGlassPrototype: Boolean = false,
    val enableIosHomeRedesign: Boolean = false,
    val failureSummary: String? = null
)

data class RuntimeConfigAccess(
    val snapshot: RuntimeConfigSnapshot,
    val legacyPreferences: SharedPreferences
)

object RuntimeConfigReader {
    fun readEarly(): RuntimeConfigSnapshot {
        return try {
            val preferences = WppXposed.getPref().apply { reload() }
            snapshot(preferences, ConfigTransportStatus.XSHARED_PREFERENCES)
        } catch (throwable: Throwable) {
            failedClosed(throwable)
        }
    }

    fun read(context: Context): RuntimeConfigAccess {
        val xSharedPreferences = WppXposed.getPref().apply { reload() }
        try {
            val readable = SELinuxHelper.getAppDataFileService()
                .checkFileAccess(xSharedPreferences.file.absolutePath, 4)
            if (readable) {
                return RuntimeConfigAccess(
                    snapshot(xSharedPreferences, ConfigTransportStatus.XSHARED_PREFERENCES),
                    xSharedPreferences
                )
            }
        } catch (throwable: Throwable) {
            XposedBridge.log("WaEnhancer config: XSharedPreferences check failed: ${throwable.javaClass.simpleName}")
        }

        return try {
            val remote = RemotePreferences(
                context,
                BuildConfig.APPLICATION_ID + ".preferences",
                BuildConfig.APPLICATION_ID + "_preferences"
            )
            RuntimeConfigAccess(
                snapshot(remote, ConfigTransportStatus.REMOTE_PROVIDER),
                remote
            )
        } catch (throwable: Throwable) {
            XposedBridge.log("WaEnhancer config: provider fallback failed: ${throwable.javaClass.simpleName}")
            RuntimeConfigAccess(failedClosed(throwable), xSharedPreferences)
        }
    }

    private fun snapshot(
        preferences: SharedPreferences,
        status: ConfigTransportStatus
    ): RuntimeConfigSnapshot {
        return RuntimeConfigSnapshot(
            disableAllHooks = preferences.getBoolean(RuntimeConfigKeys.DISABLE_ALL_HOOKS, false),
            disableVisualModifications = preferences.getBoolean(
                RuntimeConfigKeys.DISABLE_VISUAL_MODIFICATIONS,
                false
            ),
            safeMode = preferences.getBoolean(RuntimeConfigKeys.SAFE_MODE, false),
            enableLogs = preferences.getBoolean(RuntimeConfigKeys.ENABLE_LOGS, true),
            transportStatus = status,
            enableLiquidGlassPrototype = preferences.getBoolean(
                RuntimeConfigKeys.ENABLE_LIQUID_GLASS_PROTOTYPE,
                false
            ),
            enableIosHomeRedesign = preferences.getBoolean(
                RuntimeConfigKeys.ENABLE_IOS_HOME_REDESIGN,
                false
            )
        )
    }

    private fun failedClosed(throwable: Throwable): RuntimeConfigSnapshot {
        return RuntimeConfigSnapshot(
            disableAllHooks = true,
            disableVisualModifications = true,
            safeMode = true,
            enableLogs = false,
            transportStatus = ConfigTransportStatus.FAILED_CLOSED,
            enableLiquidGlassPrototype = false,
            enableIosHomeRedesign = false,
            failureSummary = throwable.javaClass.simpleName
        )
    }
}
