package com.drmd.lj2pdf

import android.net.Uri
import kotlinx.coroutines.delay
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Facebook platform module.
 *
 * Facebook has no open archive API for a wall, so this module talks to it the
 * way a browser does: it reuses the session cookies obtained by logging in
 * through a real [FacebookLoginActivity] WebView (see [BrowserSession]) and
 * reads the **mbasic** front-end — `mbasic.facebook.com`, the no-JavaScript
 * HTML version Facebook still serves. mbasic pages are plain markup with real
 * `<a href>` links, so the existing HTTP + jsoup pipeline enumerates and saves
 * them exactly like a LiveJournal archive.
 *
 * Enumeration follows mbasic's own "see more" links rather than guessing at
 * cursor formats: any link the page itself offers as a continuation is taken,
 * newest first, until the feed runs out.
 *
 * Facebook throttles hard, so scanning is sequential with a jittered pause and
 * post downloads are capped at [MAX_PARALLEL] connections.
 */
object Facebook {

    const val HOST = "mbasic.facebook.com"

    /** Connections to Facebook are capped — it rate-limits aggressive clients. */
    const val MAX_PARALLEL = 2

    private const val PAUSE_MS = 800L         // base pause between feed pages
    private const val PAUSE_JITTER_MS = 700L
    private const val MAX_FEED_PAGES = 400

    private val FB_HOSTS = listOf(
        "facebook.com", "fb.com", "fb.watch", "facebook.net"
    )

    /** Query parameters worth keeping; everything else is tracking noise. */
    private val KEEP_PARAMS = setOf("story_fbid", "id", "fbid", "v")

    private val postPathRe = Regex(
        "/(?:posts|permalink|videos|notes|photos|reel)/([0-9A-Za-z._-]+)"
    )
    private val groupPostRe = Regex("/groups/([0-9A-Za-z._-]+)/(?:permalink|posts)/(\\d+)")

    // ---- identification ------------------------------------------------

    fun isFacebook(url: String): Boolean {
        val host = (Uri.parse(url).host ?: "").lowercase()
        return FB_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * A stable, filesystem-safe id for a post URL. Facebook reaches the same
     * story through several shapes (`story.php`, `/posts/`, `/permalink.php`),
     * so the numeric story id is what identifies it — that is what makes an
     * incremental update recognise an already-archived post.
     */
    fun idOf(url: String): String {
        val u = Uri.parse(url)
        val story = u.getQueryParameter("story_fbid") ?: u.getQueryParameter("fbid")
        if (!story.isNullOrBlank()) return "fb$story"
        val path = u.path ?: ""
        groupPostRe.find(path)?.let { return "fb${it.groupValues[2]}" }
        postPathRe.find(path)?.let { return "fb${it.groupValues[1]}" }
        return "fb" + Integer.toHexString(url.substringBefore('#').hashCode() and 0x7fffffff)
    }

    /** True for a URL that names one story rather than a feed. */
    fun isPost(url: String): Boolean {
        val u = Uri.parse(url)
        val path = u.path ?: ""
        return u.getQueryParameter("story_fbid") != null ||
            u.getQueryParameter("fbid") != null ||
            groupPostRe.containsMatchIn(path) ||
            postPathRe.containsMatchIn(path)
    }

    // ---- URL normalisation ---------------------------------------------

    /** Rewrite any Facebook URL onto mbasic, dropping tracking parameters. */
    fun toMbasic(url: String): String {
        val u = Uri.parse(url)
        val b = Uri.Builder().scheme("https").authority(HOST)
            .path(u.path?.ifBlank { "/" } ?: "/")
        for (name in u.queryParameterNames) {
            if (name in KEEP_PARAMS) u.getQueryParameter(name)?.let { b.appendQueryParameter(name, it) }
        }
        return b.build().toString()
    }

    /**
     * The feed URL to start scanning from: a profile / page timeline, a group
     * feed, or the post itself when a permalink was pasted.
     */
    fun feedUrl(base: String): String {
        val u = Uri.parse(base)
        val path = (u.path ?: "/").trimEnd('/').ifBlank { "/" }
        if (isPost(base)) return toMbasic(base)
        // profile.php?id=… keeps its id; a group keeps /groups/<id>; a vanity
        // name keeps its path. Timelines list stories oldest-section-last, so
        // v=timeline is what mbasic paginates through.
        val id = u.getQueryParameter("id")
        return when {
            path.startsWith("/groups/") ->
                "https://$HOST${path.split('/').take(3).joinToString("/")}"
            !id.isNullOrBlank() ->
                "https://$HOST/profile.php?id=$id&v=timeline"
            else ->
                "https://$HOST$path?v=timeline"
        }
    }

    /** Short, filesystem-safe project name for a Facebook target. */
    fun targetName(base: String): String {
        val u = Uri.parse(base)
        val path = (u.path ?: "").trim('/')
        val id = u.getQueryParameter("id")
        val who = when {
            path.startsWith("groups/") -> "group_" + path.removePrefix("groups/").substringBefore('/')
            !id.isNullOrBlank() -> "id_$id"
            path.isNotBlank() -> path.substringBefore('/')
            else -> "feed"
        }
        return "facebook.com_" + who.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    // ---- session -------------------------------------------------------

    /**
     * A page served to a signed-out client: mbasic answers with the login form
     * (or a checkpoint) instead of the feed. Worth naming explicitly — the
     * symptom is otherwise just "0 posts found".
     */
    fun looksLoggedOut(doc: Document): Boolean {
        if (doc.selectFirst("input[name=pass]") != null) return true
        if (doc.selectFirst("form[action*=/login/]") != null) return true
        val url = doc.location() ?: ""
        return url.contains("/login") || url.contains("/checkpoint")
    }

    // ---- enumeration ---------------------------------------------------

    /** Every story on the wall/group, newest first (as mbasic orders them). */
    suspend fun scanAll(base: String, max: Int, note: (String) -> Unit): List<String> =
        scan(base, max, emptySet(), note)

    /**
     * Only the stories published since the last run: walks the top of the feed
     * and stops at the first already-archived post (same contract as the
     * LiveJournal updater).
     */
    suspend fun scanNew(
        base: String, knownIds: Set<String>, max: Int, note: (String) -> Unit
    ): List<String> = scan(base, max, knownIds, note)

    private suspend fun scan(
        base: String, max: Int, stopAtIds: Set<String>, note: (String) -> Unit
    ): List<String> {
        if (isPost(base)) return listOf(toMbasic(base))

        val out = ArrayList<String>()
        val seenPosts = HashSet<String>()
        val seenPages = HashSet<String>()
        var url: String? = feedUrl(base)
        var page = 0
        var warnedAuth = false

        while (url != null && page < MAX_FEED_PAGES && out.size < max && !ConvertBus.cancelRequested) {
            if (!seenPages.add(url)) break
            page++
            note("Facebook: page $page (${out.size} posts)…")
            val doc = Http.doc(url)
            if (doc == null) {
                ConvertBus.log("[fb] page $page failed — stopping")
                break
            }
            if (looksLoggedOut(doc)) {
                if (!warnedAuth) {
                    ConvertBus.log("[fb] not signed in — open «FB → Войти» and log in as a browser")
                    warnedAuth = true
                }
                break
            }

            var hitKnown = false
            for (link in storyLinks(doc)) {
                val id = idOf(link)
                if (id in stopAtIds) { hitKnown = true; break }
                if (seenPosts.add(id)) out.add(link)
                if (out.size >= max) break
            }
            ConvertBus.scanProgress(page, out.size)
            if (hitKnown) {
                ConvertBus.log("[fb] reached an already-archived post — stopping")
                break
            }
            url = nextPage(doc, seenPages)
            if (url != null) delay(PAUSE_MS + (0..PAUSE_JITTER_MS).random())
        }

        ConvertBus.log("[fb] ${out.size} post(s) over $page page(s)")
        return out
    }

    /** Canonical story permalinks linked from a feed page, in page order. */
    private fun storyLinks(doc: Document): List<String> {
        val out = LinkedHashSet<String>()
        for (a in doc.select("a[href]")) {
            val raw = a.absUrl("href")
            if (raw.isBlank() || !isFacebook(raw)) continue
            val clean = unwrap(raw)
            if (!isPost(clean)) continue
            if (isReaction(clean)) continue
            out.add(toMbasic(clean))
        }
        return out.toList()
    }

    /**
     * The link mbasic itself offers to continue the feed ("See more stories",
     * "Показать больше"). Cursor formats differ per surface and change over
     * time, so the continuation is recognised by the markers Facebook has kept
     * stable rather than reconstructed by hand.
     */
    private fun nextPage(doc: Document, visited: Set<String>): String? {
        val markers = listOf(
            "sectionLoadingID", "unitcursor", "cursor=", "timeend", "bacr=",
            "/pages_reaction_units/", "bac=", "pageNumber="
        )
        val candidates = LinkedHashSet<String>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (href.isBlank() || !isFacebook(href)) continue
            val text = a.text().trim().lowercase()
            val marked = markers.any { href.contains(it, ignoreCase = true) }
            val labelled = text.isNotEmpty() && MORE_LABELS.any { text.contains(it) }
            if (marked || labelled) candidates.add(toMbasicKeepingCursor(href))
        }
        return candidates.firstOrNull { it !in visited }
    }

    private val MORE_LABELS = listOf(
        "see more", "show more", "more stories", "older",
        "показать", "ещё", "еще", "далее", "старые"
    )

    /**
     * Continuation links carry opaque cursor parameters that must survive
     * intact — unlike [toMbasic], which keeps only the identifying ones.
     */
    private fun toMbasicKeepingCursor(url: String): String {
        val u = Uri.parse(unwrap(url))
        return u.buildUpon().scheme("https").authority(HOST).build().toString()
    }

    /**
     * Facebook wraps outbound and some internal links in `/l.php?u=…` or
     * `…?next=…`; take the real target so the same story is not archived twice
     * under two different URLs.
     */
    fun unwrap(url: String): String {
        val u = Uri.parse(url)
        val path = u.path ?: ""
        if (path == "/l.php" || path.endsWith("/l.php")) {
            u.getQueryParameter("u")?.let { return Uri.decode(it) }
        }
        return url
    }

    /** Like / comment / share actions look like post links but are not posts. */
    private fun isReaction(url: String): Boolean {
        val low = url.lowercase()
        return low.contains("/ufi/reaction") || low.contains("/reactions/") ||
            low.contains("/share/") || low.contains("/composer/") ||
            low.contains("/comment/replies") || low.contains("action=")
    }

    // ---- article extraction --------------------------------------------

    /**
     * mbasic wraps a story in `#m_story_permalink_view`; a feed item is a
     * `div[data-ft]` block. Tried before the generic blog selectors.
     */
    val CONTENT_SELECTORS = listOf(
        "#m_story_permalink_view", "div[role=article]", "div[data-ft]", "#root"
    )

    /** Chrome that is not part of the story: nav bars, action rows, composer. */
    const val STRIP =
        "#header,#mbasic_inline_feed_composer,#MComposer,.mbasic_ads," +
            "[data-sigil=comment-composer],[id^=see_next_pagelet]," +
            "form[action*=/a/comment],form[action*=/reactions/]," +
            "a[href*=/login/],a[href*=/reg/],#footer"

    /**
     * A story has no headline, so the chapter title is built the way a reader
     * would name it: who posted, when, and the opening words of the text.
     */
    fun titleOf(doc: Document, content: Element, id: String): String {
        val who = doc.selectFirst("h3 a, header h3 a, strong a")?.text()?.trim().orEmpty()
        val when_ = doc.selectFirst("abbr")?.text()?.trim().orEmpty()
        val text = content.text().trim()
        val head = text.take(90).substringBeforeLast(' ', text.take(90)).trim()
        val parts = listOfNotNull(
            who.ifBlank { null },
            when_.ifBlank { null },
            head.ifBlank { null }
        )
        return if (parts.isEmpty()) "Пост $id" else parts.joinToString(" · ").take(160)
    }
}
