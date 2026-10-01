package com.wmods.wppenhacer.xposed.runtime

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.wmods.wppenhacer.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.wmods.wppenhacer.ui.glass.GlassRuntimeState

object DiagnosticSanitizer {
    private val jidPattern = Regex("(?i)[^\\s,;]+@(s\\.whatsapp\\.net|g\\.us|lid|broadcast)")
    private val longNumberPattern = Regex("(?<![A-Za-z])\\+?\\d[\\d .()-]{5,}\\d")

    fun sanitize(value: String): String {
        return value
            .replace(jidPattern, "<redacted-id>")
            .replace(longNumberPattern, "<redacted-number>")
            .take(512)
    }

    fun failureSummary(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName.ifBlank { "Throwable" }
        val message = throwable.message?.let(::sanitize)?.takeIf { it.isNotBlank() }
        return if (message == null) type else "$type: $message"
    }
}

object RuntimeDiagnostics {
    fun currentJson(): JSONObject {
        val session = RuntimeState.session
        val config = RuntimeState.config
        val capabilities = RuntimeState.capabilities?.snapshot().orEmpty()
        val features = RuntimeState.features?.snapshot().orEmpty()
        val capabilityStates = capabilities.associateBy { it.id }

        return JSONObject().apply {
            put("schemaVersion", 3)
            put("moduleVersion", session?.moduleVersion ?: BuildConfig.VERSION_NAME)
            put("whatsAppVersionName", session?.whatsAppVersionName ?: JSONObject.NULL)
            put("whatsAppVersionCode", session?.whatsAppVersionCode ?: JSONObject.NULL)
            put("androidVersion", session?.androidVersion ?: JSONObject.NULL)
            put("packageName", session?.packageName ?: JSONObject.NULL)
            put("processName", session?.processName ?: JSONObject.NULL)
            put("processContext", session?.processContext ?: JSONObject.NULL)
            put("safeMode", config?.safeMode ?: true)
            put("disableAllHooks", config?.disableAllHooks ?: true)
            put("disableVisualModifications", config?.disableVisualModifications ?: true)
            put("configurationTransport", config?.transportStatus?.name ?: "UNAVAILABLE")
            put("configurationFailure", config?.failureSummary?.let(DiagnosticSanitizer::sanitize) ?: JSONObject.NULL)
            val glass = GlassRuntimeState.snapshot()
            put("glass", JSONObject().apply {
                put("prototypeEnabled", config?.enableLiquidGlassPrototype ?: false)
                put("capabilities", JSONObject().apply {
                    put("runtimeShader", glass.capabilities?.runtimeShaderAvailable ?: false)
                    put("renderEffect", glass.capabilities?.renderEffectAvailable ?: false)
                    put("crossWindowBlur", glass.capabilities?.crossWindowBlurAvailable ?: false)
                    put("highEndGraphics", glass.capabilities?.highEndGraphics ?: false)
                })
                put("backend", glass.backend?.name ?: JSONObject.NULL)
                put("attachedSurfaces", glass.attachedSurfaces)
                put("hardwareAccelerated", glass.hardwareAccelerated ?: JSONObject.NULL)
                put("failure", glass.failure?.let(DiagnosticSanitizer::sanitize) ?: JSONObject.NULL)
            })
            put("stages", JSONArray().apply {
                RuntimeState.stageSnapshot().forEach { record ->
                    put(JSONObject().apply {
                        put("stage", record.stage.name)
                        put("status", record.status.name)
                        put("summary", record.summary ?: JSONObject.NULL)
                    })
                }
            })
            put("capabilities", JSONArray().apply {
                capabilities.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id.value)
                        put("status", record.status.name)
                        put("failure", record.failureSummary ?: JSONObject.NULL)
                    })
                }
            })
            put("features", JSONArray().apply {
                features.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id.value)
                        put("name", record.diagnosticName)
                        put("category", record.category.name)
                        put("enabled", record.enabled)
                        put("status", record.status.name)
                        put("failure", record.failureReason ?: JSONObject.NULL)
                        put("requiredCapabilities", JSONArray().apply {
                            record.requiredCapabilities.sortedBy { it.value }.forEach { id ->
                                val capability = capabilityStates[id]
                                put(JSONObject().apply {
                                    put("id", id.value)
                                    put("status", capability?.status?.name ?: "UNRESOLVED")
                                })
                            }
                        })
                        put("installed", record.status == FeatureStatus.READY)
                        put("runtimeVerified", record.runtimeVerified)
                        put("attemptedHookCount", record.attemptedHookCount ?: JSONObject.NULL)
                        put("installedHookCount", record.installedHookCount ?: JSONObject.NULL)
                        put("rollbackSupport", record.rollbackSupport.name)
                        put("rollbackAttempted", record.rollbackAttempted)
                        put("rollbackSucceeded", record.rollbackSucceeded ?: JSONObject.NULL)
                        put("partialInstallation", record.partialInstallation)
                        put("legacyManagedEnablement", record.legacyManagedEnablement)
                    })
                }
            })
            // READY for a legacy adapter means its installer returned. It is intentionally not
            // reported as semantically active because the legacy class still owns its preference.
            put("activeFeatures", JSONArray())
            put("readyLegacyFeatures", JSONArray().apply {
                features.filter {
                    it.status == FeatureStatus.READY && it.legacyManagedEnablement
                }.forEach { put(it.id.value) }
            })
            put("installedManagedFeatures", JSONArray().apply {
                features.filter {
                    it.status == FeatureStatus.READY && !it.legacyManagedEnablement
                }.forEach { put(it.id.value) }
            })
            put("runtimeVerifiedFeatures", JSONArray().apply {
                features.filter { it.runtimeVerified }.forEach { put(it.id.value) }
            })
            put("failedFeatures", JSONArray().apply {
                features.filter { it.status == FeatureStatus.FAILED }.forEach { put(it.id.value) }
            })
            put("failedCapabilities", JSONArray().apply {
                capabilities.filter { it.status == CapabilityStatus.FAILED }.forEach { put(it.id.value) }
            })
        }
    }
}

object RuntimeDiagnosticsContract {
    const val METHOD_PUBLISH = "publish"
    const val EXTRA_JSON = "diagnostics_json"
    const val MAX_BYTES = 64 * 1024
    const val FILE_NAME = "runtime-diagnostics.json"

    fun uri(): Uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.runtime-diagnostics")
}

object RuntimeDiagnosticsPublisher {
    fun publish(context: Context): Boolean {
        return try {
            val json = RuntimeDiagnostics.currentJson().toString()
            if (json.toByteArray().size > RuntimeDiagnosticsContract.MAX_BYTES) return false
            val extras = Bundle().apply {
                putString(RuntimeDiagnosticsContract.EXTRA_JSON, json)
            }
            context.contentResolver.call(
                RuntimeDiagnosticsContract.uri(),
                RuntimeDiagnosticsContract.METHOD_PUBLISH,
                null,
                extras
            ) != null
        } catch (_: Throwable) {
            false
        }
    }
}

object RuntimeDiagnosticsStore {
    fun write(context: Context, json: String): Boolean {
        if (json.toByteArray().size > RuntimeDiagnosticsContract.MAX_BYTES) return false
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return false
        val sanitized = parsed.toString(2)
        return runCatching {
            File(context.filesDir, RuntimeDiagnosticsContract.FILE_NAME).writeText(sanitized)
            true
        }.getOrDefault(false)
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, RuntimeDiagnosticsContract.FILE_NAME)
        return runCatching { file.readText() }.getOrElse {
            JSONObject().apply {
                put("schemaVersion", 1)
                put("status", "No runtime diagnostics have been received yet")
                put("moduleVersion", BuildConfig.VERSION_NAME)
            }.toString(2)
        }
    }
}
