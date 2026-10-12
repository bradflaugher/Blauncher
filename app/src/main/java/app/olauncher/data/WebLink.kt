package app.olauncher.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A website the user added to the drawer. It is listed, searched, grouped and ordered like an
 * app, and a tap opens [url] in the browser. [label] may be blank, in which case the row shows
 * the site's address ([defaultLabel]). With no [categories] it sits under Other.
 */
data class WebLink(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val url: String,
    val categories: List<AppCategory> = emptyList(),
) {
    /** The name the drawer shows: the user's own, or else the site's address. */
    val displayLabel: String get() = label.trim().ifBlank { defaultLabel(url) }

    /** The groups the link is listed under; never empty. */
    val groups: List<AppCategory> get() = categories.distinct().ifEmpty { listOf(AppCategory.OTHER) }

    companion object {
        /**
         * The web address [input] names, as an http(s) URL, or null when it is not one. A bare
         * domain such as "news.ycombinator.com" gets https:// in front; other schemes
         * (javascript:, file:, intent:) are refused, so a link only ever opens a web page.
         */
        fun normalizeUrl(input: String): String? {
            val text = input.trim()
            if (text.isEmpty() || text.any { it.isWhitespace() }) return null
            val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text) &&
                !Regex("^[^/:]+:\\d+(/|$)").containsMatchIn(text)
            val candidate = if (hasScheme) text else "https://$text"
            val uri = Uri.parse(candidate)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host ?: return null
            // A host needs a dot ("example.com") unless it is a local name like "localhost".
            if (host.isBlank() || host.startsWith('.') || host.endsWith('.')) return null
            if (!host.contains('.') && host != "localhost") return null
            return candidate
        }

        /** The address a link reads as without a name: its host, less a leading "www.". */
        fun defaultLabel(url: String): String = host(url).ifBlank { url }

        /** [url]'s host in lower case, without "www.", or "" when it has none. */
        fun host(url: String): String =
            Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.")

        fun toJson(links: List<WebLink>): String = JSONArray().apply {
            links.forEach { link ->
                put(JSONObject().apply {
                    put("id", link.id)
                    put("label", link.label)
                    put("url", link.url)
                    put("categories", JSONArray(link.categories.map { it.name }))
                })
            }
        }.toString()

        /** Reads what [toJson] wrote; anything unreadable is dropped rather than crashing Home. */
        fun fromJson(json: String?): List<WebLink> {
            if (json.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val categories = item.optJSONArray("categories")?.let { names ->
                    (0 until names.length()).mapNotNull { i ->
                        runCatching { AppCategory.valueOf(names.getString(i)) }.getOrNull()
                    }
                }.orEmpty()
                WebLink(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    label = item.optString("label"),
                    url = url,
                    categories = categories,
                )
            }
        }
    }
}
