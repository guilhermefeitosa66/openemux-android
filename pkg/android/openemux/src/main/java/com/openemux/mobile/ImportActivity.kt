package com.openemux.mobile

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * "Open with" and "Share" for ROM files: no screen of its own. It hands the
 * files to the library and finishes. Starting the library directly from the
 * file manager would put a second library inside the file manager's task;
 * this brings the app's own task up instead, in front of whatever it shows
 * (a running game stays behind it, paused, never finished).
 */
class ImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = urisOf(intent)
        if (uris.isNotEmpty()) {
            val clip = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            // The read grant goes along to the library's activity, which outlives this one.
            val forward = Intent(this, MainActivity::class.java).setAction(ACTION_IMPORT).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            forward.clipData = clip
            startActivity(forward)
        }
        finish()
    }

    private fun urisOf(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        val listed = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(stream(intent))
            Intent.ACTION_SEND_MULTIPLE -> streams(intent)
            else -> return emptyList()
        }
        // Some senders only fill the clip.
        val clipped = intent.clipData?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty()
        return (listed + clipped).filter { isAcceptableImport(it) }.distinct()
    }

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

    companion object {
        /** The library's own action for files handed over from here. */
        const val ACTION_IMPORT = "com.openemux.mobile.IMPORT_ROMS"
    }
}

/** A content:// document, or a file:// path outside the app's own storage. */
internal fun Context.isAcceptableImport(uri: Uri): Boolean =
    uri.scheme == "content" || (uri.scheme == "file" && !isOwnStorage(uri))

/** A file:// path into the app's own storage: never something a file manager hands over. */
private fun Context.isOwnStorage(uri: Uri): Boolean {
    val path = runCatching { java.io.File(uri.path ?: return true).canonicalPath }.getOrElse { return true }
    return listOf(dataDir, filesDir.parentFile, getExternalFilesDir(null)?.parentFile).any { dir ->
        dir != null && (path + "/").startsWith(dir.canonicalPath + "/")
    }
}
