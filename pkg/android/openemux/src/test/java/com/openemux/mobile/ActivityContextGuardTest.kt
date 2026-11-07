package com.openemux.mobile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No code casts a context to an activity. Compose's LocalContext is a ContextThemeWrapper inside a
 * sheet or a dialog, so such a cast compiles, works on the screens it was tried on and throws
 * ClassCastException on the others: in 1.21.0, picking a language from the phone's sheet crashed
 * the app. [findActivity] unwraps the context instead (ActivityContextInstrumentedTest proves it on a
 * device, through the language sheet itself).
 */
class ActivityContextGuardTest {
    private val sources = listOf(File("src/main/java"), File("pkg/android/openemux/src/main/java")).first { it.isDirectory }

    // `as Activity`, `as? ComponentActivity`, `as android.app.Activity`...
    private val cast = Regex("""\bas\??\s+[\w.]*Activity\b""")

    private fun isComment(line: String) = line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    @Test fun noContextIsCastToAnActivity() {
        val offenders = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> !isComment(line) && cast.containsMatchIn(line.substringBefore("//")) }
                .map { (i, line) -> "${file.relativeTo(sources)}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue(
            "cast a context with findActivity() instead (a sheet's or dialog's context is not the activity):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun theGuardSeesACast() {
        // The guard is only worth something while its pattern still matches the bug it was written for.
        assertTrue(cast.containsMatchIn("AppLocale.set(context as Activity, tag)"))
        assertTrue(cast.containsMatchIn("(context as? ComponentActivity)?.finish()"))
        assertTrue(cast.containsMatchIn("launch(context as android.app.Activity, rom)"))
        assertTrue(!cast.containsMatchIn("AppLocale.set(context.findActivity(), tag)"))
    }
}
