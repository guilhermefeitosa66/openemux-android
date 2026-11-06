package com.openemux.mobile.launch

import com.retroarch.R
import com.openemux.mobile.data.Console
import com.openemux.mobile.data.KEYPAD_BIND
import com.openemux.mobile.data.SkinCluster
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinFormat
import com.openemux.mobile.data.SkinKey
import com.openemux.mobile.data.SkinLayoutId
import com.openemux.mobile.data.skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * tools/skins/templates/ASSET-PLAN.md, the asset plan of every console the app runs (issue 777): which elements an
 * OpenEmux-format skin for it has, with their binds and image files. The consoles with a template take it from the
 * template; the others from the app's drawn skin ([skin]), with the names that press the same buttons through
 * [SkinBinds]. The file is generated here and compared: a stale plan fails, and `UPDATE_ASSET_PLAN=1` rewrites it.
 */
class SkinAssetPlanTest {
    private val templates = File("tools/skins/templates")
    private val plan = File(templates, "ASSET-PLAN.md")

    /** One element of the plan: its id, type, bind, label and image files. */
    data class Row(val id: String, val type: SkinElementType, val bind: String, val label: String?) {
        val images: List<String> get() = type.imageSuffixes.map { "$id$it.png" }
    }

    /** The quick actions every skin has on its frame (and NDS the screens key), as the templates have them. */
    private fun actions(console: Console) = listOf(
        Row("save", SkinElementType.ACTION, "quickSave", null), Row("load", SkinElementType.ACTION, "quickLoad", null),
        Row("turbo", SkinElementType.ACTION, "turbo", null),
        Row("fast-forward", SkinElementType.ACTION, "toggleFastForward", null),
    ) + if (console == Console.NDS) listOf(Row("screens", SkinElementType.ACTION, "screens", null)) else emptyList()

    /** [retroPad] (the drawn skin's RetroPad bind) as the name a skin uses for it on [console]. */
    private fun name(console: Console, retroPad: String): String =
        SkinBinds.CONSOLE_BINDS[console]?.entries?.firstOrNull { it.value == retroPad }?.key ?: retroPad

    private fun kebab(name: String) = name.replace(Regex("[A-Z]")) { "-" + it.value.lowercase() }

    /** The drawn skin's controls as the plan's elements, in drawing order: shoulders, clusters, keys, keypad. */
    fun drawnRows(console: Console): List<Row> {
        val skin = console.skin
        val rows = actions(console).toMutableList()
        val taken = rows.map { it.id }.toMutableSet()
        fun add(base: String, type: SkinElementType, bind: String, label: String?) {
            var id = base
            var n = 1
            while (id in taken) id = "$base${++n}"
            taken += id
            rows += Row(id, type, bind, label?.takeIf { it.isNotEmpty() })
        }
        fun key(k: SkinKey) {
            val bind = k.bind ?: return
            if (bind == KEYPAD_BIND) return // the drawn skin's key to open its keypad: a skin lays the keypad out itself
            val label = if (k.labelRes == R.string.data_skin_disk) "DISK" else k.label
            add(kebab(name(console, bind)), SkinElementType.BUTTON, name(console, bind), label)
        }
        (skin.shouldersLeft + skin.shouldersRight).forEach(::key)
        add("menu", SkinElementType.BUTTON, "menu", "MENU")
        for (c in skin.clusters) when (c) {
            is SkinCluster.DPad -> if (c.binds == listOf("up", "down", "left", "right")) {
                add("dpad", SkinElementType.DPAD, SkinFormat.DPAD_BIND, null)
            } else {
                // A second pad that presses other buttons (Virtual Boy): four buttons, one per direction.
                listOf("up", "down", "left", "right").zip(c.binds).forEach { (dir, b) ->
                    add("pad-$dir", SkinElementType.BUTTON, name(console, b), null)
                }
            }
            is SkinCluster.Stick ->
                if (c.bind == "analog_right") add("stick2", SkinElementType.STICK, "rightAnalogStick", c.label)
                else add("stick", SkinElementType.STICK, "analogStick", c.label)
            is SkinCluster.Face -> c.buttons.forEach { b ->
                b.bind?.let { add(kebab(name(console, it)), SkinElementType.BUTTON, name(console, it), b.label) }
            }
        }
        skin.pills.forEach(::key)
        // The keypad: 1 to 9 row by row, then the key left of the 0, the 0 and the key right of it.
        skin.keypad.forEachIndexed { r, row ->
            row.forEachIndexed { i, k ->
                val (id, bind) = when {
                    r < 3 -> "key-${r * 3 + i + 1}" to "keypad${r * 3 + i + 1}"
                    i == 0 -> "key-star" to "keypadStar"
                    i == 1 -> "key-0" to "keypad0"
                    else -> "key-hash" to "keypadHash"
                }
                add(id, SkinElementType.KEYPAD, bind, k.label)
            }
        }
        return rows
    }

    /**
     * A template's elements (its phone portrait layout: every layout has the same ones), and its keypad page's when
     * it has one ([keypad]: null when it has none).
     */
    private fun templateRows(console: Console, keypad: Boolean = false): List<Row>? {
        val zip = File(templates, "${console.id}-template.zip").takeIf { it.isFile } ?: return null
        val layout = SkinFormat.readZip(zip).skin.layouts.getValue(SkinLayoutId.PHONE_PORTRAIT)
        if (keypad && layout.keypad == null) return null
        return layout.elements(keypad).map { Row(it.id, it.type, it.bind, it.label) }
    }

    private fun table(out: StringBuilder, rows: List<Row>) {
        out.append("| Element | Type | Bind | Label | Images |\n|---|---|---|---|---|\n")
        for (r in rows) {
            out.append("| `${r.id}` | ${r.type.id} | `${r.bind}` | ${cell(r.label)} | ${r.images.joinToString(", ") { "`$it`" }} |\n")
        }
    }

    private fun cell(s: String?) = s?.replace("|", "\\|") ?: ""

    fun render(): String {
        val out = StringBuilder()
        out.append(
            """
            |# Skin asset plan
            |
            |Every console OpenEmux runs on Android, and what an OpenEmux-format skin for it holds (the format:
            |`docs/skin-format.md`; the guide: [README.md](README.md)). Each layout folder (`phone-portrait/` …
            |`fold-landscape/`) holds `background.png`, `frame.png` and the images below, **every one optional**: a missing
            |image is drawn from the element's colours, shape and label.
            |
            |The consoles with a template take their list from it, and the template's `README.txt` gives every image's
            |pixel size per layout. The others have no layout yet (their bundled skins come in later batches): their
            |list is the controls of the skin the app draws for them, so a creator can plan the art before a template
            |exists. Sizes are free there; keep each image at the size of its element in `skin.json`.
            |
            |Generated by `SkinAssetPlanTest` from the templates and the app's drawn skins: do not edit by hand
            |(`UPDATE_ASSET_PLAN=1 ./gradlew --no-daemon testDebugUnitTest --tests '*SkinAssetPlanTest'` rewrites it).
            |
            """.trimMargin(),
        )
        for (console in Console.entries) {
            val fromTemplate = templateRows(console)
            val rows = fromTemplate ?: drawnRows(console)
            out.append("\n## ${console.id}: ${console.displayName}\n\n")
            out.append(
                if (fromTemplate != null) "Template: [`${console.id}-template.zip`](${console.id}-template.zip).\n\n"
                else "No template yet: the controls of the skin the app draws.\n\n",
            )
            table(out, rows)
            templateRows(console, keypad = true)?.let { keypad ->
                out.append("\nKeypad page, in each layout's `keypad/` folder with a `background.png` of its own (the frame is the layout's):\n\n")
                table(out, keypad)
            }
        }
        return out.toString()
    }

    @Test fun thePlanIsUpToDate() {
        val text = render()
        if (System.getenv("UPDATE_ASSET_PLAN") == "1") plan.writeText(text)
        assertEquals("ASSET-PLAN.md is stale: run with UPDATE_ASSET_PLAN=1", text, plan.readText())
    }

    @Test fun everyConsoleHasASection() {
        val text = plan.readText()
        for (c in Console.entries) assertTrue(c.id, "\n## ${c.id}: ${c.displayName}\n" in text)
    }

    @Test fun aDrawnSkinsPlanPressesWhatTheDrawnSkinPresses() {
        for (console in Console.entries) {
            val rows = drawnRows(console)
            assertEquals(console.id, rows.size, rows.map { it.id }.toSet().size)
            for (r in rows) {
                assertTrue("${console.id} ${r.id} ${r.bind}", r.bind in SkinFormat.bindsFor(r.type))
                assertTrue("${console.id} ${r.id}", Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(r.id))
            }
        }
        // The names that differ from the RetroPad's: the Mega Drive's A is the RetroPad's Y, as SkinBinds reads it.
        val md = drawnRows(Console.MD).first { it.label == "A" }
        assertEquals("y", SkinBinds.bind(Console.MD, md.bind, hardcore = false))
        // The Virtual Boy's right pad as four buttons, the disk key named, the keypad's corner keys.
        assertEquals(listOf("pad-up", "pad-down", "pad-left", "pad-right"),
            drawnRows(Console.VB).filter { it.id.startsWith("pad-") }.map { it.id })
        // The disk key under the app's own name (issue 770), which turns the disk over on L.
        val disk = drawnRows(Console.FDS).first { it.bind == SkinBinds.DISK_INPUT }
        assertEquals("DISK", disk.label)
        assertEquals("l", SkinBinds.bind(Console.FDS, disk.bind, hardcore = false))
        val cv = drawnRows(Console.CV).filter { it.type == SkinElementType.KEYPAD }
        assertEquals(12, cv.size)
        assertEquals(listOf("keypadStar", "keypad0", "keypadHash"), cv.takeLast(3).map { it.bind })
    }
}
