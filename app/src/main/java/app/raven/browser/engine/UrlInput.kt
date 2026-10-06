package app.raven.browser.engine

import android.net.Uri
import android.util.Patterns
import app.raven.browser.data.SearchEngine

/** Turns what was typed in the address bar into a URL: an address if it looks like one, otherwise a search. */
object UrlInput {
    fun resolve(text: String, engine: SearchEngine): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        val lower = t.lowercase()
        if (Regex("^[a-z][a-z0-9+.-]*://").containsMatchIn(lower) ||
            lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("view-source:") || lower.startsWith("moz-extension:")
        ) return t
        if (!t.contains(' ')) {
            val host = t.substringBefore('/').substringBefore(':')
            val looksLikeHost = host == "localhost" || Patterns.IP_ADDRESS.matcher(host).matches() ||
                (host.contains('.') && !host.startsWith('.') && !host.endsWith('.') && host.substringAfterLast('.').let { it.length >= 2 && it.all { c -> c.isLetter() } })
            if (looksLikeHost) return "https://$t"
        }
        return engine.template.replace("%s", Uri.encode(t))
    }

    /** What the address bar shows for a page: just the site, without https:// and www. */
    fun display(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        val u = Uri.parse(url)
        return when (u.scheme) {
            "http", "https" -> u.host?.removePrefix("www.") ?: url
            "moz-extension" -> "Add-on page"
            else -> url
        }
    }

    /** The search terms, if the page is a results page of the chosen search engine. */
    fun searchTerms(url: String, engine: SearchEngine): String? {
        val prefix = engine.template.substringBefore("%s")
        return if (url.startsWith(prefix)) Uri.decode(url.removePrefix(prefix).substringBefore('&').replace('+', ' ')) else null
    }
}
