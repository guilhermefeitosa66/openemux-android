package com.openemux.mobile.launch

import android.content.Context
import android.util.Log
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.NdsLayouts
import com.openemux.mobile.data.ScreenLayout
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinPackage
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinVariant
import com.openemux.mobile.ui.Fold
import java.io.File

/**
 * Picks and draws a game's skin, the same way at launch and when the game's
 * window changes size: the player's skin picked for the console in
 * files/skins/<console> (a folder in OpenEmux's own format or a package in the
 * iOS emulators' format, issue 782), else the
 * package the app ships for the console, then the drawn skin when there is
 * none or it fails. A user's folder that cannot be drawn for the window's form
 * falls back to the shipped package. A shipped package is in the iOS
 * emulators' format or, for the consoles issue 770 added, in OpenEmux's own
 * (a .zip), drawn like a user's folder.
 */
object GameSkin {
    /**
     * [packaged]: a skin package was used (a .deltaskin/.manicskin, or one in OpenEmux's own format); [ndsLayouts] the saved NDS
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
     * [variant]: which of the app's packages the console plays with (its own colors, Dark or one of its official colors).
     * [custom]: the player's skin picked for the console ([SkinStore.ref]); null, or one no longer there, plays [variant].
     */
    fun render(
        context: Context, base: File, console: Console, size: SkinSize, outDir: File,
        density: Float, hardcore: Boolean, coreId: String?, first: String? = null, fold: Fold? = null,
        gamepad: Boolean = false, variant: SkinVariant = SkinVariant.CONSOLE, custom: String? = null,
    ): Drawn {
        val tablet = SkinGeometry.tablet(size.short, size.long, density)
        val ndsTabletop = if (console == Console.NDS) NdsLayouts.loadTabletop(context.filesDir) else null
        val ndsLayouts = if (console == Console.NDS) NdsLayouts.load(context.filesDir) else null
        // A package's page per layout only on DeSmuME, the core the activity sets to the page shown: melonDS keeps
        // the screens one over the other, which the package's own art draws.
        val packageNds = ndsLayouts?.takeIf { "desmume" in coreId.orEmpty() }
        val importer = ImportedSkinRenderer()
        val user = SkinStore(base).file(console, custom)
        val ownFormat = user != null && ImportedSkinRenderer.isOpenEmuxSkin(user)
        // A skin in OpenEmux's own format ([name]: its folder or zip), or null, logged, when it cannot be drawn here.
        fun ownFormatSkin(name: String, next: String, read: () -> SkinPackage) =
            runCatching { openEmux(context, read(), name, console, size, outDir, tablet, hardcore, first, fold, coreId) }
                .onFailure { e -> Log.e(OWN_TAG, "$name not drawn: falling back to the $next skin", e) }.getOrNull()
        // The user's skin in OpenEmux's own format; one that cannot be drawn here leaves the bundled package next.
        val own = user?.takeIf { ownFormat }?.let { dir -> ownFormatSkin(dir.name, "bundled") { SkinFormat.readFolder(dir) } }
        val bundled = if (own == null && (user == null || ownFormat)) ImportedSkinRenderer.bundled(context, console, variant) else null
        val bundledOwnFormat = bundled != null && ImportedSkinRenderer.isOpenEmuxPackage(bundled)
        // The shipped package in OpenEmux's own format; one that cannot be drawn here leaves the drawn skin next.
        val shipped = bundled?.takeIf { bundledOwnFormat }?.let { zip -> ownFormatSkin(zip.name, "drawn") { SkinFormat.readZip(zip) } }
        val pkg = when {
            own != null -> null
            user != null && !ownFormat -> user
            bundledOwnFormat -> null
            else -> bundled
        }
        val packaged = own ?: shipped ?: pkg?.let {
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

    private const val OWN_TAG = "OpenEmuxSkin"

    /**
     * The skin [pkg] in OpenEmux's own format ([name]: its folder or zip) drawn for the window's form (the [fold]'s inner
     * screen, a [tablet] or a phone), or null, logged, when it has no layout for that form in either orientation.
     */
    private fun openEmux(
        context: Context, pkg: SkinPackage, name: String, console: Console, size: SkinSize, outDir: File, tablet: Boolean,
        hardcore: Boolean, first: String?, fold: Fold?, coreId: String?,
    ): File? {
        return when (val choice = chooseLayouts(pkg.skin, skinForm(tablet, fold))) {
            is LayoutChoice.Missing -> {
                Log.i(OWN_TAG, "$name has no ${choice.layout.id} layout: falling back")
                null
            }
            is LayoutChoice.Use ->
                OpenEmuxSkinRenderer(context).render(pkg, choice, console, size.short, size.long, outDir, size.cutout, tablet, hardcore, first, coreId)
        }
    }
}
