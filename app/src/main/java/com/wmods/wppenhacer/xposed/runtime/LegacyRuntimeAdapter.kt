package com.wmods.wppenhacer.xposed.runtime

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WaCallback
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

object LegacyRuntimeAdapter {
    private val featureClassNames = listOf(
        "com.wmods.wppenhacer.xposed.features.others.DebugFeature",
        "com.wmods.wppenhacer.xposed.features.others.MinorFixes",
        "com.wmods.wppenhacer.xposed.features.listeners.ContactItemListener",
        "com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener",
        "com.wmods.wppenhacer.xposed.features.providers.MenuStatusProvider",
        "com.wmods.wppenhacer.xposed.features.general.ShowEditMessage",
        "com.wmods.wppenhacer.xposed.features.customization.CustomToolbar",
        "com.wmods.wppenhacer.xposed.features.customization.CustomView",
        "com.wmods.wppenhacer.xposed.features.customization.BubbleColors",
        "com.wmods.wppenhacer.xposed.features.others.ActivityController",
        "com.wmods.wppenhacer.xposed.features.customization.CustomThemeV2",
        "com.wmods.wppenhacer.xposed.features.customization.FloatingBottomBar",
        "com.wmods.wppenhacer.xposed.features.general.ChatLimit",
        "com.wmods.wppenhacer.xposed.features.customization.SeparateGroup",
        "com.wmods.wppenhacer.xposed.features.customization.ShowOnline",
        "com.wmods.wppenhacer.xposed.features.customization.HideSeenView",
        "com.wmods.wppenhacer.xposed.features.customization.HideTabs",
        "com.wmods.wppenhacer.xposed.features.customization.IGStatus",
        "com.wmods.wppenhacer.xposed.features.media.MediaQuality",
        "com.wmods.wppenhacer.xposed.features.general.NewChat",
        "com.wmods.wppenhacer.xposed.features.general.Others",
        "com.wmods.wppenhacer.xposed.features.general.PinnedLimit",
        "com.wmods.wppenhacer.xposed.features.customization.CustomTime",
        "com.wmods.wppenhacer.xposed.features.general.ShareLimit",
        "com.wmods.wppenhacer.xposed.features.media.StatusDownload",
        "com.wmods.wppenhacer.xposed.features.general.CallType",
        "com.wmods.wppenhacer.xposed.features.media.MediaPreview",
        "com.wmods.wppenhacer.xposed.features.customization.FilterGroups",
        "com.wmods.wppenhacer.xposed.features.general.Tasker",
        "com.wmods.wppenhacer.xposed.features.general.DeleteStatus",
        "com.wmods.wppenhacer.xposed.features.media.DownloadViewOnce",
        "com.wmods.wppenhacer.xposed.features.others.Channels",
        "com.wmods.wppenhacer.xposed.features.media.DownloadProfile",
        "com.wmods.wppenhacer.xposed.features.others.ChatFilters",
        "com.wmods.wppenhacer.xposed.features.others.GroupAdmin",
        "com.wmods.wppenhacer.xposed.features.others.Stickers",
        "com.wmods.wppenhacer.xposed.features.others.CopyStatus",
        "com.wmods.wppenhacer.xposed.features.others.CopySelectionMessage",
        "com.wmods.wppenhacer.xposed.features.others.TextStatusComposer",
        "com.wmods.wppenhacer.xposed.features.others.ToastViewer",
        "com.wmods.wppenhacer.xposed.features.others.MenuHome",
        "com.wmods.wppenhacer.xposed.features.others.AudioTranscript",
        "com.wmods.wppenhacer.xposed.features.others.GoogleTranslate",
        "com.wmods.wppenhacer.xposed.features.customization.ContactVerify",
        "com.wmods.wppenhacer.xposed.features.media.CallRecording",
        "com.wmods.wppenhacer.xposed.features.others.BackupRestore",
        "com.wmods.wppenhacer.xposed.features.others.JumpFirstMessage",
        "com.wmods.wppenhacer.xposed.features.general.AboutContactPicker",
        "com.wmods.wppenhacer.xposed.features.customization.DefaultEmoji",
        "com.wmods.wppenhacer.xposed.features.general.CaptureDevice",
        "com.wmods.wppenhacer.xposed.features.providers.ContextMenuActionProvider"
    )

    fun initializeMessageComponents(loader: ClassLoader): Boolean {
        FMessageWpp.initialize(loader)
        FStatusWpp.initialize(loader)
        ProtocolTreeNodeWpp.initialize(loader)
        WaContactWpp.initialize(loader)
        return true
    }

    fun initializeCore(application: Application, loader: ClassLoader, pref: SharedPreferences): Boolean {
        AlertDialogWpp.initDialog(loader)
        WppCore.initialize(loader, pref)
        DesignUtils.setPrefs(pref)
        Utils.init()
        application.registerActivityLifecycleCallbacks(WaCallback())
        return true
    }

    fun registerFeatures(
        registry: FeatureRegistry,
        loader: ClassLoader,
        preferences: SharedPreferences,
        config: RuntimeConfigSnapshot
    ) {
        registry.register(
            FeatureSpec(
                id = FeatureId("legacy.general.disable-secure-flag"),
                diagnosticName = "Disable secure window flag",
                category = FeatureCategory.GENERAL,
                requiredCapabilities = emptySet(),
                enabled = { !config.safeMode && !config.disableAllHooks },
                installer = {
                    installDisableSecureFlag().forEachIndexed { index, handle ->
                        register("window-secure-flag[$index]", handle)
                    }
                }
            )
        )

        featureClassNames.forEach { className ->
            val category = categoryFor(className)
            registry.register(
                FeatureSpec(
                    id = FeatureId(stableId(className, category)),
                    diagnosticName = className.substringAfterLast('.'),
                    category = category,
                    requiredCapabilities = setOf(RuntimeCapabilities.LEGACY_CORE),
                    enabled = {
                        !config.safeMode && !config.disableAllHooks &&
                            !(config.disableVisualModifications && category == FeatureCategory.VISUAL) &&
                            !LegacyHomeConflictPolicy.blocks(className, config)
                    },
                    installer = {
                        val clazz = LegacyRuntimeAdapter::class.java.classLoader!!.loadClass(className)
                        val constructor = clazz.getConstructor(
                            ClassLoader::class.java,
                            SharedPreferences::class.java
                        )
                        val feature = constructor.newInstance(loader, preferences) as Feature
                        feature.doHook()
                    },
                    legacyManagedEnablement = true
                )
            )
        }
    }

    @SuppressLint("WrongConstant")
    fun registerAuthenticatedReceivers(application: Application, preferences: SharedPreferences) {
        val expectedToken = RuntimeControl.token(preferences)
        registerReceiver(application, RuntimeControl.ACTION_RESTART) { context, intent ->
            if (!RuntimeControl.authenticate(intent, expectedToken)) return@registerReceiver
            if (context.packageName == intent.getStringExtra("PKG")) Utils.doRestart(context)
        }
        registerReceiver(application, RuntimeControl.ACTION_CHECK) { context, intent ->
            if (!RuntimeControl.authenticate(intent, expectedToken)) return@registerReceiver
            sendRuntimeState(context, expectedToken)
        }
        registerReceiver(application, RuntimeControl.ACTION_MANUAL_RESTART) { _, intent ->
            if (!RuntimeControl.authenticate(intent, expectedToken)) return@registerReceiver
            WppCore.setPrivBoolean("need_restart", true)
        }
    }

    fun sendRuntimeState(context: Context, token: String?) {
        if (token.isNullOrBlank()) return
        runCatching {
            context.sendBroadcast(Intent(RuntimeControl.ACTION_RUNTIME_STATE).apply {
                putExtra("VERSION", RuntimeState.session?.whatsAppVersionName)
                putExtra("PKG", context.packageName)
                putExtra(RuntimeControl.TOKEN_EXTRA, token)
                setPackage(BuildConfig.APPLICATION_ID)
            })
        }
    }

    private fun registerReceiver(
        application: Application,
        action: String,
        callback: (Context, Intent) -> Unit
    ) {
        ContextCompat.registerReceiver(
            application,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = callback(context, intent)
            },
            IntentFilter(action),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installDisableSecureFlag(): List<HookHandle> {
        val setFlags = XposedHelpers.findAndHookMethod(
            Window::class.java,
            "setFlags",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    val mask = param.args[1] as Int
                    param.args[0] = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    param.args[1] = mask and WindowManager.LayoutParams.FLAG_SECURE.inv()
                }
            }
        )
        val addFlags = XposedHelpers.findAndHookMethod(
            Window::class.java,
            "addFlags",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[0] as Int
                    val newFlags = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    param.args[0] = newFlags
                    if (newFlags == 0) param.result = null
                }
            }
        )
        return listOf(setFlags, addFlags).map { unhook ->
            object : HookHandle {
                override fun unhook() = unhook.unhook()
            }
        }
    }

    private fun categoryFor(className: String): FeatureCategory = when {
        ".features.privacy." in className -> FeatureCategory.PRIVACY
        ".features.customization." in className -> FeatureCategory.VISUAL
        ".features.media." in className -> FeatureCategory.MEDIA
        ".features.providers." in className || ".features.listeners." in className -> FeatureCategory.SUPPORT
        else -> FeatureCategory.GENERAL
    }

    private fun stableId(className: String, category: FeatureCategory): String {
        val simple = className.substringAfterLast('.')
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .lowercase()
        return "legacy.${category.name.lowercase()}.$simple"
    }
}
