package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.runtime.HookInstallScope
import com.wmods.wppenhacer.xposed.runtime.registerXposed
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method

class AlwaysOnlinePrivacy(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("always_online", false)) return
        install(HookInstallScope(), Unobfuscator.loadStateChangeMethod(classLoader))
    }

    fun install(scope: HookInstallScope, target: Method) {
        scope.registerXposed(
            "presence-state-change",
            XposedBridge.hookMethod(target, XC_MethodReplacement.DO_NOTHING)
        )
    }

    override fun getPluginName(): String = "Always Online"
}
