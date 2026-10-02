package com.wmods.wppenhacer.xposed.runtime

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.view.ContextThemeWrapper
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
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
        if (!hookInstalled.compareAndSet(false, true)) {
            RuntimeTrace.event("bootstrap-hook-already-installed")
            return
        }
        RuntimeTrace.event("bootstrap-hook-installed", processName)
        RuntimeState.stage(RuntimeStage.PACKAGE_VALIDATION, RuntimeStageStatus.RUNNING)
        XposedHelpers.findAndHookMethod(
            Instrumentation::class.java,
            "callApplicationOnCreate",
            Application::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!initialized.compareAndSet(false, true)) return
                    val application = param.args[0] as Application
                    RuntimeTrace.event("bootstrap-entered", application.packageName)
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
                RuntimeTrace.event("package-rejected", application.packageName)
                RuntimeState.stage(
                    RuntimeStage.PACKAGE_VALIDATION,
                    RuntimeStageStatus.FAILED,
                    "Unsupported host package"
                )
                return
            }
            RuntimeTrace.event("package-accepted", application.packageName)
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
                RuntimeTrace.event("configuration-failed-closed", "Configuration capability unavailable")
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
            RuntimeTrace.event("configuration-ready", config.transportStatus.name)

            RuntimeState.stage(RuntimeStage.HOST_IDENTITY, RuntimeStageStatus.RUNNING)
            val session = capabilities.resolve(RuntimeCapabilities.HOST_SESSION) {
                HostSession.from(application, processName)
            }
            RuntimeState.session = session
            RuntimeState.stage(
                RuntimeStage.HOST_IDENTITY,
                if (session == null) RuntimeStageStatus.FAILED else RuntimeStageStatus.READY
            )
            if (session == null) {
                RuntimeTrace.event("host-identity-failed")
                publish(application)
                return
            }

            val supportedPatterns = runCatching {
                application.resources.getStringArray(R.array.supported_versions_wpp).asList()
            }.getOrDefault(emptyList())
            val compatibility = WhatsAppVersionPolicy.evaluate(
                versionName = session.whatsAppVersionName,
                supportedPatterns = supportedPatterns,
                bypassEnabled = runCatching {
                    configAccess.legacyPreferences.getBoolean("bypass_version_check", false)
                }.getOrDefault(false)
            )
            RuntimeState.hostCompatibility = compatibility
            RuntimeTrace.event(
                if (compatibility.metadataAccepted) "version-accepted" else "version-rejected",
                "${session.whatsAppVersionName} (${compatibility.mode.name})"
            )

            val registry = FeatureRegistry(capabilities)
            RuntimeState.features = registry
            GlassFeatureRegistry.resolve(capabilities, application)
            GlassFeatureRegistry.register(registry, capabilities, application, config)
            RuntimeTrace.event("glass-feature-registered")
            var privacyConfig = PrivacyConfigReader.read(
                configAccess.legacyPreferences,
                config
            )
            RuntimeState.privacyConfig = privacyConfig
            if (config.disableAllHooks || config.transportStatus == ConfigTransportStatus.FAILED_CLOSED) {
                PrivacyFeatureRegistry.register(
                    registry,
                    capabilities,
                    loader,
                    configAccess.legacyPreferences,
                    privacyConfig
                )
                LegacyRuntimeAdapter.registerFeatures(
                    registry,
                    loader,
                    configAccess.legacyPreferences,
                    config
                )
                registry.installAll()
                RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.SKIPPED, "Global kill switch")
                RuntimeState.stage(RuntimeStage.STOPPED, RuntimeStageStatus.READY, "All hooks disabled")
                RuntimeTrace.event("startup-stopped", "Global kill switch")
                publish(application)
                return
            }

            if (config.safeMode) {
                PrivacyFeatureRegistry.register(
                    registry,
                    capabilities,
                    loader,
                    configAccess.legacyPreferences,
                    privacyConfig
                )
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
                RuntimeTrace.event("startup-complete", "Safe mode")
                publish(application)
                return
            }

            if (compatibility.mode == HostCompatibilityMode.FRAMEWORK_ONLY) {
                RuntimeState.stage(
                    RuntimeStage.RESOLVER_INITIALIZATION,
                    RuntimeStageStatus.SKIPPED,
                    compatibility.summary
                )
                RuntimeState.stage(
                    RuntimeStage.CAPABILITY_RESOLUTION,
                    RuntimeStageStatus.SKIPPED,
                    compatibility.summary
                )
                RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.RUNNING)
                RuntimeTrace.event("feature-registry-started", "Framework-only compatibility mode")
                registry.installAll()
                RuntimeState.stage(
                    RuntimeStage.FEATURE_INSTALLATION,
                    RuntimeStageStatus.READY,
                    "Only resolver-independent features considered"
                )
                RuntimeState.stage(
                    RuntimeStage.COMPLETE,
                    RuntimeStageStatus.READY,
                    compatibility.summary
                )
                RuntimeTrace.event("startup-complete", compatibility.summary)
                publish(application)
                return
            }

            RuntimeState.stage(RuntimeStage.RESOLVER_INITIALIZATION, RuntimeStageStatus.RUNNING)
            RuntimeTrace.event("resolver-initialization-started")
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
            RuntimeTrace.event("capability-resolution-started")
            val cacheStatus = capabilities.resolve(RuntimeCapabilities.RESOLVER_CACHE) {
                val status = UnobfuscatorCache.init(application)
                RuntimeState.resolverCacheStatus = status
                RuntimeTrace.event(
                    "resolver-cache-${status.disposition.name.lowercase()}",
                    status.invalidationReason?.name ?: "host version unchanged"
                )
                SharedPreferencesWrapper.hookInit(application.classLoader)
                ReflectionUtils.initCache(application)
                status
            }
            if (cacheStatus == null && RuntimeState.resolverCacheStatus == null) {
                RuntimeTrace.event("resolver-cache-failed")
            }

            if (compatibility.mode == HostCompatibilityMode.FULL_RUNTIME_BYPASS && dexKitReady == true) {
                runCatching { installExpirationFallback(loader) }
                    .onFailure { XposedBridge.log(it) }
            }

            if (dexKitReady == true && capabilities.isReady(RuntimeCapabilities.RESOLVER_CACHE)) {
                capabilities.resolve(RuntimeCapabilities.MESSAGE_COMPONENTS) {
                    LegacyRuntimeAdapter.initializeMessageComponents(loader)
                }
            }

            if (moduleContext != null && capabilities.isReady(RuntimeCapabilities.MESSAGE_COMPONENTS)) {
                capabilities.resolve(RuntimeCapabilities.LEGACY_CORE) {
                    LegacyRuntimeAdapter.initializeCore(
                        application,
                        loader,
                        configAccess.legacyPreferences
                    )
                }
            }

            privacyConfig = PrivacyConfigReader.read(
                configAccess.legacyPreferences,
                config,
                privateBoolean = { key ->
                    if (capabilities.isReady(RuntimeCapabilities.LEGACY_CORE)) {
                        WppCore.getPrivBoolean(key, false)
                    } else {
                        false
                    }
                }
            )
            RuntimeState.privacyConfig = privacyConfig
            PrivacyCapabilityResolver.resolve(capabilities, loader, privacyConfig)
            RuntimeState.stage(
                RuntimeStage.CAPABILITY_RESOLUTION,
                if (capabilities.isReady(RuntimeCapabilities.LEGACY_CORE)) {
                    RuntimeStageStatus.READY
                } else {
                    RuntimeStageStatus.FAILED
                }
            )

            RuntimeState.stage(RuntimeStage.FEATURE_INSTALLATION, RuntimeStageStatus.RUNNING)
            RuntimeTrace.event("feature-registry-started", "Resolver-dependent runtime")
            PrivacyFeatureRegistry.register(
                registry,
                capabilities,
                loader,
                configAccess.legacyPreferences,
                privacyConfig
            )
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
            RuntimeTrace.event("startup-complete", "Feature registry finished")
            publish(application)
        } catch (throwable: Throwable) {
            XposedBridge.log(throwable)
            RuntimeTrace.event("startup-failed-closed", DiagnosticSanitizer.failureSummary(throwable))
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
