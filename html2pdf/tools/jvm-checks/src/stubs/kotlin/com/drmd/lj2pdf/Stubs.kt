package com.drmd.lj2pdf

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Minimal stand-ins for the app singletons Facebook.kt talks to, so the real
 * file can be compiled and exercised on the JVM without the Android SDK.
 */
object ConvertBus {
    @Volatile var cancelRequested = false
    val logLines = ArrayList<String>()
    val scanEvents = ArrayList<Pair<Int, Int>>()

    var done = 0
    var total = 0
    var lastStatus = ""

    fun log(line: String) { logLines.add(line) }
    fun scanProgress(pages: Int, found: Int) { scanEvents.add(pages to found) }
    fun progress(done: Int, total: Int, status: String) {
        this.done = done; this.total = total; lastStatus = status
    }

    fun reset() {
        cancelRequested = false; logLines.clear(); scanEvents.clear()
        done = 0; total = 0; lastStatus = ""
    }
}

object Http {
    /** Mirrors the real Http.Identity contract so BrowserSession compiles. */
    fun interface Identity {
        fun headersFor(url: String): Map<String, String>
    }

    @Volatile var identity: Identity? = null

    /** url -> served HTML. A missing url models a failed fetch (null). */
    val pages = HashMap<String, String>()
    /** url -> served bytes (images). */
    val blobs = HashMap<String, ByteArray>()
    val requested = ArrayList<String>()

    @Suppress("RedundantSuspendModifier")
    suspend fun getBytes(url: String): ByteArray? {
        requested.add(url)
        return blobs[url] ?: pages[url]?.toByteArray()
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun doc(url: String): Document? {
        requested.add(url)
        val html = pages[url] ?: return null
        return Jsoup.parse(html, url)
    }

    fun reset() { pages.clear(); blobs.clear(); requested.clear() }
}
