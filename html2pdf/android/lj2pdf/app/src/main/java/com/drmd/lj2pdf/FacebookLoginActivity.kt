package com.drmd.lj2pdf

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Signs in to Facebook the way a browser does — no app registration, no API
 * keys, no tokens: a real WebView opens Facebook's own login page, the user
 * types their credentials into Facebook's form (including any two-factor or
 * checkpoint step, which works because this *is* a browser), and Android's
 * cookie jar keeps the resulting session.
 *
 * The credentials are never seen by this app: they go from the WebView straight
 * to Facebook over TLS. What the archiver keeps afterwards is the same session
 * cookie a browser would hold, read back through [BrowserSession].
 *
 * The screen closes itself as soon as the `c_user` cookie appears, which is
 * Facebook's signal that the login completed.
 */
class FacebookLoginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var status: TextView
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        status = TextView(this).apply {
            text = "Вход в Facebook — как в браузере"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(status)
        bar.addView(MaterialButton(this).apply {
            text = "Выйти"
            setOnClickListener { signOut() }
        })
        bar.addView(MaterialButton(this).apply {
            text = "Готово"
            setOnClickListener { finishIfSignedIn(manual = true) }
        })
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        configureWebView()
        web.loadUrl(LOGIN_URL)
    }

    private fun configureWebView() {
        // The default User-Agent is deliberately left alone: BrowserSession
        // sends the very same string with every later download, so Facebook
        // sees one consistent client instead of a session that changes browser
        // half way through.
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        // Facebook's login bounces through its own sub-domains; without
        // third-party cookies the session never completes. (minSdk is 21, so
        // this is always available.)
        cm.setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                BrowserSession.flush()
                updateStatus()
                finishIfSignedIn(manual = false)
            }
        }
    }

    private fun updateStatus() {
        val id = BrowserSession.facebookUserId()
        status.text = if (id != null) "Сессия активна (id $id)" else "Войдите в свой аккаунт"
    }

    /**
     * Close as soon as Facebook hands out a session. Tapping «Готово» before
     * that only reports what is missing — closing early would leave the
     * archiver without cookies and every scan would come back empty.
     */
    private fun finishIfSignedIn(manual: Boolean) {
        if (done) return
        val id = BrowserSession.facebookUserId()
        if (id == null) {
            if (manual) toast("Ещё не вошли — Facebook не выдал сессию.")
            return
        }
        done = true
        BrowserSession.flush()
        Logx.append("[fb] browser session acquired (id $id)")
        setResult(RESULT_OK)
        toast("Facebook: вход выполнен")
        finish()
    }

    private fun signOut() {
        BrowserSession.logout()
        done = false
        updateStatus()
        web.loadUrl(LOGIN_URL)
        toast("Сессия очищена")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        BrowserSession.flush()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        /** mbasic's own login form: the same host the archiver later reads. */
        private const val LOGIN_URL = "https://mbasic.facebook.com/login/"
    }
}
