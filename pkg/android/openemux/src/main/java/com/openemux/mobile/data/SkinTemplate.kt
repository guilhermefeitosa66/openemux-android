package com.openemux.mobile.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.floor
import kotlin.math.min

/** What a template image is: the layout's background, the frame, or one of an element's images. */
enum class TemplateImageKind { BACKGROUND, FRAME, BUTTON, KEYPAD, ACTION, DPAD, DPAD_BALL, STICK_BASE, STICK_KNOB }

/**
 * One image of a template: its [file] name, its exact size, and the [element] it draws (if any); [keypadPage] when it
 * belongs to the layout's keypad page (issue 772), whose images are in the layout's `keypad/` folder; [screenPage] the
 * NDS screen-layout page it belongs to (issue 807), whose images are in the layout's folder of that id (`top/`).
 */
data class TemplateImage(
    val file: String,
    val width: Int,
    val height: Int,
    val kind: TemplateImageKind,
    val element: SkinElement? = null,
    val keypadPage: Boolean = false,
    val screenPage: String? = null,
) {
    /** Where it is in the layout's folder: [file], `keypad/<file>` on the keypad page, `<page>/<file>` on a screen-layout page. */
    val path: String get() = when {
        screenPage != null -> "$screenPage/$file"
        keypadPage -> "${SkinKeypadPage.FOLDER}/$file"
        else -> file
    }
}

/**
 * "Download as template" (issue 787): a skin of the player's as a zip a creator can repaint on a computer and import
 * back. It holds the skin's own skin.json, a README.txt with every file and its size, and one PNG per element at its
 * exact pixel size in every layout the skin has (also the ones this device cannot edit), the sizes the templates of
 * issue 777 use (tools/skins/build.py `images()`).
 */
object SkinTemplate {
    /** The D-pad's ball image across, as a share of the pad's shorter side (launch/OverlayLines.kt DPAD_TRAVEL). */
    const val BALL_SHARE = 44.0 / 60.0

    /** A stick's knob image across, as a share of the stick's shorter side. */
    const val KNOB_SHARE = 0.5

    const val README = "README.txt"

    /**
     * Every image [layout] can have, in the order the README lists them: the controls page's, then its keypad page's
     * (its own background, when it names one, and its controls), then each screen-layout page's (its own background,
     * when it names one, its frame and its controls).
     */
    fun images(layout: SkinLayout): List<TemplateImage> = buildList {
        add(TemplateImage(layout.background.imageFile, layout.size.width, layout.size.height, TemplateImageKind.BACKGROUND))
        layout.frame?.let { add(TemplateImage(it.imageFile, px(it.rect.width), px(it.rect.height), TemplateImageKind.FRAME)) }
        layout.elements.forEach { addAll(elementImages(it, keypadPage = false)) }
        layout.keypad?.let { page ->
            page.background?.let {
                add(TemplateImage(it.imageFile, layout.size.width, layout.size.height, TemplateImageKind.BACKGROUND, keypadPage = true))
            }
            page.elements.forEach { addAll(elementImages(it, keypadPage = true)) }
        }
        layout.screenLayouts.forEach { (id, page) ->
            page.background?.let {
                add(TemplateImage(it.imageFile, layout.size.width, layout.size.height, TemplateImageKind.BACKGROUND, screenPage = id))
            }
            page.frame?.let { add(TemplateImage(it.imageFile, px(it.rect.width), px(it.rect.height), TemplateImageKind.FRAME, screenPage = id)) }
            page.elements.forEach { addAll(elementImages(it, keypadPage = false, screenPage = id)) }
        }
    }

    private fun elementImages(e: SkinElement, keypadPage: Boolean, screenPage: String? = null): List<TemplateImage> {
        val w = px(e.rect.width)
        val h = px(e.rect.height)
        val files = e.imageFiles
        fun image(i: Int, iw: Int, ih: Int, kind: TemplateImageKind) = TemplateImage(files[i], iw, ih, kind, e, keypadPage, screenPage)
        return when (e.type) {
            SkinElementType.DPAD -> {
                val side = px(min(w, h) * BALL_SHARE)
                listOf(image(0, w, h, TemplateImageKind.DPAD), image(1, side, side, TemplateImageKind.DPAD_BALL))
            }
            SkinElementType.STICK -> {
                val side = px(min(w, h) * KNOB_SHARE)
                listOf(image(0, w, h, TemplateImageKind.STICK_BASE), image(1, side, side, TemplateImageKind.STICK_KNOB))
            }
            SkinElementType.BUTTON -> listOf(image(0, w, h, TemplateImageKind.BUTTON))
            SkinElementType.KEYPAD -> listOf(image(0, w, h, TemplateImageKind.KEYPAD))
            SkinElementType.ACTION -> listOf(image(0, w, h, TemplateImageKind.ACTION))
        }
    }

    /** The zip's file name: the skin's name in letters and digits. */
    fun fileName(name: String): String = "${slug(name)}.zip"

    /** README.txt: what the zip is, every layout with its files and their sizes, and how to bring it back. */
    fun readme(skin: OpenEmuxSkin): String {
        val lines = mutableListOf(
            "${skin.name}: ${skin.console.displayName} controller skin for OpenEmux (Android)",
            "",
            "A skin in OpenEmux's own format, downloaded as a template. Repaint any image, keep its name and size,",
            "zip the folder again and import it: Controller skins > Import .zip, as a new skin or replacing this one.",
            "",
            "An image you delete falls back to the one in the skin it replaces (or to the colours in skin.json).",
            "Delete a whole layout folder (say, fold-portrait/) and the skin stops offering that screen.",
            "The format: https://github.com/guilhermefeitosa66/openemux-android/blob/openemux-mobile/pkg/android/openemux/docs/skin-format.md",
            "",
        )
        for ((id, layout) in skin.layouts) {
            lines += "${id.id}/  (${layout.size.width} x ${layout.size.height} px)"
            layout.screens.forEach { s ->
                lines += "  game screen at ${num(s.x)}, ${num(s.y)}, ${num(s.width)} x ${num(s.height)} px (left clear: nothing is drawn there)"
            }
            layout.screenLayouts.forEach { (page, p) ->
                p.screens.forEach { s ->
                    lines += "  $page/ game screen at ${num(s.x)}, ${num(s.y)}, ${num(s.width)} x ${num(s.height)} px (its screen-layout page)"
                }
            }
            for (image in images(layout)) {
                val bind = image.element?.let { ", bind ${it.bind}" }.orEmpty()
                lines += "  ${image.path.padEnd(22)} ${image.width.toString().padStart(5)} x ${image.height.toString().padEnd(5)} px  ${what(image.kind)}$bind"
            }
            lines += ""
        }
        return lines.joinToString("\n")
    }

    /**
     * Writes [skin]'s template to [out]: everything under one folder (what unzipping gives back), its skin.json as
     * [skinJson] (the skin's own file, so keys this build does not model survive), README.txt, and each image of
     * each layout as [image] makes it. [out] is left open.
     */
    fun write(out: OutputStream, skin: OpenEmuxSkin, skinJson: String, image: (SkinLayoutId, TemplateImage) -> ByteArray) {
        val root = slug(skin.name) + "/"
        val zip = ZipOutputStream(out)
        fun put(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(root + name))
            zip.write(bytes)
            zip.closeEntry()
        }
        put(SkinFormat.SKIN_JSON, skinJson.toByteArray(Charsets.UTF_8))
        put(README, readme(skin).toByteArray(Charsets.UTF_8))
        for ((id, layout) in skin.layouts) {
            for (img in images(layout)) put("${id.id}/${img.path}", image(id, img))
        }
        zip.finish()
    }

    /** A PNG's width and height from its header, or null when [bytes] does not start like a PNG. */
    fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24 || !PNG.indices.all { bytes[it] == PNG[it] }) return null
        fun int(at: Int) = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (bytes[at + i].toInt() and 0xFF) }
        return int(16) to int(20)
    }

    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

    private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-').ifEmpty { "skin" }

    /** Half up, as build.py rounds: a size of at least one pixel. */
    private fun px(v: Double): Int = floor(v + 0.5).toInt().coerceAtLeast(1)

    private fun px(v: Float): Int = px(v.toDouble())

    private fun num(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

    private fun what(kind: TemplateImageKind) = when (kind) {
        TemplateImageKind.BACKGROUND -> "the whole layout, behind everything"
        TemplateImageKind.FRAME -> "the bezel around the game screen"
        TemplateImageKind.BUTTON -> "a button"
        TemplateImageKind.KEYPAD -> "a keypad key"
        TemplateImageKind.ACTION -> "a quick action on the frame"
        TemplateImageKind.DPAD -> "the D-pad"
        TemplateImageKind.DPAD_BALL -> "the D-pad's ball, moved by the thumb over the pad"
        TemplateImageKind.STICK_BASE -> "the stick's well"
        TemplateImageKind.STICK_KNOB -> "the stick's knob, moved by the thumb"
    }
}
