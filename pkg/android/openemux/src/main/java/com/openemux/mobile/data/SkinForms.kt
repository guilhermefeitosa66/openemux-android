package com.openemux.mobile.data

import kotlin.math.roundToInt

/*
 * The editor's Layouts panel (issue 785): which layouts a form has, and the layout a form switched on gets from the
 * closest one the skin already has. Pure, no Android.
 */
object SkinForms {
    /** A form's nominal upright size, for a layout derived with no layout of that form to take it from. */
    private val UPRIGHT = mapOf(
        SkinForm.PHONE to SkinSize(1080, 2340),
        SkinForm.TABLET to SkinSize(1640, 2360),
        SkinForm.FOLD to SkinSize(1812, 2176),
    )

    /** The other forms from the closest to the farthest: a fold is nearer a tablet than a phone. */
    private val NEAR = mapOf(
        SkinForm.PHONE to listOf(SkinForm.FOLD, SkinForm.TABLET),
        SkinForm.TABLET to listOf(SkinForm.FOLD, SkinForm.PHONE),
        SkinForm.FOLD to listOf(SkinForm.TABLET, SkinForm.PHONE),
    )

    /** [form]'s upright and sideways layouts. */
    fun layoutsOf(form: SkinForm): List<SkinLayoutId> = SkinLayoutId.entries.filter { it.form == form }

    fun id(form: SkinForm, orientation: SkinOrientation): SkinLayoutId =
        SkinLayoutId.entries.first { it.form == form && it.orientation == orientation }

    /**
     * The layout of [have] that [target] is made from: the same orientation (the nearest form first), then [target]'s
     * form turned, then the other orientation of the other forms. Null when [have] holds nothing but [target].
     */
    fun closest(target: SkinLayoutId, have: Collection<SkinLayoutId>): SkinLayoutId? {
        val turned = if (target.orientation == SkinOrientation.PORTRAIT) SkinOrientation.LANDSCAPE else SkinOrientation.PORTRAIT
        val near = NEAR.getValue(target.form)
        val order = near.map { id(it, target.orientation) } + id(target.form, turned) + near.map { id(it, turned) }
        return order.firstOrNull { it in have }
    }

    /** [target]'s size: its form's other layout turned when [layouts] has it, else the form's nominal size. */
    fun size(target: SkinLayoutId, layouts: Map<SkinLayoutId, SkinLayout>): SkinSize {
        val other = layoutsOf(target.form).first { it != target }
        layouts[other]?.size?.let { return SkinSize(it.height, it.width) }
        val up = UPRIGHT.getValue(target.form)
        return if (target.orientation == SkinOrientation.PORTRAIT) up else SkinSize(up.height, up.width)
    }

    /**
     * [source] carried to a layout of [size]: each rect keeps its centre at the same share of the layout and is scaled
     * by the smaller of the two ratios, so round controls stay round; whole units, at least 1, inside the layout.
     * Drawn from its colours: the images of [source] are at its own pixel size, so they are not carried.
     */
    fun derive(source: SkinLayout, size: SkinSize): SkinLayout {
        val sx = size.width / source.size.width.toFloat()
        val sy = size.height / source.size.height.toFloat()
        val k = minOf(sx, sy)
        fun map(r: SkinRect): SkinRect {
            val w = (r.width * k).roundToInt().coerceIn(1, size.width)
            val h = (r.height * k).roundToInt().coerceIn(1, size.height)
            val x = ((r.x + r.width / 2f) * sx - w / 2f).roundToInt().coerceIn(0, size.width - w)
            val y = ((r.y + r.height / 2f) * sy - h / 2f).roundToInt().coerceIn(0, size.height - h)
            return SkinRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        }
        fun element(e: SkinElement) = e.copy(rect = map(e.rect), image = null)
        return SkinLayout(
            size = size,
            screens = source.screens.map(::map),
            background = source.background.copy(image = null),
            frame = source.frame?.let { it.copy(rect = map(it.rect), image = null) },
            elements = source.elements.map(::element),
            keypad = source.keypad?.let { page -> SkinKeypadPage(page.elements.map(::element), page.background?.copy(image = null)) },
        )
    }
}
