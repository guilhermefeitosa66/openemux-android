package com.openemux.mobile.data.theme

import java.util.Locale

/**
 * `theme.yaml` without a YAML library (issue 1018): the file is one flat mapping of scalars, which is all the desktop
 * writes (PyYAML's `safe_dump`) and all a theme needs.
 *
 * Read: `key: value` lines at the start of a line; plain, `'single'` and `"double"` quoted scalars (a quoted one may
 * continue on indented lines); YAML 1.1 booleans and nulls, ints and floats; comments, blank lines and the `---` /
 * `...` markers. A key whose value is a nested block or a list (something a newer version might add) comes back as
 * [Nested], which no setting accepts, so it is dropped like any other bad value. Anything else is a [ThemeYamlError].
 */
object ThemeYaml {
    /** The largest file read: a theme is a few hundred bytes. */
    const val MAX_BYTES = 64 * 1024

    /** A value that was a block or a list, not a scalar. */
    object Nested

    private val KEY_LINE = Regex("^([A-Za-z0-9_][A-Za-z0-9_-]*)\\s*:(?:\\s+(.*)|)$")
    private val INT = Regex("[-+]?[0-9]+")
    private val FLOAT = Regex("[-+]?(?:[0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+)(?:[eE][-+]?[0-9]+)?")
    private val TRUE = setOf("true", "True", "TRUE", "yes", "Yes", "YES", "on", "On", "ON")
    private val FALSE = setOf("false", "False", "FALSE", "no", "No", "NO", "off", "Off", "OFF")
    private val NULL = setOf("", "~", "null", "Null", "NULL")

    fun parse(text: String): Map<String, Any?> {
        if (text.length > MAX_BYTES) throw ThemeYamlError("file too large")
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = LinkedHashMap<String, Any?>()
        var i = 0
        var lastKey: String? = null
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            i++
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed == "---" || trimmed == "...") continue
            if (line[0].isWhitespace() || trimmed.startsWith("- ") || trimmed == "-") {
                // Below a key with no value of its own: that key holds a block or a list.
                val key = lastKey ?: throw ThemeYamlError("line $i: indented with no key above")
                val held = out[key]
                // A long plain scalar folded onto the next line goes on with a space; anything else is a block.
                out[key] = if (held is String && !trimmed.startsWith("-")) "$held $trimmed" else Nested
                continue
            }
            val m = KEY_LINE.matchEntire(line.trimEnd()) ?: throw ThemeYamlError("line $i: not a key")
            val key = m.groupValues[1]
            var raw = m.groupValues[2].trim()
            // A quoted scalar may go on over the indented lines below it (folded with a space).
            if ((raw.startsWith("'") || raw.startsWith("\"")) && !closed(raw)) {
                val parts = mutableListOf(raw)
                while (i < lines.size && lines[i].isNotEmpty() && lines[i][0].isWhitespace() && !closed(parts.joinToString(" "))) {
                    parts += lines[i].trim()
                    i++
                }
                raw = parts.joinToString(" ")
            }
            out[key] = scalar(raw, i)
            lastKey = key
        }
        return out
    }

    /** Whether a quoted scalar ends on this text. */
    private fun closed(raw: String): Boolean = try {
        quoted(raw, 0)
        true
    } catch (e: ThemeYamlError) {
        false
    }

    private fun scalar(raw: String, line: Int): Any? {
        if (raw.startsWith("'") || raw.startsWith("\"")) return quoted(raw, line)
        if (raw.startsWith("[") || raw.startsWith("{") || raw.startsWith("|") || raw.startsWith(">") ||
            raw.startsWith("&") || raw.startsWith("*") || raw.startsWith("!")
        ) return Nested
        // A plain scalar ends at " #" (a comment); one that starts with "#" is all comment, as PyYAML reads it.
        if (raw.startsWith("#")) return null
        val plain = raw.substringBefore(" #").trimEnd()
        return when {
            plain in NULL -> null
            plain in TRUE -> true
            plain in FALSE -> false
            INT.matches(plain) -> plain.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: plain.toDouble()
            FLOAT.matches(plain) -> plain.toDouble()
            plain.lowercase(Locale.ROOT) in setOf(".inf", "+.inf") -> Double.POSITIVE_INFINITY
            plain.lowercase(Locale.ROOT) == "-.inf" -> Double.NEGATIVE_INFINITY
            plain.lowercase(Locale.ROOT) == ".nan" -> Double.NaN
            else -> plain
        }
    }

    /** The text of a quoted scalar; what follows its closing quote may only be a comment. */
    private fun quoted(raw: String, line: Int): String {
        val q = raw[0]
        val sb = StringBuilder()
        var i = 1
        while (true) {
            if (i >= raw.length) throw ThemeYamlError("line $line: unclosed quote")
            val c = raw[i]
            if (q == '\'' && c == '\'') {
                if (i + 1 < raw.length && raw[i + 1] == '\'') {
                    sb.append('\''); i += 2; continue
                }
                i++
                break
            }
            if (q == '"' && c == '"') {
                i++
                break
            }
            if (q == '"' && c == '\\') {
                if (i + 1 >= raw.length) throw ThemeYamlError("line $line: bad escape")
                val e = raw[i + 1]
                i += 2
                when (e) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '0' -> sb.append('\u0000')
                    '\\', '"', '/', ' ' -> sb.append(e)
                    'x', 'u', 'U' -> {
                        val n = when (e) { 'x' -> 2; 'u' -> 4; else -> 8 }
                        val hex = raw.substring(i, minOf(raw.length, i + n))
                        val code = hex.takeIf { it.length == n }?.toIntOrNull(16) ?: throw ThemeYamlError("line $line: bad escape")
                        sb.appendCodePoint(code)
                        i += n
                    }
                    else -> throw ThemeYamlError("line $line: bad escape")
                }
                continue
            }
            sb.append(c)
            i++
        }
        val rest = raw.substring(i).trim()
        if (rest.isNotEmpty() && !rest.startsWith("#")) throw ThemeYamlError("line $line: text after a quoted value")
        return sb.toString()
    }

    /** [values] as a file PyYAML reads back to the same mapping, in the order given. */
    fun write(values: Map<String, Any>): String = buildString {
        for ((key, value) in values) {
            append(key).append(": ").append(format(value)).append('\n')
        }
    }

    private fun format(value: Any): String = when (value) {
        is Int -> value.toString()
        is Long -> value.toString()
        is Double -> number(value)
        is Float -> number(value.toDouble())
        is Boolean -> value.toString()
        else -> quote(value.toString())
    }

    /** A number with at most three decimals and always a point, so it reads back as a float. */
    private fun number(value: Double): String {
        val text = String.format(Locale.ROOT, "%.3f", value).trimEnd('0')
        return if (text.endsWith(".")) text + "0" else text
    }

    /** Single quotes always: a string can never be taken for a number, a boolean or a null. */
    private fun quote(text: String): String = "'" + text.replace("'", "''") + "'"
}

class ThemeYamlError(message: String) : Exception(message)
