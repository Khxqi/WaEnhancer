package com.wmods.wppenhacer.xposed.runtime

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.view.ContextThemeWrapper
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object RuntimeBootstrap {
    private val hookInstalled = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    fun install(loader: ClassLoader, sourceDir: String, processName: String) {
        if (!hookInstalled.compareAndSet(false, true)) return
        RuntimeState.stage(RuntimeStage.PACKAGE_VALIDATION, RuntimeStageStatus.RUNNING)
        XposedHelpers.findAndHookMethod(
            Instrumentation::class.java,
            "callApplicationOnCreate",
            Application::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!initialized.compareAndSet(false, true)) return
                    val application = param.args[0] as Application
                    initialize(application, loader, sourceDir, processName)
                }
            }
        )
    }

    private fun initialize(
        application: Application,
        loader: ClassLoader,
        sourceDir: String,
        processName: String
    ) {
        try {
            if (application.packageName != FeatureLoader.PACKAGE_WPP) {
                RuntimeState.stage(
                    RuntimeStage.PACKAGE_VALIDATION,
                    RuntimeStageStatus.FAILED,
                    "Unsupported host package"
                )
                return
            }
            RuntimeState.stage(RuntimeStage.PACKAGE_VALIDATION, RuntimeStageStatus.READY)
            FeatureLoader.mApp = application
            Utils.appClassLoader = loader

            val capabilities = CapabilityRegistry()
            RuntimeState.capabilities = capabilities

            RuntimeState.stage(RuntimeStage.CONFIGURATION, RuntimeStageStatus.RUNNING)
            val configAccess = capabilities.resolve(RuntimeCapabilities.CONFIG) {
                RuntimeConfigReader.read(application)
            }
            if (configAccess == null) {
                RuntimeState.stage(RuntimeStage.CONFIGURATION, RuntimeStageStatus.FAILED)
                return
            }
            val config = configAccess.snapshot
            RuntimeState.config = config
            Feature.DEBUG = config.enableLogs
            Utils.xprefs = configAccess.legacyPreferences
            RuntimeState.stage(
                RuntimeStage.CONFIGURATION,
                if (config.transportStatus == ConfigTransportStatus.FAILED_CLOSED) {
                    RuntimeStageStatus.FAILED
                } else {
                    RuntimeStageStatus.READY
                },
                config.failureSummary
            )

            RuntimeState.stage(RuntimeStage.HOST_IDENTITY, RuntimeStageStatus.RUNNING)
            val session = capabilities.resolve(RuntimeCapabilities.HOST_SESSION) {
                HostSession.from(application, processName)
            }
            RuntimeState.session = session
            RuntimeState.stage(
                RuntimeStage.HOST_IDENTITY,
                if (session == null) RuntimeStageStatus.FAILED else RuntimeStageStatus.READY
            )

            val registry = FeatureRegistry(capabilities)
            RuntimeState.features = registry
            if (config.disableAllHooks || config.transportStatus == ConfigTransportStatus.FAILED_CLOSED) {
                RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.SKIPPED, "Global kill switch")
                RuntimeState.stage(RuntimeStage.STOPPED, RuntimeStageStatus.READY, "All hooks disabled")
                publish(application)
                return
            }

            if (config.safeMode) {
                LegacyRuntimeAdapter.registerFeatures(
                    registry,
                    loader,
                    configAccess.legacyPreferences,
                    config
                )
                registry.installAll()
                RuntimeState.stage(RuntimeStage.RESOLVER_INITIALIZATION, RuntimeStageStatus.SKIPPED, "Safe mode")
                RuntimeState.stage(RuntimeStage.CAPABILITY_RESOLUTION, RuntimeStageStatus.SKIPPED, "Safe mode")
                RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.READY, "Optional features skipped")
                RuntimeState.stage(RuntimeStage.COMPLETE, RuntimeStageStatus.READY, "Safe mode")
                publish(application)
                return
            }

            RuntimeState.stage(RuntimeStage.RESOLVER_INITIALIZATION, RuntimeStageStatus.RUNNING)
            val moduleContext = capabilities.resolve(RuntimeCapabilities.MODULE_RESOURCES) {
                ContextThemeWrapper(application, R.style.AppTheme) as Context
            }
            if (moduleContext != null) FeatureLoader.moduleContext = moduleContext

            val dexKitReady = capabilities.resolve(
                RuntimeCapabilities.DEXKIT,
                resolver = { Unobfuscator.initWithPath(sourceDir) },
                validator = { it }
            )
            RuntimeState.stage(
                RuntimeStage.RESOLVER_INITIALIZATION,
                if (dexKitReady == true) RuntimeStageStatus.READY else RuntimeStageStatus.FAILED
            )

            RuntimeState.stage(RuntimeStage.CAPABILITY_RESOLUTION, RuntimeStageStatus.RUNNING)
            capabilities.resolve(RuntimeCapabilities.RESOLVER_CACHE) {
                UnobfuscatorCache.init(application)
                SharedPreferencesWrapper.hookInit(application.classLoader)
                ReflectionUtils.initCache(application)
                true
            }

            val supported = isSupportedVersion(application, session?.whatsAppVersionName.orEmpty())
            if (!supported && dexKitReady == true) {
                runCatching { installExpirationFallback(loader) }
                    .onFailure { XposedBridge.log(it) }
                if (!configAccess.legacyPreferences.getBoolean("bypass_version_check", false)) {
                    RuntimeState.stage(
                        RuntimeStage.CAPABILITY_RESOLUTION,
                        RuntimeStageStatus.FAILED,
                        "Unsupported WhatsApp version; optional features not installed"
                    )
                    publish(application)
                    return
                }
            }

            if (dexKitReady == true && moduleContext != null &&
                capabilities.isReady(RuntimeCapabilities.RESOLVER_CACHE)
            ) {
                capabilities.resolve(RuntimeCapabilities.LEGACY_CORE) {
                    LegacyRuntimeAdapter.initializeCore(
                        application,
                        loader,
                        configAccess.legacyPreferences
                    )
                }
            }
            RuntimeState.stage(
                RuntimeStage.CAPABILITY_RESOLUTION,
                if (capabilities.isReady(RuntimeCapabilities.LEGACY_CORE)) {
                    RuntimeStageStatus.READY
                } else {
                    RuntimeStageStatus.FAILED
                }
            )

            RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.RUNNING)
            LegacyRuntimeAdapter.registerFeatures(
                registry,
                loader,
                configAccess.legacyPreferences,
                config
            )
            registry.installAll()
            LegacyRuntimeAdapter.registerAuthenticatedReceivers(application, configAccess.legacyPreferences)
            LegacyRuntimeAdapter.sendRuntimeState(
                application,
                RuntimeControl.token(configAccess.legacyPreferences)
            )
            RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.READY)
            RuntimeState.stage(RuntimeStage.COMPLETE, RuntimeStageStatus.READY)
            publish(application)
        } catch (throwable: Throwable) {
            XposedBridge.log(throwable)
            RuntimeState.stage(
                RuntimeStage.STOPPED,
                RuntimeStageStatus.FAILED,
                DiagnosticSanitizer.failureSummary(throwable)
            )
            publish(application)
        }
    }

    private fun publish(application: Application) {
        RuntimeState.stage(RuntimeStage.DIAGNOSTICS, RuntimeStageStatus.RUNNING)
        val published = RuntimeDiagnosticsPublisher.publish(application)
        RuntimeState.stage(
            RuntimeStage.DIAGNOSTICS,
            if (published) RuntimeStageStatus.READY else RuntimeStageStatus.FAILED,
            if (published) null else "Companion diagnostics provider unavailable"
        )
        if (published) RuntimeDiagnosticsPublisher.publish(application)
    }

    private fun isSupportedVersion(application: Application, version: String): Boolean {
        return application.resources.getStringArray(R.array.supported_versions_wpp)
            .any { version.startsWith(it.replace(".xx", "")) }
    }

    fun installExpirationFallback(classLoader: ClassLoader) {
        val expirationClass = Unobfuscator.loadExpirationClass(classLoader)
        ReflectionUtils.findAllMethodsUsingFilter(expirationClass) { it.returnType == Date::class.java }
            .forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = Calendar.getInstance().apply { set(2099, 11, 31) }.time
                    }
                })
            }
    }
}
