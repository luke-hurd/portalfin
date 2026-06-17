package org.jellyfin.mobile.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class StorageManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    // App-private external files dir (e.g. /storage/emulated/0/Android/data/<pkg>/files).
    // Requires NO runtime storage permission on any Android version — unlike
    // Environment.getExternalStorageDirectory(), which on Android 9 (the Portal)
    // needs WRITE_EXTERNAL_STORAGE granted at runtime. The app never requested
    // that permission, so createDirectory() returned null and every download
    // failed with "Unable to find or create folder <name>". Downloads are
    // app-private anyway, so the private dir is the correct home for them.
    private val defaultStorageLocation
        get() = (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath

    init {
        ensureNoMedia(getStorageLocation())
    }

    fun getStorageLocation(): DocumentFile = appPreferences.storageLocation?.toUri()?.let {
        DocumentFile.fromTreeUri(context, it)
    } ?: DocumentFile.fromFile(File(defaultStorageLocation))

    fun changeStorageLocation(location: Uri) {
        if (appPreferences.storageLocation?.toUri() == location) return

        val documentFile = DocumentFile.fromTreeUri(context, location) ?: error("Invalid location $location")
        context.contentResolver.takePersistableUriPermission(
            documentFile.uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        ensureNoMedia(documentFile)
        appPreferences.storageLocation = documentFile.uri.toString()
    }

    private fun ensureNoMedia(documentFile: DocumentFile) {
        if (documentFile.findFile(NOMEDIA_FILE) == null) {
            documentFile.createFile("", NOMEDIA_FILE)
        }
    }

    companion object {
        const val NOMEDIA_FILE = ".nomedia"
    }
}
