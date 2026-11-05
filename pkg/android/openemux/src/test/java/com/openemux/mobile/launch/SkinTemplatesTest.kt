package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * The templates for skin creators (tools/skins/templates, issue 665) against the bundled packages they come from:
 * one per bundled console, every control where the bundled skin has it and bound to something, every image there at
 * the size the guide gives, each with its editable source.
 */
class SkinTemplatesTest {
    private val skins = File("src/main/assets/skins")
    private val templates = File("tools/skins/templates")

    /** The bundled packages in the console's own colours: console id to file. */
    private fun bundled() = skins.listFiles()!!
        .filter { it.isFile && (it.name.endsWith(".deltaskin") || it.name.endsWith(".manicskin")) }
        .associateBy { it.name.substringBeforeLast('.') }

    private fun info(zip: ZipFile) = JSONObject(zip.getInputStream(zip.getEntry("info.json")).bufferedReader().readText())

    @Test fun everyBundledConsoleHasATemplateAndNothingElseDoes() {
        val made = templates.listFiles()!!.filter { it.name.endsWith(".zip") }.map { it.name.removeSuffix("-template.zip") }
        assertEquals(bundled().keys.sorted(), made.sorted())
        assertTrue(File(templates, "README.md").isFile)
    }

    @Test fun aTemplateHasTheBundledLayoutsControlsAndScreens() {
        for ((id, pkg) in bundled()) {
            ZipFile(File(templates, "$id-template.zip")).use { t ->
                ZipFile(pkg).use { b ->
                    val ti = info(t)
                    val bi = info(b)
                    assertEquals(bi.getString("gameTypeIdentifier"), ti.getString("gameTypeIdentifier"))
                    assertEquals("openemux.template.${id.lowercase()}", ti.getString("identifier"))
                    assertEquals(layouts(bi.getJSONObject("representations")), layouts(ti.getJSONObject("representations")))
                }
            }
        }
    }

    @Test fun everyImageIsThereAtItsSizeWithItsSource() {
        for (id in bundled().keys) {
            val file = File(templates, "$id-template.zip")
            assertTrue("$id template too big for the repo", file.length() < 1_000_000)
            ZipFile(file).use { zip ->
                assertNotNull("$id: README.md", zip.getEntry("README.md"))
                val reps = info(zip).getJSONObject("representations")
                for ((family, scale) in listOf("iphone" to 3, "ipad" to 2)) {
                    val kinds = reps.getJSONObject(family)
                    for (kind in kinds.keys()) for (orient in kinds.getJSONObject(kind).keys()) {
                        val rep = kinds.getJSONObject(kind).getJSONObject(orient)
                        for (r in withLayouts(rep)) {
                            val size = r.getJSONObject("mappingSize")
                            val art = r.getJSONObject("assets").getString("large")
                            assertEquals("$id $art", size.getInt("width") * scale to size.getInt("height") * scale, png(zip, art))
                            val named = listOf(art) + items(r).mapNotNull { it.optJSONObject("thumbstick")?.getString("name") } +
                                items(r).mapNotNull { it.optString("knob").ifEmpty { null } }
                            for (name in named) {
                                assertNotNull("$id: $name", zip.getEntry(name))
                                assertNotNull("$id: source of $name", zip.getEntry("sources/${name.removeSuffix(".png")}.svg"))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun everyControlPressesSomething() {
        for (id in bundled().keys) {
            val console = Console.byId(id)!!
            ZipFile(File(templates, "$id-template.zip")).use { zip ->
                val reps = info(zip).getJSONObject("representations")
                for (family in reps.keys()) for (kind in reps.getJSONObject(family).keys()) {
                    val orients = reps.getJSONObject(family).getJSONObject(kind)
                    for (orient in orients.keys()) for (item in withLayouts(orients.getJSONObject(orient)).flatMap { items(it) }) {
                        val inputs = item.get("inputs")
                        val names = if (inputs is JSONArray) (0 until inputs.length()).map { inputs.getString(it) }
                        else (inputs as JSONObject).let { o -> o.keys().asSequence().map { o.getString(it) }.toList() }
                        for (name in names) {
                            val stick = inputs is JSONObject && (listOf("analogStick", "rightAnalogStick", "cStick").any { name.startsWith(it) })
                            val ok = stick || name == ImportedSkinRenderer.SCREENS_INPUT || SkinBinds.bind(console, name, false) != null
                            assertTrue("$id $family $orient: '$name' presses nothing", ok)
                        }
                    }
                }
            }
        }
    }

    /** [rep] and, for the NDS, each of its screen layouts. */
    private fun withLayouts(rep: JSONObject): List<JSONObject> =
        listOf(rep) + rep.optJSONObject("layouts").let { l -> l?.keys()?.asSequence()?.map { l.getJSONObject(it) }?.toList().orEmpty() }

    private fun items(rep: JSONObject): List<JSONObject> {
        val a = rep.getJSONArray("items")
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    /** What a skin plays like, per representation and layout: mapping size, screens, and each control's inputs and areas. */
    private fun layouts(reps: JSONObject): Map<String, String> {
        val out = sortedMapOf<String, String>()
        fun add(key: String, r: JSONObject) {
            val controls = items(r).map { it ->
                JSONObject().put("inputs", it.get("inputs")).put("frame", it.get("frame"))
                    .put("extendedEdges", it.opt("extendedEdges"))
                    .put("thumbstick", it.optJSONObject("thumbstick")?.let { s -> "${s.opt("width")}x${s.opt("height")}" })
                    .put("knob", it.has("knob")).toString()
            }
            out[key] = listOf(r.get("mappingSize").toString(), r.get("screens").toString(), r.opt("extendedEdges").toString(),
                controls.joinToString("\n")).joinToString("\n")
        }
        for (family in reps.keys()) for (kind in reps.getJSONObject(family).keys()) {
            val orients = reps.getJSONObject(family).getJSONObject(kind)
            for (orient in orients.keys()) {
                val r = orients.getJSONObject(orient)
                add("$family/$kind/$orient", r)
                r.optJSONObject("layouts")?.let { l -> l.keys().forEach { add("$family/$kind/$orient/$it", l.getJSONObject(it)) } }
            }
        }
        return out
    }

    /** A PNG entry's width and height, from its header. */
    private fun png(zip: ZipFile, name: String): Pair<Int, Int> =
        DataInputStream(zip.getInputStream(zip.getEntry(name))).use { s ->
            s.skipBytes(16)
            s.readInt() to s.readInt()
        }
}
