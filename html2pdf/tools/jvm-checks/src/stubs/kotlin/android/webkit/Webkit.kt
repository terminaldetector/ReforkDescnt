package android.webkit

import android.content.Context

/**
 * Test-harness stand-ins for the WebView cookie jar BrowserSession reads. The
 * jar here is an in-memory map keyed by registrable domain, which is enough to
 * compile and exercise the header/logout logic off-device.
 */
class CookieManager private constructor() {
    private val jar = HashMap<String, String>()
    private var accept = true

    fun setAcceptCookie(v: Boolean) { accept = v }
    fun getCookie(url: String): String? = jar[keyOf(url)]
    fun setCookie(url: String, value: String) {
        if (!accept) return
        val key = keyOf(url)
        val name = value.substringBefore('=')
        val kept = (jar[key] ?: "").split("; ").filter {
            it.isNotBlank() && it.substringBefore('=') != name
        }
        val expiring = value.contains("Max-Age=0")
        jar[key] = (if (expiring) kept else kept + value.substringBefore(';')).joinToString("; ")
        if (jar[key].isNullOrBlank()) jar.remove(key)
    }
    fun removeSessionCookies(cb: Any?) {}
    fun flush() {}

    private fun keyOf(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix(".")

    companion object {
        private val instance = CookieManager()
        @JvmStatic fun getInstance(): CookieManager = instance
    }
}

object WebSettings {
    @JvmStatic
    fun getDefaultUserAgent(ctx: Context): String =
        "Mozilla/5.0 (Linux; Android 13; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
}
