package com.openemux.mobile.ui

// What each hint-bar badge shows, as plain values so they run as JVM unit tests.
// The hints name positions ("A" is the bottom face button, as Android reports it);
// the face drawn for that position follows the family of the pad in use.

/** The way a controller prints its buttons. Anything unknown is drawn the Xbox way. */
enum class PadFamily { XBOX, NINTENDO, PLAYSTATION }

/** A drawn face: the PlayStation shapes and the Xbox Menu and View icons. */
enum class GlyphKind { CROSS, CIRCLE, SQUARE, TRIANGLE, MENU, VIEW }

/** What a badge shows for one position: text, or a drawn glyph. */
sealed interface Face {
    data class Text(val text: String) : Face
    data class Glyph(val kind: GlyphKind) : Face
}

/** An input device as the labels see it. */
data class PadId(val vendor: Int, val product: Int, val name: String?)

const val VENDOR_SONY = 0x054C
const val VENDOR_NINTENDO = 0x057E
private val DUALSENSE_PRODUCTS = setOf(0x0CE6, 0x0DF2)

/**
 * The family of a pad from its USB vendor id. Some Bluetooth stacks report 0, and
 * then the device name decides; anything else is Xbox style.
 */
fun familyOf(vendorId: Int, productId: Int, name: String?): PadFamily = when (vendorId) {
    VENDOR_SONY -> PadFamily.PLAYSTATION
    VENDOR_NINTENDO -> PadFamily.NINTENDO
    0 -> familyOfName(name)
    else -> PadFamily.XBOX
}

private fun familyOfName(name: String?): PadFamily {
    val n = name?.lowercase() ?: return PadFamily.XBOX
    return when {
        // "Xbox Wireless Controller" holds "wireless controller" too: the brand is read first.
        "xbox" in n -> PadFamily.XBOX
        "dualsense" in n || "dualshock" in n || "wireless controller" in n -> PadFamily.PLAYSTATION
        "pro controller" in n || "joy-con" in n -> PadFamily.NINTENDO
        else -> PadFamily.XBOX
    }
}

/** A DualSense (or DualSense Edge), whose SELECT button is printed CREATE instead of SHARE. */
fun isDualSense(vendorId: Int, productId: Int): Boolean = vendorId == VENDOR_SONY && productId in DUALSENSE_PRODUCTS

/** The faces for a hint [token]; "L1 R1" gives two. A token this does not know is drawn as it is. */
fun faces(token: String, family: PadFamily, dualSense: Boolean): List<Face> = token.split(' ').map { face(it, family, dualSense) }

private fun face(token: String, family: PadFamily, dualSense: Boolean): Face = when (family) {
    PadFamily.XBOX -> when (token) {
        "L1" -> Face.Text("LB")
        "R1" -> Face.Text("RB")
        "START" -> Face.Glyph(GlyphKind.MENU)
        "SELECT" -> Face.Glyph(GlyphKind.VIEW)
        else -> Face.Text(token)
    }
    PadFamily.NINTENDO -> when (token) {
        "A" -> Face.Text("B")
        "B" -> Face.Text("A")
        "X" -> Face.Text("Y")
        "Y" -> Face.Text("X")
        "L1" -> Face.Text("L")
        "R1" -> Face.Text("R")
        "START" -> Face.Text("+")
        "SELECT" -> Face.Text("−")
        else -> Face.Text(token)
    }
    PadFamily.PLAYSTATION -> when (token) {
        "A" -> Face.Glyph(GlyphKind.CROSS)
        "B" -> Face.Glyph(GlyphKind.CIRCLE)
        "X" -> Face.Glyph(GlyphKind.SQUARE)
        "Y" -> Face.Glyph(GlyphKind.TRIANGLE)
        "START" -> Face.Text("OPTIONS")
        "SELECT" -> Face.Text(if (dualSense) "CREATE" else "SHARE")
        else -> Face.Text(token)
    }
}

/** The faces to draw in one badge: text faces joined by a space ("LB RB"), glyphs as they are. */
fun badgeFaces(faces: List<Face>): List<Face> =
    if (faces.all { it is Face.Text }) listOf(Face.Text(faces.joinToString(" ") { (it as Face.Text).text })) else faces

/** The stored values of the "Button labels" setting. */
object ButtonLabelsKeys {
    const val AUTO = "auto"
    const val XBOX = "xbox"
    const val NINTENDO = "nintendo"
    const val PLAYSTATION = "playstation"

    /** A stored value as one of the four; anything else (or nothing) is Automatic. */
    fun parse(stored: String?): String = when (stored) {
        XBOX, NINTENDO, PLAYSTATION -> stored
        else -> AUTO
    }
}

/** The family the hints use: the forced one, or the detected pad's with Automatic. */
fun effectiveFamily(pref: String, detected: PadFamily): PadFamily = when (pref) {
    ButtonLabelsKeys.XBOX -> PadFamily.XBOX
    ButtonLabelsKeys.NINTENDO -> PadFamily.NINTENDO
    ButtonLabelsKeys.PLAYSTATION -> PadFamily.PLAYSTATION
    else -> detected
}

/** Whether SELECT reads CREATE: only a detected DualSense, never a forced PlayStation. */
fun effectiveDualSense(pref: String, dualSense: Boolean): Boolean = pref == ButtonLabelsKeys.AUTO && dualSense

/**
 * Which pad decides the labels: the last real pad that pressed a button, while it is
 * still connected; before any press, the first connected one (lowest device id). A debug
 * override, for the emulator, wins over both.
 */
class PadFamilyTracker {
    private val pads = sortedMapOf<Int, PadId>()
    private var lastPressed: Int? = null

    /** Debug builds: a pad said to be in use, over the devices. */
    var override: PadId? = null

    /** The connected pads now, replacing what was known. */
    fun reset(connected: Map<Int, PadId>) {
        pads.clear()
        pads.putAll(connected)
    }

    fun added(deviceId: Int, pad: PadId) { pads[deviceId] = pad }

    fun changed(deviceId: Int, pad: PadId) { pads[deviceId] = pad }

    fun removed(deviceId: Int) { pads.remove(deviceId) }

    /** A button press from [deviceId]: it is the pad in the person's hands now. */
    fun pressed(deviceId: Int, pad: PadId) {
        pads[deviceId] = pad
        lastPressed = deviceId
    }

    val current: PadId?
        get() = override ?: lastPressed?.let { pads[it] } ?: pads.values.firstOrNull()
}

/** The detected family and DualSense flag of [pad]; no pad is Xbox. */
fun detection(pad: PadId?): Pair<PadFamily, Boolean> =
    if (pad == null) PadFamily.XBOX to false
    else familyOf(pad.vendor, pad.product, pad.name) to isDualSense(pad.vendor, pad.product)
