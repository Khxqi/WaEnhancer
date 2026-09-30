package com.wmods.wppenhacer.xposed.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wmods.wppenhacer.BuildConfig
import java.io.File

object RuntimeDiagnosticsExporter {
    fun share(context: Context) {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, RuntimeDiagnosticsContract.FILE_NAME)
        file.writeText(RuntimeDiagnosticsStore.read(context))
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export WaEnhancer diagnostics"))
    }
}
