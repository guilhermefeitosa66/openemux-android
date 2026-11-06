package com.openemux.mobile.launch

import android.content.Context
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinStoreException
import com.openemux.mobile.data.SkinVariant
import java.io.File

/**
 * Makes an editable skin of the player's own, in OpenEmux's format, out of a bundled skin (issue 783): "Create copy
 * and edit" asks here whether a console's bundled skin can be made one, and has it made.
 *
 * The packages that already ship in OpenEmux's format (issue 770) are copied as they are; a console with no package at
 * all plays the drawn skin, which is converted (issue 798, [DrawnSkinCopy]). The Delta/Manic packages come in another
 * part, a [Kind] of their own behind the same two calls.
 */
object SkinConversion {
    /** How a bundled skin is made into the player's own. */
    enum class Kind {
        /** A package already in OpenEmux's format: copied as it is, only its name changed. */
        OPENEMUX,

        /** No package: the console plays the app's drawn skin, converted into OpenEmux's format. */
        DRAWN,
    }

    /**
     * How [console]'s bundled skin in [variant] can be made into the player's own, or null when it cannot yet. The
     * package is the one the game plays: [variant]'s, or the console's own colors' when [variant] ships none for it;
     * with none at all, the drawn skin. [list] lists an assets folder.
     */
    fun kind(list: (String) -> Array<String>?, console: Console, variant: SkinVariant): Kind? {
        val (_, name) = ImportedSkinRenderer.bundledEntry(list, console, variant) ?: return Kind.DRAWN
        return Kind.OPENEMUX.takeIf { ImportedSkinRenderer.isOpenEmuxPackage(File(name)) }
    }

    /** A new skin of the player's own named [name], made from [console]'s bundled skin in [variant]: its ref. */
    fun convert(context: Context, store: SkinStore, console: Console, variant: SkinVariant, name: String): String =
        when (kind({ context.assets.list(it) }, console, variant)) {
            Kind.DRAWN -> DrawnSkinArt(context).let { painter -> convertDrawn(store, console, name, context.cacheDir, painter::paint) }
            else -> convert(store, console, ImportedSkinRenderer.bundled(context, console, variant), name)
        }

    /**
     * A new skin of the player's own named [name], made from [console]'s drawn skin: its ref. The skin.json and the
     * images ([paint] writes each [DrawnSkinCopy.Art] to its file) go in a folder of their own under [cache], which
     * [SkinStore.create] reads back before copying, and which is gone afterwards whatever happens.
     */
    fun convertDrawn(store: SkinStore, console: Console, name: String, cache: File, paint: (DrawnSkinCopy.Art, File) -> Unit): String {
        val copy = DrawnSkinCopy.convert(console, name)
        val dir = File(cache, "drawn-skin-${console.id}-${System.nanoTime()}")
        try {
            if (!dir.mkdirs()) throw SkinStoreException("could not make ${dir.name}")
            for (art in copy.art) {
                val file = File(dir, "${art.layout.id}/${art.file}")
                file.parentFile!!.mkdirs()
                paint(art, file)
            }
            File(dir, SkinFormat.SKIN_JSON).writeText(SkinFormat.toJson(copy.skin))
            return store.create(console, dir, name)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A new skin of the player's own named [name], made from [bundled] (the package copied out of the app): its ref. */
    fun convert(store: SkinStore, console: Console, bundled: File?, name: String): String {
        val file = bundled ?: throw SkinStoreException("${console.id} ships no bundled package")
        if (!ImportedSkinRenderer.isOpenEmuxPackage(file)) throw SkinStoreException("${file.name} cannot be converted yet")
        return store.create(console, file, name)
    }
}
