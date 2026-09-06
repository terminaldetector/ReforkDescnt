import android.content.Context
import android.webkit.CookieManager
import com.drmd.lj2pdf.BrowserSession
import com.drmd.lj2pdf.Http
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "log in as a browser" side: which requests carry the session, and what. */
class BrowserSessionTest {

    @BeforeTest fun setUp() {
        BrowserSession.logout()
        BrowserSession.init(Context(File("/tmp"), File("/tmp")))
    }

    private fun signIn() {
        val cm = CookieManager.getInstance()
        cm.setCookie("https://mbasic.facebook.com/", "c_user=100044123; Path=/")
        cm.setCookie("https://mbasic.facebook.com/", "xs=42%3Aabc; Path=/")
    }

    @Test fun registersItselfWithTheHttpLayer() {
        assertTrue(Http.identity === BrowserSession)
    }

    @Test fun signedOutMeansNoSessionAndNoCookieHeader() {
        assertNull(BrowserSession.facebookUserId())
        assertTrue(!BrowserSession.isFacebookLoggedIn())
        assertNull(BrowserSession.headersFor("https://mbasic.facebook.com/zuck")["Cookie"])
    }

    @Test fun theSessionIsIdentifiedByCUser() {
        signIn()
        assertEquals("100044123", BrowserSession.facebookUserId())
        assertTrue(BrowserSession.isFacebookLoggedIn())
    }

    @Test fun onlyFacebookRequestsCarryTheSession() {
        signIn()
        val fb = BrowserSession.headersFor("https://mbasic.facebook.com/zuck?v=timeline")
        assertTrue(fb["Cookie"]!!.contains("c_user=100044123"))
        assertTrue(fb["User-Agent"]!!.contains("Mobile Safari"))
        assertTrue(fb.containsKey("Accept-Language"))

        // Someone else's blog is fetched anonymously — no cookies leak there.
        assertTrue(BrowserSession.headersFor("https://technolirik.livejournal.com/1.html").isEmpty())
        assertTrue(BrowserSession.headersFor("https://notfacebook.com.evil.example/").isEmpty())
    }

    @Test fun logoutLeavesNothingBehind() {
        signIn()
        BrowserSession.logout()
        assertNull(BrowserSession.facebookUserId())
        assertTrue(BrowserSession.headersFor("https://mbasic.facebook.com/zuck")["Cookie"] == null)
    }
}
