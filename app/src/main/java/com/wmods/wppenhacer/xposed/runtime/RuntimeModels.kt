package com.wmods.wppenhacer.xposed.runtime

import android.app.Application
import android.os.Build
import com.wmods.wppenhacer.BuildConfig

data class HostSession(
    val packageName: String,
    val processName: String,
    val whatsAppVersionName: String,
    val whatsAppVersionCode: Long,
    val moduleVersion: String,
    val androidVersion: String,
    val processContext: String?
) {
    companion object {
        fun from(application: Application, processName: String): HostSession {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val context = processName.substringAfter(':', "").ifBlank { null }
            return HostSession(
                packageName = application.packageName,
                processName = processName,
                whatsAppVersionName = packageInfo.versionName.orEmpty(),
                whatsAppVersionCode = versionCode,
                moduleVersion = BuildConfig.VERSION_NAME,
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                processContext = context
            )
        }
    }
}

enum class RuntimeStage {
    PACKAGE_VALIDATION,
    CONFIGURATION,
    HOST_IDENTITY,
    RESOLVER_INITIALIZATION,
    CAPABILITY_RESOLUTION,
    FEATURE_INSTALLATION,
    DIAGNOSTICS,
    COMPLETE,
    STOPPED
}

enum class RuntimeStageStatus {
    PENDING,
    RUNNING,
    READY,
    FAILED,
    SKIPPED
}

data class RuntimeStageRecord(
    val stage: RuntimeStage,
    val status: RuntimeStageStatus,
    val summary: String? = null
)
