package com.openemux.mobile.launch

import android.content.Context
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinStore
import com.openemux.mobile.data.SkinStoreException
import com.openemux.mobile.data.SkinVariant
import java.io.File

/**
 * Makes an editable skin of the player's own, in OpenEmux's format, out of a bundled skin (issue 783): "Create copy
 * and edit" asks here whether a console's bundled skin can be made one, and has it made.
 *
 * Today only the packages that already ship in OpenEmux's format (issue 770) can: a straight copy. The Delta/Manic
 * packages and the drawn skin come in later parts, each a [Kind] of its own behind the same two calls.
 */
object SkinConversion {
    /** How a bundled skin is made into the player's own. */
    enum class Kind {
        /** A package already in OpenEmux's format: copied as it is, only its name changed. */
        OPENEMUX,
    }

    /**
     * How [console]'s bundled skin in [variant] can be made into the player's own, or null when it cannot yet. The
     * package is the one the game plays: [variant]'s, or the console's own colors' when [variant] ships none for it.
     * [list] lists an assets folder.
     */
    fun kind(list: (String) -> Array<String>?, console: Console, variant: SkinVariant): Kind? {
        val name = listOf(variant, SkinVariant.CONSOLE).firstNotNullOfOrNull { ImportedSkinRenderer.bundledName(list(it.assetDir), console) }
            ?: return null
        return Kind.OPENEMUX.takeIf { ImportedSkinRenderer.isOpenEmuxPackage(File(name)) }
    }

    /** A new skin of the player's own named [name], made from [console]'s bundled skin in [variant]: its ref. */
    fun convert(context: Context, store: SkinStore, console: Console, variant: SkinVariant, name: String): String =
        convert(store, console, ImportedSkinRenderer.bundled(context, console, variant), name)

    /** A new skin of the player's own named [name], made from [bundled] (the package copied out of the app): its ref. */
    fun convert(store: SkinStore, console: Console, bundled: File?, name: String): String {
        val file = bundled ?: throw SkinStoreException("${console.id} ships no bundled package")
        if (!ImportedSkinRenderer.isOpenEmuxPackage(file)) throw SkinStoreException("${file.name} cannot be converted yet")
        return store.create(console, file, name)
    }
}
