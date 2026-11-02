package com.openemux.mobile.launch

import com.openemux.mobile.data.ScreenLayout

/**
 * One page a package skin writes (ImportedSkinRenderer): its [name], whether it is [portrait], the page turning
 * switches to ([rotate]), the NDS [layout] whose art it draws (null: the representation's own) and the page its NDS
 * screens key steps to ([screensNext], null: no such key).
 */
class PackagePage(val name: String, val portrait: Boolean, val rotate: String, val layout: String?, val screensNext: String?)

/**
 * A package skin's own pages, the one RetroArch opens first: a plain "portrait" and "landscape", or, given the saved
 * NDS layouts [nds] (a package with one art per layout), one page per layout and orientation as ndsFlatSpecs names
 * them. [first] is the page shown before a redraw (any mode): the same layout and orientation opens again, else that
 * orientation's saved one; none opens on portrait.
 */
fun packagePages(nds: Pair<ScreenLayout, ScreenLayout>?, tablet: Boolean, first: String?): List<PackagePage> {
    val shown = first?.let(OverlayName::parse)
    if (nds == null) {
        val pages = listOf(PackagePage("portrait", true, "landscape", null, null), PackagePage("landscape", false, "portrait", null, null))
        return if (shown?.portrait == false) pages.reversed() else pages
    }
    val pages = ndsFlatSpecs(nds, tablet).map { PackagePage(it.name, it.portrait, it.rotate, it.layout?.id, it.layoutNext) }
    val names = pages.map { it.name }
    // A tabletop page has no package namesake: "tabletop@side" is not the portrait side-by-side page.
    val opening = shown?.takeIf { !it.tabletop }?.let { s -> OverlayName(portrait = s.portrait, layout = s.layout).toString().takeIf { it in names } }
        ?: if (shown?.portrait == false) "landscape@${nds.second.id}" else null
    return if (opening != null) pages.sortedBy { if (it.name == opening) 0 else 1 } else pages
}

/** The saved NDS layout ([nds]: portrait, landscape) a game starting in [landscape] or portrait opens in. */
fun ndsLaunchLayout(nds: Pair<ScreenLayout, ScreenLayout>, landscape: Boolean): ScreenLayout = if (landscape) nds.second else nds.first
