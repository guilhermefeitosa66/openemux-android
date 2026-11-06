package com.openemux.mobile.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinForms
import com.openemux.mobile.data.SkinOrientation
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.launch.ImportedSkinRenderer
import com.openemux.mobile.launch.OpenEmuxSkinRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/*
 * The skin list's miniatures: each skin in OpenEmux's format drawn the way the game draws it on this device, in the
 * layout of the device's form held the way it is held now, so a card shows the skin itself (a skin edited looks
 * edited) and not a drawing of a controller in two of its colours.
 *
 * A miniature is made from the skin, never shipped with it: which one is right depends on the device and on how it is
 * held, and one made from the skin cannot go out of date. It is drawn once, kept in memory for this run of the app and
 * in the cache folder for the next ones, and drawn again when the skin's file changes or the app is updated.
 */

/** Where a miniature's skin is read from: the package of a bundled variant, or the folder of one of the player's. */
sealed interface SkinThumbSource {
    data class Bundled(val variant: SkinVariant) : SkinThumbSource

    data class Own(val dir: File) : SkinThumbSource
}

/** A miniature kept: [stamp] says what it was drawn from (the skin's file and when it last changed). */
private class Thumb(val stamp: String, val image: ImageBitmap)

/** The miniatures drawn this run of the app, by skin, form and orientation: a list opened again shows them at once. */
private val thumbs = LruCache<String, Thumb>(48)

/** The cache folder's miniatures: `<skin, form and orientation>-<what it was drawn from>.png`, one per skin kept. */
private const val THUMB_DIR = "skin-thumbs"

/** The longest side a miniature is drawn at: sharp on a card, small enough to keep a list's worth of them. */
private const val THUMB_PX = 720

private const val TAG = "SkinThumbnail"

/** The device's orientation as a skin's: the layout of it a miniature shows. */
@Composable
fun currentSkinOrientation(): SkinOrientation =
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) SkinOrientation.LANDSCAPE else SkinOrientation.PORTRAIT

/**
 * The miniature of [console]'s skin at [source] for this device, drawn off the main thread; null until it is drawn, and
 * for a skin that cannot be (not in OpenEmux's format, unreadable, or no [source]). Drawn again when the skin's file
 * changes ([revision] bumps after an edit) or the device is turned.
 */
@Composable
fun rememberSkinThumbnail(console: Console, source: SkinThumbSource?, revision: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val form = currentSkinForm()
    val orientation = currentSkinOrientation()
    val key = source?.let { "${console.id}|$it|${form.id}|${orientation.id}" }
    val image by produceState(key?.let { thumbs.get(it)?.image }, key, revision) {
        if (source == null || key == null) {
            value = null
            return@produceState
        }
        value = thumbs.get(key)?.image
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = file(context, console, source) ?: return@runCatching null
                // The app's own update time too: a newer app may draw the same skin another way.
                val updated = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
                val stamp = "${file.path}|${file.lastModified()}|${file.length()}|$updated"
                thumbs.get(key)?.takeIf { it.stamp == stamp }?.let { return@runCatching it.image }
                val kept = keptFile(context, key, stamp)
                val bitmap = kept.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) } ?: run {
                    val pkg = read(file) ?: return@runCatching null
                    val layout = SkinForms.shown(pkg.skin.layouts.keys, form, orientation) ?: return@runCatching null
                    OpenEmuxSkinRenderer(context).thumbnail(pkg, layout, THUMB_PX).also { keep(kept, it) }
                }
                bitmap.asImageBitmap().also { thumbs.put(key, Thumb(stamp, it)) }
            }.onFailure { Log.w(TAG, "no miniature of $source for ${console.id}", it) }.getOrNull()
        }
    }
    return image
}

/** The file that changes when the skin does: a bundled package itself, a folder's skin.json (every save rewrites it). */
private fun file(context: Context, console: Console, source: SkinThumbSource): File? = when (source) {
    is SkinThumbSource.Bundled -> ImportedSkinRenderer.bundled(context, console, source.variant)?.takeIf(ImportedSkinRenderer::isOpenEmuxPackage)
    is SkinThumbSource.Own -> File(source.dir, SkinFormat.SKIN_JSON).takeIf { it.isFile }
}

private fun read(file: File): SkinPackage? =
    if (file.name == SkinFormat.SKIN_JSON) SkinFormat.readFolder(file.parentFile!!) else SkinFormat.readZip(file)

/** Where the miniature of [key] drawn from [stamp] is kept in the cache folder. */
private fun keptFile(context: Context, key: String, stamp: String): File =
    File(File(context.cacheDir, THUMB_DIR), "${digest(key)}-${digest(stamp)}.png")

/**
 * Keeps [bitmap] as [file], through a temporary file renamed into place (two cards may draw the same skin at once),
 * and drops what was kept for the same skin drawn from something older. A cache: a failure here only costs a redraw.
 */
private fun keep(file: File, bitmap: Bitmap) {
    runCatching {
        val dir = file.parentFile ?: return
        dir.mkdirs()
        val tmp = File.createTempFile("thumb", ".tmp", dir)
        try {
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(file)) return
        } finally {
            tmp.delete()
        }
        val skin = file.name.substringBefore('-')
        dir.listFiles()?.filter { it.name.startsWith("$skin-") && it != file }?.forEach { it.delete() }
    }.onFailure { Log.w(TAG, "miniature not kept", it) }
}

private fun digest(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

/**
 * A skin's miniature in a card: [image] fitted whole on the stage's dark, or, while there is none (it is being drawn,
 * or the skin is not in OpenEmux's format), the small controller in the skin's two colours ([body], [key]).
 */
@Composable
fun SkinThumb(image: ImageBitmap?, body: Color, key: Color, modifier: Modifier = Modifier) {
    if (image == null) {
        MiniSkin(body, key, modifier)
        return
    }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF101014)).border(1.dp, Oe.Border, RoundedCornerShape(10.dp)).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(image, null, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
    }
}
