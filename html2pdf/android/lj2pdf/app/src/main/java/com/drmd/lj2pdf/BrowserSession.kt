package com.drmd.lj2pdf

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.util.Locale

/**
 * The archiver's "log in as a browser, not as an app" side.
 *
 * Sites like Facebook show a wall only to a signed-in visitor and offer no
 * public archive API, so instead of registering an application and asking for
 * API tokens the user signs in once in a real [FacebookLoginActivity] WebView.
 * Android's [CookieManager] keeps that session (it is the same cookie jar the
 * WebView uses, persisted in the app's private storage), and this object hands
 * those cookies — plus the WebView's own User-Agent — to the OkHttp downloader
 * so every later request looks like the browser that logged in.
 *
 * Nothing is copied anywhere else: the session lives only in the WebView cookie
 * jar, and "Выйти" clears it.
 */
object BrowserSession : Http.Identity {

    /** Hosts whose requests carry the browser session. */
    private val AUTHED_HOSTS = listOf("facebook.com", "fbcdn.net", "fb.com")

    @Volatile private var userAgent: String? = null

    /**
     * Warm up the WebView statics on the main thread and register as the HTTP
     * layer's identity. [CookieManager] and [WebSettings.getDefaultUserAgent]
     * both load the WebView provider on first use, which is not something the
     * download threads should be doing.
     */
    fun init(ctx: Context) {
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            userAgent = WebSettings.getDefaultUserAgent(ctx)
        } catch (t: Throwable) {
            Logx.append("[session] WebView unavailable: ${t.message}")
        }
        Http.identity = this
    }

    // ---- Http.Identity --------------------------------------------------

    override fun headersFor(url: String): Map<String, String> {
        if (!authed(url)) return emptyMap()
        val out = HashMap<String, String>(4)
        cookiesFor(url)?.let { out["Cookie"] = it }
        userAgent?.let { out["User-Agent"] = it }
        out["Accept-Language"] = acceptLanguage()
        // mbasic serves the desktop-ish markup this pipeline parses; a same-site
        // referer keeps the feed links stable across pages.
        out["Referer"] = "https://${Facebook.HOST}/"
        return out
    }

    private fun authed(url: String): Boolean {
        val host = (Uri.parse(url).host ?: "").lowercase()
        return AUTHED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    // ---- session state --------------------------------------------------

    /** The cookie header the browser would send for [url], or null. */
    fun cookiesFor(url: String): String? = try {
        CookieManager.getInstance().getCookie(url)?.ifBlank { null }
    } catch (_: Throwable) { null }

    /**
     * `c_user` is the cookie Facebook sets to the signed-in account id — its
     * presence is what separates a real session from a visitor's cookies.
     */
    fun facebookUserId(): String? {
        val c = cookiesFor("https://${Facebook.HOST}/") ?: return null
        val m = Regex("(?:^|[;\\s])c_user=([0-9]+)").find(c) ?: return null
        return m.groupValues[1]
    }

    fun isFacebookLoggedIn(): Boolean = facebookUserId() != null

    /** Persist the cookie jar to disk so the session survives a restart. */
    fun flush() {
        try { CookieManager.getInstance().flush() } catch (_: Throwable) {}
    }

    /** Sign out: drop the cookies, so nothing of the session is left behind. */
    fun logout() {
        try {
            val cm = CookieManager.getInstance()
            val host = "https://${Facebook.HOST}/"
            val names = cm.getCookie(host)?.split(';')?.mapNotNull {
                it.substringBefore('=').trim().ifBlank { null }
            } ?: emptyList()
            // Expire each cookie on every scope Facebook may have set it on —
            // most live on ".facebook.com", the rest on the exact host. The URL
            // stays a real one; only the Domain attribute varies.
            val scopes = listOf(
                host to Facebook.HOST,
                "https://www.facebook.com/" to ".facebook.com",
                "https://facebook.com/" to "facebook.com"
            )
            for ((url, domain) in scopes) {
                for (n in names) {
                    cm.setCookie(url, "$n=; Max-Age=0; Path=/; Domain=$domain")
                }
            }
            cm.removeSessionCookies(null)
            cm.flush()
            // If anything survived that, the session is still usable, and a
            // half-cleared "logout" would be a lie — drop the whole jar.
            if (facebookUserId() != null) {
                Logx.append("[session] targeted logout left cookies — clearing all")
                cm.removeAllCookies(null)
                cm.flush()
            }
        } catch (t: Throwable) {
            Logx.append("[session] logout failed: ${t.message}")
        }
    }

    private fun acceptLanguage(): String {
        val l = Locale.getDefault()
        val tag = l.language.ifBlank { "en" }
        val region = l.country
        val primary = if (region.isNotBlank()) "$tag-$region" else tag
        return "$primary,$tag;q=0.9,en;q=0.8"
    }
}
