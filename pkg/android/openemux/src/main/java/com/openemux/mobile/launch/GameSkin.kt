package com.openemux.mobile.launch

import android.content.Context
import android.util.Log
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.SkinVariant
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
     * [packaged]: a .deltaskin/.manicskin was used; [ndsLayouts] the saved NDS
     * layouts when the skin has one page per layout (the drawn skin, or a
     * package with the art for each), else null: a package draws the two
     * screens one over the other.
     */
    class Drawn(val file: File, val packaged: Boolean, val ndsLayouts: Pair<ScreenLayout, ScreenLayout>?)

    /**
     * [density] makes one tablet decision for both skins, from the window they are drawn
     * for (a package's iPad art, the drawn tablet shape); [first] is the overlay RetroArch opens on.
     * [fold], the one the window has now, adds the drawn tabletop overlays when it runs across the long side.
     * [gamepad]: a gamepad is connected, the drawn skin opens on its gamepad layout.
     * [variant]: which of the app's packages the console plays with (its own colors or Dark).
     */
    fun render(
        context: Context, base: File, console: Console, size: SkinSize, outDir: File,
        density: Float, hardcore: Boolean, coreId: String?, first: String? = null, fold: Fold? = null,
        gamepad: Boolean = false, variant: SkinVariant = SkinVariant.CONSOLE,
    ): Drawn {
        val tablet = SkinGeometry.tablet(size.short, size.long, density)
        val ndsTabletop = if (console == Console.NDS) NdsLayouts.loadTabletop(context.filesDir) else null
        val ndsLayouts = if (console == Console.NDS) NdsLayouts.load(context.filesDir) else null
        // A package's page per layout only on DeSmuME, the core the activity sets to the page shown: melonDS keeps
        // the screens one over the other, which the package's own art draws.
        val packageNds = ndsLayouts?.takeIf { "desmume" in coreId.orEmpty() }
        val importer = ImportedSkinRenderer()
        val packaged = ImportedSkinRenderer.find(context, base, console, variant)?.let {
            runCatching {
                // The page in [first]'s orientation (and NDS layout, when the package draws one page per layout) opens.
                importer.render(it, console, size.short, size.long, outDir, size.cutout, tablet, hardcore, first, packageNds)
            }.onFailure { e -> Log.e("ImportedSkin", "falling back to the built-in skin", e) }.getOrNull()
        }
        if (packaged != null) {
            val pages = packageNds?.takeIf { importer.wroteNdsLayouts }
            // A package skin has no gamepad layout nor tabletop art: the drawn skin's go after its own.
            val file = runCatching {
                SkinRenderer(context).render(
                    console, size, outDir, pages, hardcore = hardcore, coreId = coreId, density = density,
                    fold = fold, ndsTabletop = ndsTabletop, under = packaged,
                )
            }.onFailure { e -> Log.e("ImportedSkin", "no gamepad or tabletop overlays", e) }.getOrDefault(packaged)
            return Drawn(file, true, pages)
        }
        val file = SkinRenderer(context).render(
            console, size, outDir, ndsLayouts, hardcore, coreId, density, first, fold, ndsTabletop.takeIf { ndsLayouts != null },
            gamepad = gamepad,
        )
        return Drawn(file, false, ndsLayouts)
    }
}
