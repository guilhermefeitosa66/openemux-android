package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_LAYOUTS
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.ScreenLayout

/** The tabletop overlay's name ("<name>@<layout>" on NDS): "portrait" in it keeps the engine's auto-rotate reading it as one. */
const val TABLETOP = "portrait-tabletop"

/**
 * One overlay of the skin: [rotate] is the one turning the screen switches to, [layoutNext] the NDS layout key's
 * target, [tabletop] the half-open shape only the activity switches to. [gamepad] is the layout for a connected
 * gamepad (the app controls only), [collapsed] the page with the app controls hidden but for the key that shows them.
 */
class OverlaySpec(
    val name: String, val portrait: Boolean, val aspect: Float, val rotate: String,
    val keypadOpen: Boolean = false, val layoutNext: String? = null, val layout: ScreenLayout? = null,
    val tabletop: Boolean = false, val gamepad: Boolean = false, val collapsed: Boolean = false,
    /** A tablet's skin, whose quick NDS cycle is its own (NdsLayouts.cycle). */
    val tablet: Boolean = false,
) {
    /**
     * The same page in another mode: its name, its rotation and its NDS layout key all stay in that mode, and the
     * layout key steps through that mode's cycle (the gamepad one has side by side in landscape).
     */
    fun twin(gamepad: Boolean, collapsed: Boolean): OverlaySpec {
        fun mode(n: String) = OverlayName.map(n) { it.copy(gamepad = gamepad, collapsed = collapsed) }
        val next = layout?.let { l -> OverlayName.parse(name)?.let { ndsNext(it, l, gamepad, tablet) } }?.let(::mode) ?: layoutNext?.let(::mode)
        return OverlaySpec(mode(name), portrait, aspect, mode(rotate), keypadOpen, next, layout, tabletop, gamepad, collapsed, tablet)
    }
}

/** The page [page]'s NDS layout key steps to, from its [layout], in the [gamepad] or touch cycle. */
private fun ndsNext(page: OverlayName, layout: ScreenLayout, gamepad: Boolean, tablet: Boolean): String =
    page.copy(layout = NdsLayouts.next(page.orientation, layout.id, gamepad, tablet).id).toString()

/**
 * The NDS's flat touch pages, one per layout and orientation, each orientation from its saved layout ([nds]: portrait,
 * landscape) on, the saved portrait one first (RetroArch opens the first): the layout key steps to the next one of
 * the touch cycle, turning switches to the other orientation's saved one. The drawn skin's, and a package skin's with
 * one art per layout (ImportedSkinRenderer).
 */
fun ndsFlatSpecs(nds: Pair<ScreenLayout, ScreenLayout>, tablet: Boolean = false): List<OverlaySpec> {
    val (port, land) = nds
    val specs = mutableListOf<OverlaySpec>()
    for (orient in listOf("portrait", "landscape")) {
        val layouts = NDS_LAYOUTS.getValue(orient)
        val saved = layouts.indexOfFirst { it.id == (if (orient == "portrait") port else land).id }.coerceAtLeast(0)
        val other = if (orient == "portrait") "landscape@${land.id}" else "portrait@${port.id}"
        (layouts.drop(saved) + layouts.take(saved)).forEach { l ->
            val page = OverlayName(portrait = orient == "portrait", layout = l.id)
            specs += OverlaySpec(page.toString(), page.portrait, l.aspect, other, layoutNext = ndsNext(page, l, false, tablet), layout = l, tablet = tablet)
        }
    }
    return specs
}

/**
 * Every page of a drawn skin: its [touch] pages, then their collapsed, gamepad and collapsed gamepad twins, each mode
 * in the touch pages' order. Without [collapsible] (the tabletop pages drawn under a package skin, whose own pages
 * have no hide key) there is no collapsed touch twin.
 */
fun withAppModes(touch: List<OverlaySpec>, collapsible: Boolean = true): List<OverlaySpec> {
    val modes = listOfNotNull(if (collapsible) false to true else null, true to false, true to true)
    return touch + modes.flatMap { (gamepad, collapsed) -> touch.map { it.twin(gamepad, collapsed) } }
}

/**
 * The pages drawn under a package skin (ImportedSkinRenderer): its [tabletop] ones (packageTabletopSpecs, empty
 * without a fold), then the gamepad layout and its collapsed twin for each of its own pages and each tabletop one.
 * Its own pages are a plain "portrait" and "landscape", or, for an NDS package with one art per layout ([ndsLayouts],
 * the saved portrait and landscape ones), [ndsFlatSpecs]' pages; an NDS package without them shows the two screens
 * one over the other, [PACKAGE_NDS_LAYOUT].
 */
fun packageSpecs(
    gameAspect: Float, nds: Boolean, tabletop: List<OverlaySpec>,
    ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null, tablet: Boolean = false,
): List<OverlaySpec> {
    val aspect = if (nds) PACKAGE_NDS_ASPECT else gameAspect
    val flat = ndsLayouts?.takeIf { nds }?.let { ndsFlatSpecs(it, tablet) }
        ?: listOf(OverlaySpec("portrait", true, aspect, "landscape"), OverlaySpec("landscape", false, aspect, "portrait"))
    return tabletop + listOf(false, true).flatMap { collapsed -> (flat + tabletop).map { it.twin(true, collapsed) } }
}

/** The NDS screens one over the other, as a package skin shows them. */
const val PACKAGE_NDS_ASPECT = 256f / 384f

/**
 * The skin's overlays, in the order skin.cfg lists them (RetroArch opens the first). [nds] is the saved portrait and
 * landscape layouts, [ndsTabletop] the saved tabletop one; [tabletop] appends the half-open overlays after the rest,
 * so a skin without a fold is the same as before. No flat overlay ever targets a tabletop one.
 */
fun overlaySpecs(
    gameAspect: Float, keypad: Boolean, nds: Pair<ScreenLayout, ScreenLayout>?,
    tabletop: Boolean = false, ndsTabletop: ScreenLayout? = null, tablet: Boolean = false,
): List<OverlaySpec> {
    val specs = mutableListOf<OverlaySpec>()
    if (nds != null) {
        // One overlay per NDS layout and orientation, and the activity sets the core to match the one shown.
        specs += ndsFlatSpecs(nds, tablet)
        if (tabletop) specs += ndsTabletopSpecs(ndsTabletop, "landscape@${nds.second.id}", tablet)
        return specs
    }
    specs += OverlaySpec("portrait", true, gameAspect, "landscape")
    specs += OverlaySpec("landscape", false, gameAspect, "portrait")
    // The keypad is an overlay of its own per orientation, switched to by the keypad key.
    if (keypad) {
        specs += OverlaySpec("keypad-portrait", true, gameAspect, "keypad-landscape", keypadOpen = true)
        specs += OverlaySpec("keypad-landscape", false, gameAspect, "keypad-portrait", keypadOpen = true)
    }
    if (tabletop) {
        specs += OverlaySpec(TABLETOP, true, gameAspect, "landscape", tabletop = true)
        if (keypad) specs += OverlaySpec("keypad-$TABLETOP", true, gameAspect, "keypad-landscape", keypadOpen = true, tabletop = true)
    }
    return specs
}

/**
 * The tabletop overlays drawn under a package skin (ImportedSkinRenderer), which has no tabletop art of its own: its
 * overlays are a plain "portrait" and "landscape" pair with no keypad, and on NDS they show the screens [PACKAGE_NDS_LAYOUT].
 */
fun packageTabletopSpecs(
    gameAspect: Float, nds: Boolean, ndsTabletop: ScreenLayout? = null, ndsLandscape: ScreenLayout? = null, tablet: Boolean = false,
): List<OverlaySpec> =
    if (nds) ndsTabletopSpecs(ndsTabletop, ndsLandscape?.let { "landscape@${it.id}" } ?: "landscape", tablet)
    else listOf(OverlaySpec(TABLETOP, true, gameAspect, "landscape", tabletop = true))

/** DeSmuME's screen layout under a package skin: its art draws the two screens one over the other. */
const val PACKAGE_NDS_LAYOUT = "top/bottom"

/** NDS's tabletop layouts, from the saved [ndsTabletop] on; turning switches to [rotate]. */
private fun ndsTabletopSpecs(ndsTabletop: ScreenLayout?, rotate: String, tablet: Boolean): List<OverlaySpec> {
    val layouts = NDS_LAYOUTS.getValue(TABLETOP)
    // Nothing saved opens on one per half.
    val saved = layouts.indexOfFirst { it.id == (ndsTabletop ?: NdsLayouts.find(TABLETOP, "halves"))?.id }.coerceAtLeast(0)
    return (layouts.drop(saved) + layouts.take(saved)).map { l ->
        val page = OverlayName(portrait = true, tabletop = true, layout = l.id)
        OverlaySpec(page.toString(), true, l.aspect, rotate, layoutNext = ndsNext(page, l, false, tablet), layout = l, tabletop = true, tablet = tablet)
    }
}

/** The overlays a skin.cfg names, in its order. */
fun overlayNames(cfg: String): List<String> =
    Regex("""^overlay(\d+)_name = "(.*)"$""", RegexOption.MULTILINE).findAll(cfg)
        .map { it.groupValues[1].toInt() to it.groupValues[2] }.sortedBy { it.first }.map { it.second }.toList()

/** [cfg] with [added] more overlays, written in [body] from the index after its own. */
fun withOverlays(cfg: String, added: Int, body: String): String {
    val count = Regex("""^overlays = (\d+)$""", RegexOption.MULTILINE)
    val n = count.find(cfg)?.groupValues?.get(1)?.toInt() ?: 0
    return count.replaceFirst(cfg, "overlays = ${n + added}").trimEnd('\n') + "\n" + body
}
