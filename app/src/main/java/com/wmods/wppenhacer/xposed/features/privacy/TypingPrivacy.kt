package com.wmods.wppenhacer.xposed.features.privacy

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method
import com.wmods.wppenhacer.xposed.runtime.HookInstallScope
import com.wmods.wppenhacer.xposed.runtime.PrivacyConfigReader
import com.wmods.wppenhacer.xposed.runtime.PrivacyConfigSnapshot
import com.wmods.wppenhacer.xposed.runtime.RuntimeConfigSnapshot
import com.wmods.wppenhacer.xposed.runtime.ConfigTransportStatus
import com.wmods.wppenhacer.xposed.runtime.registerXposed

class TypingPrivacy(
    loader: ClassLoader,
    preferences:SharedPreferences
) : Feature(loader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        val method: Method = Unobfuscator.loadGhostModeMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(method))
        val config = PrivacyConfigReader.read(
            prefs,
            RuntimeConfigSnapshot(false, false, false, Feature.DEBUG, ConfigTransportStatus.XSHARED_PREFERENCES),
            privateBoolean = { key -> WppCore.getPrivBoolean(key, false) }
        )
        install(HookInstallScope(), method, config)
    }

    fun install(scope: HookInstallScope, method: Method, config: PrivacyConfigSnapshot) {
        val unhook = XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val type = ReflectionUtils.getArg(param.args, Int::class.javaObjectType, 0)
                val jidObj = ReflectionUtils.getArg(param.args, FMessageWpp.UserJid.TYPE_JID, 0)

                if (jidObj == null) {
                    logDebug("UserJid not found in Typing Privacy")
                    return
                }

                val userJid = FMessageWpp.UserJid(jidObj)
                val privacy = CustomPrivacy.getJSON(userJid.phoneNumber)

                val customHideTyping = privacy.optBoolean("HideTyping", config.hideTyping) ||
                    config.privateGhostMode
                val customHideRecording = privacy.optBoolean("HideRecording", config.hideRecording) ||
                    config.privateGhostMode

                if ((type == 1 && customHideRecording) ||
                    (type == 0 && customHideTyping)
                ) {
                    param.result = null
                }
            }
        })
        scope.registerXposed("typing-recording-state", unhook)
    }

    override fun getPluginName(): String {
        return "Typing Privacy"
    }
}
