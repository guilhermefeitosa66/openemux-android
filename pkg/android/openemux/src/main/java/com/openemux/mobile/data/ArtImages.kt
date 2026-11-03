package com.openemux.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** What became of a file picked for import. */
sealed interface PickedArt {
    /** The working copy in the app cache: the picked file itself is never touched again. */
    class Copy(val file: File) : PickedArt
    /** Not a PNG, JPEG or WebP image. */
    data object NotImage : PickedArt
    /** It could not be read at all. */
    data object Unreadable : PickedArt
}

/**
 * The Android half of the artwork editor and search (issue 753): decoding, drawing the editor's
 * steps, PNG encoding and the working copies in the app cache. The steps themselves are
 * [EditSession], tested on the JVM.
 */
object ArtImages {
    private const val TAG = "OpenEmuxArtImages"

    /** The editor works on at most this many pixels a side: plenty for art, and a photo stays small in memory. */
    const val MAX_SIDE = 2048

    /** The largest file the editor imports: far above any cover or label, well below what a phone can hold twice. */
    const val MAX_IMPORT_BYTES = 40 * 1024 * 1024

    fun scratch(context: Context) = File(context.cacheDir, "artwork/edit")

    fun clearScratch(context: Context) {
        scratch(context).deleteRecursively()
    }

    /** Whether the platform can decode [bytes] (search results whose header looks right but are not). */
    fun decodable(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    /** Copies the picked [uri] into the cache, when it is an image the editor takes. */
    fun importUri(context: Context, uri: Uri): PickedArt {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it) } ?: return PickedArt.Unreadable
        } catch (e: IOException) {
            Log.w(TAG, "picked file unreadable: ${e.javaClass.simpleName}")
            return PickedArt.Unreadable
        } catch (e: SecurityException) {
            Log.w(TAG, "picked file not readable: ${e.javaClass.simpleName}")
            return PickedArt.Unreadable
        }
        val format = ArtImageCheck.format(bytes)
        if (format == null || format !in ArtImageCheck.IMPORTABLE) return PickedArt.NotImage
        if (!decodable(bytes)) return PickedArt.Unreadable
        return copy(context, bytes, format)
    }

    /**
     * The whole stream, or null past [MAX_IMPORT_BYTES]: a huge file picked by mistake must not
     * take the app down with an OutOfMemoryError while it is read.
     */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > MAX_IMPORT_BYTES) {
                Log.w(TAG, "picked file too large")
                return null
            }
            out.write(buf, 0, n)
        }
    }

    /** A working copy of art already on the device (Adjust). */
    fun copyOf(context: Context, source: File): PickedArt {
        val bytes = try { source.readBytes() } catch (_: IOException) { return PickedArt.Unreadable }
        val format = ArtImageCheck.format(bytes) ?: return PickedArt.NotImage
        return copy(context, bytes, format)
    }

    private fun copy(context: Context, bytes: ByteArray, format: String): PickedArt {
        val dir = scratch(context).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.$format")
        return try {
            file.writeBytes(bytes)
            PickedArt.Copy(file)
        } catch (e: IOException) {
            Log.w(TAG, "working copy not written: ${e.javaClass.simpleName}")
            PickedArt.Unreadable
        }
    }

    /** Decodes [file] upright (a photo's EXIF orientation applied where the platform can) and no larger than [MAX_SIDE]. */
    fun decode(file: File): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = maxOf(info.size.width, info.size.height)
                if (side > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / side
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: IOException) {
        Log.w(TAG, "image not decoded: ${e.javaClass.simpleName}")
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "image too large to decode")
        null
    }

    /** [source] with the editor's [ops] applied, in order. */
    fun render(source: Bitmap, ops: List<EditOp>): Bitmap = ops.fold(source) { bitmap, op ->
        when (op) {
            is EditOp.Crop -> {
                val (x, y, w, h) = op.rect.pixels(bitmap.width, bitmap.height).toList()
                Bitmap.createBitmap(bitmap, x, y, w, h)
            }
            else -> {
                val matrix = Matrix().apply {
                    when (op) {
                        EditOp.RotateLeft -> postRotate(-90f)
                        EditOp.RotateRight -> postRotate(90f)
                        EditOp.FlipHorizontal -> postScale(-1f, 1f)
                        EditOp.FlipVertical -> postScale(1f, -1f)
                        is EditOp.Crop -> Unit
                    }
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        }
    }

    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    /** [bytes] as PNG: unchanged when they already are, re-encoded otherwise (null when they cannot be decoded). */
    fun ensurePng(bytes: ByteArray): ByteArray? {
        if (ArtImageCheck.format(bytes) == "png") return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return png(bitmap)
    }
}
