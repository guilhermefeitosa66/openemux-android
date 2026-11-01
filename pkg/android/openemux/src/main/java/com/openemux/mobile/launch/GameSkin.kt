package com.openemux.mobile.launch

import android.content.Context
import android.util.Log
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.ui.Fold
import java.io.File

/**
 * Picks and draws a game's skin, the same way at launch and when the game's
 * window changes size: a skin in the iOS emulators' format (the user's own
 * in files/skins/<console>, else the one the app ships for the console)
 * first, the drawn skin when there is none or it fails.
 */
object GameSkin {
    /**
     * [packaged]: a .deltaskin/.manicskin was used, which draws the two NDS
     * screens one over the other; [ndsLayouts] the drawn skin's NDS layouts.
     */
    class Drawn(val file: File, val packaged: Boolean, val ndsLayouts: Pair<ScreenLayout, ScreenLayout>?)

    /**
     * [density] makes one tablet decision for both skins, from the window they are drawn
     * for (a package's iPad art, the drawn tablet shape); [first] is the overlay RetroArch opens on.
     * [fold], the one the window has now, adds the drawn tabletop overlays when it runs across the long side.
     * [gamepad]: a gamepad is connected, the drawn skin opens on its gamepad layout.
     */
    fun render(
        context: Context, base: File, console: Console, size: SkinSize, outDir: File,
        density: Float, hardcore: Boolean, coreId: String?, first: String? = null, fold: Fold? = null,
        gamepad: Boolean = false,
    ): Drawn {
        val tablet = SkinGeometry.tablet(size.short, size.long, density)
        val ndsTabletop = if (console == Console.NDS) NdsLayouts.loadTabletop(context.filesDir) else null
        val packaged = ImportedSkinRenderer.find(context, base, console)?.let {
            runCatching {
                // Its own pages are a plain "portrait" and "landscape": the one in [first]'s orientation opens.
                val own = first?.let { f -> OverlayName.map(f) { n -> OverlayName(n.portrait) } }
                ImportedSkinRenderer().render(it, console, size.short, size.long, outDir, size.cutout, tablet, hardcore, own)
            }.onFailure { e -> Log.e("ImportedSkin", "falling back to the built-in skin", e) }.getOrNull()
        }
        if (packaged != null) {
            // A package skin has no gamepad layout nor tabletop art: the drawn skin's go after its own.
            val file = runCatching {
                SkinRenderer(context).render(
                    console, size, outDir, hardcore = hardcore, coreId = coreId, density = density,
                    fold = fold, ndsTabletop = ndsTabletop, under = packaged,
                )
            }.onFailure { e -> Log.e("ImportedSkin", "no gamepad or tabletop overlays", e) }.getOrDefault(packaged)
            return Drawn(file, true, null)
        }
        // The NDS layouts belong to the drawn skin.
        val ndsLayouts = if (console == Console.NDS) NdsLayouts.load(context.filesDir) else null
        val file = SkinRenderer(context).render(
            console, size, outDir, ndsLayouts, hardcore, coreId, density, first, fold, ndsTabletop.takeIf { ndsLayouts != null },
            gamepad = gamepad,
        )
        return Drawn(file, false, ndsLayouts)
    }
}
