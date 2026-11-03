package com.openemux.mobile.data

/** One step of the artwork editor, applied in order to the working copy (issue 753). */
sealed interface EditOp {
    data object RotateLeft : EditOp
    data object RotateRight : EditOp
    data object FlipHorizontal : EditOp
    data object FlipVertical : EditOp

    /** Keeps [rect], in fractions of the image as it stands after the steps before this one. */
    data class Crop(val rect: CropRect) : EditOp
}

/** A crop rectangle in fractions (0..1) of the image's width and height. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Whether this keeps the whole image: applying it would change nothing. */
    val whole: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** Moved by [dx], [dy] as a whole, kept inside the image at the same size. */
    fun moved(dx: Float, dy: Float): CropRect {
        val x = (left + dx).coerceIn(0f, 1f - width)
        val y = (top + dy).coerceIn(0f, 1f - height)
        return CropRect(x, y, x + width, y + height)
    }

    /**
     * [corner] dragged by [dx], [dy]: the opposite corner stays put, and the rectangle never gets
     * smaller than [min] on either side nor leaves the image.
     */
    fun resized(corner: Corner, dx: Float, dy: Float, min: Float = MIN_SIDE): CropRect = when (corner) {
        Corner.TOP_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), top = (top + dy).coerceIn(0f, bottom - min))
        Corner.TOP_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), top = (top + dy).coerceIn(0f, bottom - min))
        Corner.BOTTOM_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), bottom = (bottom + dy).coerceIn(top + min, 1f))
        Corner.BOTTOM_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), bottom = (bottom + dy).coerceIn(top + min, 1f))
    }

    /** The rectangle in pixels of a [width] × [height] image, at least one pixel each way. */
    fun pixels(width: Int, height: Int): IntArray {
        val x0 = (left * width).toInt().coerceIn(0, width - 1)
        val y0 = (top * height).toInt().coerceIn(0, height - 1)
        val x1 = (right * width).toInt().coerceIn(x0 + 1, width)
        val y1 = (bottom * height).toInt().coerceIn(y0 + 1, height)
        return intArrayOf(x0, y0, x1 - x0, y1 - y0)
    }

    companion object {
        val WHOLE = CropRect(0f, 0f, 1f, 1f)

        /** The smallest side a crop may have: a handle dragged past it would turn the box inside out. */
        const val MIN_SIDE = 0.08f

        /** How far one D-pad press moves a handle or the box. */
        const val PAD_STEP = 0.02f
    }
}

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * The editor's state: the steps applied to the working copy, and the crop box being drawn.
 * Pure, so the steps and the geometry are tested on the JVM; [ArtImages] draws them.
 */
data class EditSession(
    val ops: List<EditOp> = emptyList(),
    /** The box on screen, not applied yet; null while cropping is off. */
    val crop: CropRect? = null,
) {
    val changed: Boolean get() = ops.isNotEmpty()

    fun rotateLeft() = with(EditOp.RotateLeft)
    fun rotateRight() = with(EditOp.RotateRight)
    fun flipHorizontal() = with(EditOp.FlipHorizontal)
    fun flipVertical() = with(EditOp.FlipVertical)

    /** Shows the crop box over the whole image, or hides it. */
    fun toggleCrop() = copy(crop = if (crop == null) CropRect.WHOLE else null)

    fun moveCrop(rect: CropRect) = copy(crop = rect)

    /** Applies the box; a box over the whole image adds no step. */
    fun applyCrop(): EditSession {
        val rect = crop ?: return this
        return if (rect.whole) copy(crop = null) else copy(ops = ops + EditOp.Crop(rect), crop = null)
    }

    /** Back to the image as it was imported. */
    fun reset() = EditSession()

    // A rotation or a flip turns the box with the image: simpler to drop it than to turn it too.
    private fun with(op: EditOp) = copy(ops = ops + op, crop = null)

    /** The size the steps turn a [width] × [height] image into. */
    fun size(width: Int, height: Int): Pair<Int, Int> = ops.fold(width to height) { (w, h), op ->
        when (op) {
            EditOp.RotateLeft, EditOp.RotateRight -> h to w
            EditOp.FlipHorizontal, EditOp.FlipVertical -> w to h
            is EditOp.Crop -> op.rect.pixels(w, h).let { it[2] to it[3] }
        }
    }
}
