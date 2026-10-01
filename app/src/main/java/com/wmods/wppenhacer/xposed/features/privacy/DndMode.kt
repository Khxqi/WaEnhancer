package com.wmods.wppenhacer.xposed.features.privacy

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivBoolean
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadDndModeMethod
import de.robv.android.xposed.XC_MethodReplacement
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import com.wmods.wppenhacer.xposed.runtime.HookInstallScope
import com.wmods.wppenhacer.xposed.runtime.registerXposed
import java.lang.reflect.Method

class DndMode(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        if (!getPrivBoolean("dndmode", false)) return
        val dndMethod = loadDndModeMethod(classLoader)
        logDebug(getMethodDescriptor(dndMethod))
        install(HookInstallScope(), dndMethod)
    }

    fun install(scope: HookInstallScope, target: Method) {
        scope.registerXposed(
            "dnd-dispatch",
            XposedBridge.hookMethod(target, XC_MethodReplacement.DO_NOTHING)
        )
    }

    override fun getPluginName(): String {
        return "Dnd Mode"
    }
}
