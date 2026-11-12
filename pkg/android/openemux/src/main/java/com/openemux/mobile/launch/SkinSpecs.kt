package com.openemux.mobile.launch

import com.openemux.mobile.data.NDS_LAYOUTS
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.game.available

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
 * one over the other, [PACKAGE_NDS_LAYOUT]. On a console with a [keypad], the gamepad layout's keypad key opens the
 * drawn keypad, as on the drawn skin: its keypad pages' gamepad twins too.
 */
fun packageSpecs(
    gameAspect: Float, nds: Boolean, tabletop: List<OverlaySpec>,
    ndsLayouts: Pair<ScreenLayout, ScreenLayout>? = null, tablet: Boolean = false, keypad: Boolean = false,
): List<OverlaySpec> {
    val aspect = if (nds) PACKAGE_NDS_ASPECT else gameAspect
    val flat = ndsLayouts?.takeIf { nds }?.let { ndsFlatSpecs(it, tablet) }
        ?: (listOf(OverlaySpec("portrait", true, aspect, "landscape"), OverlaySpec("landscape", false, aspect, "portrait")) +
            if (keypad) keypadSpecs(aspect) else emptyList())
    return tabletop + listOf(false, true).flatMap { collapsed -> (flat + tabletop).map { it.twin(true, collapsed) } }
}

/** The keypad's pages, one per orientation, switched to by the keypad key; turning keeps the keypad open. */
private fun keypadSpecs(aspect: Float) = listOf(
    OverlaySpec("keypad-portrait", true, aspect, "keypad-landscape", keypadOpen = true),
    OverlaySpec("keypad-landscape", false, aspect, "keypad-portrait", keypadOpen = true),
)

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
    if (keypad) specs += keypadSpecs(gameAspect)
    if (tabletop) {
        specs += OverlaySpec(TABLETOP, true, gameAspect, "landscape", tabletop = true)
        if (keypad) specs += OverlaySpec("keypad-$TABLETOP", true, gameAspect, "keypad-landscape", keypadOpen = true, tabletop = true)
    }
    return specs
}

/**
 * The tabletop overlays drawn under a package skin (ImportedSkinRenderer), which has no tabletop art of its own: its
 * overlays are a plain "portrait" and "landscape" pair, and on NDS they show the screens [PACKAGE_NDS_LAYOUT]. On a
 * console with a [keypad], the drawn keypad's tabletop page too; turning it switches to the package's keypad page when
 * it has one ([packageKeypad], a skin in OpenEmux's format with keypad pages), else to its landscape page.
 */
fun packageTabletopSpecs(
    gameAspect: Float, nds: Boolean, ndsTabletop: ScreenLayout? = null, ndsLandscape: ScreenLayout? = null, tablet: Boolean = false,
    keypad: Boolean = false, packageKeypad: Boolean = false,
): List<OverlaySpec> =
    if (nds) ndsTabletopSpecs(ndsTabletop, ndsLandscape?.let { "landscape@${it.id}" } ?: "landscape", tablet)
    else listOf(OverlaySpec(TABLETOP, true, gameAspect, "landscape", tabletop = true)) + listOfNotNull(
        OverlaySpec("keypad-$TABLETOP", true, gameAspect, if (packageKeypad) "keypad-landscape" else "landscape", keypadOpen = true, tabletop = true)
            .takeIf { keypad },
    )

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

/**
 * [cfg] with the overlays [a] and [b] trading places, every key of each moving with it: the engine opens a skin on its
 * first overlay, so a package skin opens on its gamepad twin this way when a gamepad layout is wanted (issue 865).
 */
fun swapOverlays(cfg: String, a: Int, b: Int): String {
    if (a == b) return cfg
    return Regex("""^overlay(\d+)_""", RegexOption.MULTILINE).replace(cfg) { m ->
        when (m.groupValues[1].toInt()) {
            a -> "overlay${b}_"
            b -> "overlay${a}_"
            else -> m.value
        }
    }
}

/**
 * A package skin's [cfg] (its own overlays [flat], then the app's: every one in [names]) opening on the gamepad twin of
 * its first page when a [gamepad] layout is wanted, as the drawn skin does: switching to it once the game runs races
 * the engine loading the skin (issue 865).
 */
fun packageOpening(cfg: String, flat: List<String>, names: List<String>, gamepad: Boolean): String {
    val first = flat.firstOrNull()?.takeIf { gamepad } ?: return cfg
    val twin = OverlayName.parse(first)?.let { available(it.withGamepad(true), names) } ?: return cfg
    return swapOverlays(cfg, 0, names.indexOf(twin))
}

/** [cfg] with [added] more overlays, written in [body] from the index after its own. */
fun withOverlays(cfg: String, added: Int, body: String): String {
    val count = Regex("""^overlays = (\d+)$""", RegexOption.MULTILINE)
    val n = count.find(cfg)?.groupValues?.get(1)?.toInt() ?: 0
    return count.replaceFirst(cfg, "overlays = ${n + added}").trimEnd('\n') + "\n" + body
}
