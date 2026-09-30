package com.wmods.wppenhacer.provider

import android.content.Context
import android.os.Binder
import android.os.Process
import com.wmods.wppenhacer.BuildConfig

object IpcCallerValidator {
    private const val OFFICIAL_WHATSAPP = "com.whatsapp"

    fun isAllowed(context: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        return packages.any { it == OFFICIAL_WHATSAPP || it == BuildConfig.APPLICATION_ID }
    }
}
