package com.liftly.app.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Creates and exposes only short-lived files from Liftly's dedicated sharing cache. */
object WorkoutShareFiles {
    const val CACHE_DIRECTORY = "shared_workouts"
    const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    fun createTemporaryFile(
        context: Context,
        suggestedName: String,
        extension: String
    ): File {
        val safeExtension = extension
            .trim()
            .removePrefix(".")
            .lowercase(Locale.ROOT)
        require(safeExtension.matches(Regex("[a-z0-9]{1,10}"))) {
            "A extensão do arquivo compartilhado é inválida."
        }

        val safeName = suggestedName
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "liftly-treino" }
            .padEnd(3, '_')

        val directory = sharingDirectory(context)
        return File.createTempFile("$safeName-", ".$safeExtension", directory)
    }

    fun contentUri(context: Context, file: File): Uri {
        require(isInsideSharingDirectory(context, file)) {
            "O arquivo precisa estar na pasta temporária de compartilhamento do Liftly."
        }
        return FileProvider.getUriForFile(
            context,
            context.packageName + PROVIDER_AUTHORITY_SUFFIX,
            file
        )
    }

    fun buildSendIntent(
        context: Context,
        file: File,
        mimeType: String,
        title: String? = null
    ): Intent {
        require(mimeType.isNotBlank()) { "O tipo do arquivo compartilhado é obrigatório." }
        val uri = contentUri(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun sharingDirectory(context: Context): File =
        File(context.cacheDir, CACHE_DIRECTORY).also { directory ->
            check(directory.isDirectory || directory.mkdirs()) {
                "Não foi possível preparar a pasta temporária de compartilhamento."
            }
        }

    private fun isInsideSharingDirectory(context: Context, file: File): Boolean {
        val directoryPath = sharingDirectory(context).canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        return file.isFile && filePath.startsWith(directoryPath)
    }
}
