package com.openemux.mobile.launch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.PathParser
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.ui.Icons
import java.io.File
import java.util.zip.ZipFile

/**
 * Makes a Delta/Manic package (a bundled one, or one the player imported) into a skin of the player's own in OpenEmux's
 * format (issue 797): the bitmap work around [PackageConversion], which decides everything else. Each art is drawn
 * once, every control is lifted off it into an image of its own at its exact size and painted out of the background,
 * and the folder is read back by the store before it is put in place.
 */
object PackageConverter {
    private const val TAG = "PackageConverter"

    /** A new skin of the player's own for [console] named [name], made from the package [file]: its ref. */
    fun convert(store: SkinStore, console: Console, file: File, name: String): String = ZipFile(file).use { zip ->
        val info = SkinStore.packageInfo(zip)
        val plan = PackageConversion.plan(info, console, SkinStore.cleanName(name)) { entry -> size(zip, entry) }
        store.createConverted(console, name) { dir ->
            val drawn = mutableMapOf<String, Pair<File, List<ConvertedElement>>>()
            val results = plan.layouts.mapValues { (id, layout) ->
                val folder = File(dir, id.id)
                val same = drawn[layout.art]
                if (same != null) {
                    // A fold draws the tablet's art: the same files again.
                    SkinStore.copyTree(same.first, folder)
                    same.second
                } else {
                    draw(zip, layout, folder).also { drawn[layout.art] = folder to it }
                }
            }
            PackageConversion.finish(plan, results)
        }
    }

    /** The size of the package's image [entry] when it has one of its own (a raster image), null for a PDF. */
    private fun size(zip: ZipFile, entry: String): Pair<Int, Int>? {
        if (entry.endsWith(".pdf", ignoreCase = true)) return null
        val found = ImportedSkinRenderer.findEntry(zip, entry) ?: throw IllegalArgumentException("no $entry in the package")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(found).use { BitmapFactory.decodeStream(it, null, bounds) }
        return (bounds.outWidth to bounds.outHeight).takeIf { it.first > 0 && it.second > 0 }
    }

    /** Writes [layout]'s images into [folder]: the background and each element's; returns what each element came to. */
    private fun draw(zip: ZipFile, layout: PlannedLayout, folder: File): List<ConvertedElement> {
        folder.mkdirs()
        val w = layout.artW
        val h = layout.artH
        val px = IntArray(w * h)
        ImportedSkinRenderer.rasterize(zip, layout.asset, RectF(0f, 0f, w.toFloat(), h.toFloat())).let { art ->
            // A raster art read at its own size; scaled when it is not the size the plan expects.
            val sized = if (art.width == w && art.height == h) art else Bitmap.createScaledBitmap(art, w, h, true).also { art.recycle() }
            sized.getPixels(px, 0, w, 0, 0, w, h)
            sized.recycle()
        }
        if (layout.translucent) PackageConversion.fade(px)
        val sizes = PackageConversion.sizes(layout.layout)
        // Every control comes off the art before any is painted out, so none is lifted from a hole another left.
        val lifted = layout.elements.map { p -> p.sources.map { (it as? ImageSource.Art)?.let { a -> PackageConversion.lift(px, w, a.frame) } } }
        val results = layout.elements.mapIndexed { i, p ->
            val missing = mutableSetOf<String>()
            p.sources.forEachIndexed { k, source ->
                val file = p.element.imageFiles[k]
                val (iw, ih) = sizes.getValue(file)
                val image = when (source) {
                    is ImageSource.Art -> lifted[i][k]!!.takeIf { it.clean }?.let { bitmap(PackageConversion.place(it, iw, ih, source.dx, source.dy), iw, ih) }
                    is ImageSource.Entry -> entry(zip, source, iw, ih)
                    is ImageSource.SlowMotion -> slowMotion(PackageConversion.lift(px, w, source.model), iw, ih)
                }
                if (image == null) missing += file else save(image, File(folder, file))
            }
            val main = p.sources.firstOrNull() as? ImageSource.Art
            ConvertedElement(missing, main?.takeIf { p.element.imageFiles.first() in missing }?.let { PackageConversion.fill(px, w, it.frame) })
        }
        layout.elements.forEachIndexed { i, p ->
            p.sources.forEachIndexed { k, source ->
                val cut = lifted[i][k]
                if (source is ImageSource.Art && cut != null && cut.clean) PackageConversion.erase(px, w, source.frame, cut)
            }
        }
        val size = layout.layout.size
        val background = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        background.setPixels(px, 0, w, 0, layout.offsetY, w, h)
        save(background, File(folder, layout.layout.background.imageFile))
        return results
    }

    private fun bitmap(px: IntArray, w: Int, h: Int) = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

    /** The package's own image (a circle, a knob) as [source] says, in a [w] x [h] image; null when it cannot be read. */
    private fun entry(zip: ZipFile, source: ImageSource.Entry, w: Int, h: Int): Bitmap? {
        val image = try {
            ImportedSkinRenderer.rasterize(zip, source.entry, RectF(0f, 0f, w / source.crop * source.fill, h / source.crop * source.fill))
        } catch (e: Exception) {
            Log.w(TAG, "${source.entry} not readable: drawn in its colours", e)
            return null
        } ?: return null
        val side = minOf(image.width, image.height) * source.crop
        val from = Rect(
            ((image.width - side) / 2f).toInt(), ((image.height - side) / 2f).toInt(),
            ((image.width + side) / 2f).toInt(), ((image.height + side) / 2f).toInt(),
        )
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val to = RectF(w * (1f - source.fill) / 2f, h * (1f - source.fill) / 2f, w * (1f + source.fill) / 2f, h * (1f + source.fill) / 2f)
        Canvas(out).drawBitmap(image, from, to, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        image.recycle()
        return out
    }

    /** The slow-motion key's glyph, as the package renderer draws it, in the ink of fast forward's ([model]). */
    private fun slowMotion(model: LiftedArt, w: Int, h: Int): Bitmap {
        val glyph = PackageConversion.glyph(model)
        val size = PackageConversion.slowMotionSize(glyph?.second, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val path = PathParser.createPathFromPathData(Icons.SlowMotion)
        path.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(w / 2f - size / 2f, h / 2f - size / 2f) })
        Canvas(out).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glyph?.first ?: 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE; strokeWidth = 1.8f * size / 24f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        })
        return out
    }

    private fun save(bitmap: Bitmap, file: File) {
        try {
            file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw java.io.IOException("could not write ${file.name}") }
        } finally {
            bitmap.recycle()
        }
    }
}
