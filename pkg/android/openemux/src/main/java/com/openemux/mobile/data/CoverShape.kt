package com.openemux.mobile.data

/**
 * Each console's standard cover shape (width / height), so every cover of a console fills a cell of
 * the same size and the cover grid's rows line up (issue 882). A cover of another shape is cropped
 * from the centre to it.
 *
 * Measured over every cover in the openemux-artwork mirror (the reduced libretro-thumbnails boxart
 * the app syncs): the most common aspect, within ±0.015, of the covers that are not Japanese, or of
 * all of them when a console has fewer than 30 others. Japanese covers stay out of the vote because
 * some consoles' differ in kind (Super Famicom boxes are tall, the SNES box is wide), and so do the
 * GBA's e-Reader cards, which are not game boxes. NES is a call: USA covers peak at 0.70, World and
 * unmarked ones at 0.73.
 */
val COVER_ASPECTS: Map<Console, Float> = mapOf(
    Console.A2600 to 0.73f,
    Console.A5200 to 0.73f,
    Console.A7800 to 0.73f,
    Console.LYNX to 0.81f,
    Console.CV to 0.73f,
    Console.FDS to 0.98f,
    Console.FC to 0.72f,
    Console.GB to 1.00f,
    Console.GBC to 1.00f,
    Console.GBA to 1.00f,
    Console.GG to 0.71f,
    Console.INTV to 0.72f,
    Console.NGP to 0.87f,
    Console.N64 to 1.37f,
    Console.NDS to 1.11f,
    Console.GC to 0.71f,
    Console.O2 to 0.73f,
    Console.SG1000 to 0.73f,
    Console.S32X to 0.72f,
    Console.MCD to 0.71f,
    Console.MD to 0.71f,
    Console.SMS to 0.71f,
    Console.SATURN to 0.65f,
    Console.PS to 1.00f,
    Console.PSP to 0.58f,
    Console.SFC to 1.41f,
    Console.PCE to 1.00f,
    Console.PCECD to 1.00f,
    Console.VECTREX to 0.74f,
    Console.VB to 1.00f,
    Console.WS to 0.73f,
)

/** The shape of a console missing from [COVER_ASPECTS]: the usual tall box. */
const val DEFAULT_COVER_ASPECT = 0.72f

/** The shape every cover of this console is drawn in, in the cover grid. */
val Console.coverAspect: Float get() = coverAspect(this, COVER_ASPECTS)

internal fun coverAspect(console: Console, table: Map<Console, Float>): Float = table[console] ?: DEFAULT_COVER_ASPECT
