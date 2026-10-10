package app.olauncher.helper

import java.text.Normalizer

/**
 * How a typed query finds apps, shared by the drawer and the home search bar.
 *
 * A label matches when it contains the query, ignoring case, accents and separators (so
 * "gmaps" finds "G Maps" and "cafe" finds "Café"). Matches are ranked by how strongly they
 * match, best first: the whole label, then the start of the label, then the start of one of
 * its words, then anywhere inside it. Equal matches keep the order they came in, which is
 * the drawer's smart order.
 */
object AppSearch {

    /** How a label matched, strongest first. */
    enum class Match { EXACT, PREFIX, WORD_PREFIX, CONTAINS }

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

    /** How [label] matches [query], or null when it does not. */
    fun match(label: String, query: CharSequence): Match? {
        if (query.isBlank()) return null
        val q = normalize(query)
        // A query of separators alone ("+", ".") normalizes away; it can still find "C++".
        if (q.isEmpty()) return if (label.contains(query.trim(), ignoreCase = true)) Match.CONTAINS else null
        val l = normalize(label)
        return when {
            l == q -> Match.EXACT
            l.startsWith(q) -> Match.PREFIX
            label.split(separatorsRegex).any { normalize(it).startsWith(q) } -> Match.WORD_PREFIX
            l.contains(q) -> Match.CONTAINS
            // Anything a plain case-insensitive search finds, normalization must not lose.
            label.contains(query.trim(), ignoreCase = true) -> Match.CONTAINS
            else -> null
        }
    }

    /**
     * The items whose [label] matches [query], best match first, keeping only the first item
     * per [key] so an app listed under several groups is one result.
     */
    fun <T> search(
        items: List<T>,
        query: CharSequence,
        label: (T) -> String,
        key: (T) -> String,
    ): List<Pair<T, Match>> {
        val seen = HashSet<String>()
        return items
            .mapNotNull { item -> match(label(item), query)?.let { item to it } }
            .filter { (item, _) -> seen.add(key(item)) }
            .sortedBy { (_, match) -> match.ordinal }
    }

    private fun normalize(text: CharSequence): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")
            .lowercase()
}
