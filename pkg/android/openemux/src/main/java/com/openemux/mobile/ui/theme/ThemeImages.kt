package com.openemux.mobile.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.openemux.mobile.data.ArtImages
import com.openemux.mobile.data.theme.ThemeBlur
import com.openemux.mobile.data.theme.ThemeFit
import com.openemux.mobile.data.theme.ThemeImage
import com.openemux.mobile.data.theme.ThemeImageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Pictures picked for a theme become its own JPEG copy, no larger than [ArtImages.MAX_SIDE] (issue 1018). */
object AndroidThemeCodec : ThemeImageCodec {
    override fun reencode(input: File, output: File): Boolean {
        if (!input.isFile || input.length() > ArtImages.MAX_IMPORT_BYTES) return false
        val bitmap = ArtImages.decode(input) ?: return false
        return try {
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } finally {
            bitmap.recycle()
        }
    }
}

/** Decoded and blurred pictures, by file, its date and the blur: made once, off the main thread. */
private val prepared = LruCache<String, ImageBitmap>(4)

private fun prepare(file: File, blur: Float): ImageBitmap? {
    val key = "${file.path}|${file.lastModified()}|$blur"
    prepared.get(key)?.let { return it }
    val decoded = ArtImages.decode(file) ?: return null
    val bitmap = if (blur <= 0f) decoded else {
        val side = maxOf(decoded.width, decoded.height)
        val scale = minOf(1f, ThemeBlur.WORK_SIDE.toFloat() / side)
        val small = Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), true)
        if (small !== decoded) decoded.recycle()
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        // The blur is in dp of the screen; on a phone the picture spans about 400 dp across.
        val radius = ThemeBlur.workRadius(blur, 1f, maxOf(small.width, small.height), 400f)
        val out = Bitmap.createBitmap(ThemeBlur.blur(pixels, small.width, small.height, radius), small.width, small.height, Bitmap.Config.ARGB_8888)
        small.recycle()
        out
    }
    return bitmap.asImageBitmap().also { prepared.put(key, it) }
}

/**
 * [image] of the theme in [folder] behind [content], dimmed toward [dimTo] so text on it stays readable. Nothing when
 * the theme has no picture there or it cannot be read.
 */
@Composable
fun ThemeBackdrop(image: ThemeImage?, folder: File?, dimTo: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val file = remember(image?.path, folder) { if (image != null && folder != null) File(folder, image.path) else null }
    val bitmap by produceState<ImageBitmap?>(null, file, image?.blur) {
        value = file?.let { f -> withContext(Dispatchers.IO) { runCatching { prepare(f, image!!.blur) }.getOrNull() } }
    }
    Box(modifier) {
        val b = bitmap
        if (image != null && b != null) {
            when (image.fit) {
                ThemeFit.TILE -> {
                    val brush = remember(b) { ShaderBrush(ImageShader(b, TileMode.Repeated, TileMode.Repeated)) }
                    Box(Modifier.fillMaxSize().drawBehind { drawRect(brush) })
                }
                else -> Image(
                    b, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = if (image.fit == ThemeFit.CONTAIN) ContentScale.Fit else ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().drawBehind { drawRect(dimTo.copy(alpha = image.dim)) })
        }
        content()
    }
}
