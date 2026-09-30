package com.wmods.wppenhacer.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.wmods.wppenhacer.xposed.runtime.RuntimeDiagnosticsContract
import com.wmods.wppenhacer.xposed.runtime.RuntimeDiagnosticsStore

class RuntimeDiagnosticsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!IpcCallerValidator.isAllowed(context)) {
            throw SecurityException("Caller is not allowed to publish WaEnhancer diagnostics")
        }
        if (method != RuntimeDiagnosticsContract.METHOD_PUBLISH) return null
        val json = extras?.getString(RuntimeDiagnosticsContract.EXTRA_JSON) ?: return null
        val written = RuntimeDiagnosticsStore.write(context, json)
        return Bundle().apply { putBoolean("written", written) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
