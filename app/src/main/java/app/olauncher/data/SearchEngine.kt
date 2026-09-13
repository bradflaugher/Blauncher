package app.olauncher.data

import android.net.Uri

/**
 * Where the home-screen search bar sends its text. Every engine but [BROWSER] is a results
 * URL the launcher opens in the default browser, so the page loads on the first tap.
 * [BROWSER] instead hands the raw query to the browser as a web-search intent and lets the
 * browser pick the engine; some browsers only fill their address bar with it and wait for a
 * second enter, which is why it is not the default.
 */
enum class SearchEngine(val displayName: String, val urlTemplate: String?) {
    DIVID3("divid3", "https://divid3.com/?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    KAGI("Kagi", "https://kagi.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q="),
    CHATGPT("ChatGPT", "https://chatgpt.com/?q="),
    BROWSER("Browser default", null);

    /** The results page for [query], or null for [BROWSER], which has no URL of its own. */
    fun searchUrl(query: String): String? = urlTemplate?.let { it + Uri.encode(query) }

    companion object {
        val DEFAULT = DIVID3

        fun fromName(name: String?): SearchEngine =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
