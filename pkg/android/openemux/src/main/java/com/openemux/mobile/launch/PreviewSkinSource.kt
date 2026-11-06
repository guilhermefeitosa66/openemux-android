package com.openemux.mobile.launch

import android.graphics.Bitmap
import com.openemux.mobile.data.SkinSource
import java.io.InputStream

/**
 * The skin editor's preview of a skin (issue 786): [base] (the skin with its image edits, data/EditedSkinSource) plus
 * [bitmaps], textures being framed in the paint panel, drawn as they are without going through a file.
 */
class PreviewSkinSource(private val base: SkinSource, private val bitmaps: Map<String, Bitmap>) : SkinSource {
    override val files: Set<String> = base.files + bitmaps.keys

    override fun open(path: String): InputStream? = base.open(path)

    /** The texture being framed for [path], or null to read the file. */
    fun bitmap(path: String): Bitmap? = bitmaps[path]
}
