package app.vertune.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.vertune.desktop.data.Settings
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The phone app's own translations: its strings.xml files are copied into
 * desktop/src/main/resources/strings, so the Mac app shows the same words in the same
 * languages. Anything missing in a language falls back to English.
 */
object Strings {
    /** The languages the phone app is translated into, as language tags. */
    val languages = listOf(
        "en", "ca", "de", "es", "et", "fr", "hi-IN", "id", "it", "ja", "nl", "pt-BR", "ru", "sr-Cyrl", "sr-Latn", "tr", "zh"
    )

    class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private val tables = HashMap<String, Table>()

    @Synchronized
    fun table(tag: String): Table = tables.getOrPut(tag) { load(tag) }

    private fun load(tag: String): Table {
        val stream = Strings::class.java.getResourceAsStream("/strings/$tag.xml") ?: return Table(emptyMap(), emptyMap())
        val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val e = stringNodes.item(i) as Element
            strings[e.getAttribute("name")] = unescape(e.textContent)
        }
        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val e = pluralNodes.item(i) as Element
            val items = e.getElementsByTagName("item")
            plurals[e.getAttribute("name")] = (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to unescape(item.textContent)
            }
        }
        return Table(strings, plurals)
    }

    private fun unescape(s: String) = s.trim().removeSurrounding("\"")
        .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\@", "@")

    /** The language in use: the one picked in Settings, or the Mac's own if the phone app has it. */
    fun currentTag(setting: String = Settings.language.value): String {
        if (setting.isNotBlank()) return setting
        val locale = Locale.getDefault()
        val candidates = listOf(locale.toLanguageTag(), "${locale.language}-${locale.country}", locale.language)
        return candidates.firstOrNull { it in languages } ?: when (locale.language) {
            "sr" -> if (locale.script == "Latn") "sr-Latn" else "sr-Cyrl"
            "pt" -> "pt-BR"
            "hi" -> "hi-IN"
            "in" -> "id"
            else -> "en"
        }
    }

    fun get(key: String, tag: String = currentTag()): String? =
        table(tag).strings[key] ?: table("en").strings[key]

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

/** A string from the phone app's translations, in the chosen language. [fallback] if the phone has no such string. */
@Composable
fun str(key: String, fallback: String = key, vararg args: Any?): String {
    val setting by Settings.language.flow.collectAsState()
    val text = remember(key, setting) { Strings.get(key, Strings.currentTag(setting)) } ?: fallback
    return if (args.isEmpty()) text else runCatching { text.format(*args) }.getOrDefault(text)
}

/** A plural string ("1 song" / "4 songs") from the phone app's translations. */
@Composable
fun plural(key: String, count: Int, fallback: String): String {
    val setting by Settings.language.flow.collectAsState()
    val tag = Strings.currentTag(setting)
    val forms = Strings.table(tag).plurals[key] ?: Strings.table("en").plurals[key]
    val text = forms?.get(if (count == 1) "one" else "other") ?: forms?.get("other") ?: fallback
    return runCatching { text.format(count) }.getOrDefault(text)
}
