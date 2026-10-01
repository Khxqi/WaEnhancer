package com.wmods.wppenhacer.xposed.features.privacy

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadForwardClassMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadForwardTagMethod
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener.OnConversationItemListener
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import com.wmods.wppenhacer.xposed.runtime.ForwardTagTarget
import com.wmods.wppenhacer.xposed.runtime.HookHandle
import com.wmods.wppenhacer.xposed.runtime.HookInstallScope
import com.wmods.wppenhacer.xposed.runtime.registerXposed

class TagMessage(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {


    override fun doHook() {
        val method = loadForwardTagMethod(classLoader)
        logDebug(getMethodDescriptor(method))
        val forwardClass = loadForwardClassMethod(classLoader)
        logDebug("ForwardClass: " + forwardClass.name)

        if (prefs.getBoolean("hidetag", false)) {
            installHideForwardTag(HookInstallScope(), ForwardTagTarget(method, forwardClass))
        }

        if (prefs.getBoolean("broadcast_tag", false)) {
            installBroadcastIndicator(HookInstallScope())
        }
    }

    fun installHideForwardTag(scope: HookInstallScope, target: ForwardTagTarget) {
        val unhook = XposedBridge.hookMethod(target.method, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val arg = param.args[0] as Long
                if (arg == 1L) {
                    if (ReflectionUtils.isCalledFromClass(target.forwardClass)) {
                        param.args[0] = 0
                    }
                }
            }
        })
        scope.registerXposed("forwarded-tag", unhook)
    }

    fun installBroadcastIndicator(scope: HookInstallScope) {
        val listener = object : OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                if (fMessage.key.isFromMe) return
                val dateTextView = view.findViewById<TextView>(Utils.getID("date", "id")) ?: return
                val dateWrapper = dateTextView.parent as ViewGroup
                val id = Utils.getID("broadcast_icon", "id")
                val res = dateWrapper.findViewById<View?>(id)
                if (fMessage.isBroadcast && res == null) {
                    val broadcast = ImageView(dateWrapper.context)
                    broadcast.id = id
                    broadcast.setImageDrawable(DesignUtils.getDrawableByName("broadcast_status_icon"))
                    dateWrapper.addView(broadcast, 0)
                } else if (!fMessage.isBroadcast && res != null) {
                    dateWrapper.removeView(res)
                }
            }
        }
        ConversationItemListener.conversationListeners.add(listener)
        scope.register("broadcast-tag-listener", object : HookHandle {
            override fun unhook() {
                ConversationItemListener.conversationListeners.remove(listener)
            }
        })
    }

    override fun getPluginName(): String {
        return "Tag Message"
    }
}
