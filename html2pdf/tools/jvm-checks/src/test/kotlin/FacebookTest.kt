import com.drmd.lj2pdf.ConvertBus
import com.drmd.lj2pdf.Facebook
import com.drmd.lj2pdf.Http
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacebookTest {

    @BeforeTest fun setUp() { Http.reset(); ConvertBus.reset() }
    @AfterTest fun tearDown() { Http.reset(); ConvertBus.reset() }

    // ---- host recognition ----------------------------------------------

    @Test fun recognisesFacebookHosts() {
        for (u in listOf(
            "https://www.facebook.com/zuck",
            "https://m.facebook.com/zuck",
            "https://mbasic.facebook.com/zuck",
            "https://web.facebook.com/zuck",
            "https://facebook.com/zuck",
            "https://fb.com/zuck"
        )) assertTrue(Facebook.isFacebook(u), u)

        for (u in listOf(
            "https://technolirik.livejournal.com/",
            "https://habr.com/ru/users/x/posts/",
            "https://notfacebook.com.evil.example/zuck"
        )) assertFalse(Facebook.isFacebook(u), u)
    }

    // ---- ids ------------------------------------------------------------

    @Test fun storyIdIsStableAcrossUrlShapes() {
        val expect = "fb10160123456789"
        assertEquals(expect, Facebook.idOf(
            "https://mbasic.facebook.com/story.php?story_fbid=10160123456789&id=4"))
        assertEquals(expect, Facebook.idOf(
            "https://www.facebook.com/permalink.php?story_fbid=10160123456789&id=4&__tn__=x"))
        assertEquals(expect, Facebook.idOf(
            "https://www.facebook.com/zuck/posts/10160123456789"))
        assertEquals(expect, Facebook.idOf(
            "https://mbasic.facebook.com/groups/999/permalink/10160123456789/"))
        assertEquals("fb555", Facebook.idOf("https://mbasic.facebook.com/photo.php?fbid=555"))
    }

    @Test fun idIsFilesystemSafe() {
        val ids = listOf(
            "https://mbasic.facebook.com/story.php?story_fbid=1&id=2",
            "https://www.facebook.com/some.page/posts/pfbid02AbC-d_e",
            "https://www.facebook.com/watch/live/?ref=x"
        ).map { Facebook.idOf(it) }
        for (id in ids) assertTrue(id.matches(Regex("[A-Za-z0-9._-]+")), id)
    }

    @Test fun tellsPostsFromFeeds() {
        assertTrue(Facebook.isPost("https://mbasic.facebook.com/story.php?story_fbid=1&id=2"))
        assertTrue(Facebook.isPost("https://www.facebook.com/zuck/posts/123"))
        assertTrue(Facebook.isPost("https://mbasic.facebook.com/groups/9/permalink/8/"))
        assertFalse(Facebook.isPost("https://www.facebook.com/zuck"))
        assertFalse(Facebook.isPost("https://www.facebook.com/groups/9"))
        assertFalse(Facebook.isPost("https://www.facebook.com/profile.php?id=4"))
    }

    // ---- normalisation ---------------------------------------------------

    @Test fun rewritesOntoMbasicAndDropsTracking() {
        assertEquals(
            "https://mbasic.facebook.com/story.php?story_fbid=10&id=4",
            Facebook.toMbasic(
                "https://www.facebook.com/story.php?story_fbid=10&id=4" +
                    "&__tn__=-R&refid=52&__cft__[0]=abc&mibextid=zz")
        )
        assertEquals(
            "https://mbasic.facebook.com/zuck",
            Facebook.toMbasic("https://web.facebook.com/zuck?locale=ru_RU")
        )
    }

    @Test fun buildsTheRightFeedUrl() {
        assertEquals("https://mbasic.facebook.com/zuck?v=timeline",
            Facebook.feedUrl("https://www.facebook.com/zuck"))
        assertEquals("https://mbasic.facebook.com/zuck?v=timeline",
            Facebook.feedUrl("https://www.facebook.com/zuck/"))
        assertEquals("https://mbasic.facebook.com/profile.php?id=100044&v=timeline",
            Facebook.feedUrl("https://www.facebook.com/profile.php?id=100044"))
        assertEquals("https://mbasic.facebook.com/groups/123456",
            Facebook.feedUrl("https://www.facebook.com/groups/123456/"))
        // A permalink is its own "feed": archive just that story.
        assertEquals("https://mbasic.facebook.com/story.php?story_fbid=7&id=4",
            Facebook.feedUrl("https://www.facebook.com/story.php?story_fbid=7&id=4"))
    }

    @Test fun eachTargetGetsItsOwnProjectName() {
        val names = listOf(
            "https://www.facebook.com/zuck",
            "https://www.facebook.com/meta",
            "https://www.facebook.com/groups/123456",
            "https://www.facebook.com/profile.php?id=100044"
        ).map { Facebook.targetName(it) }
        assertEquals(names.size, names.toSet().size, "project names collided: $names")
        for (n in names) assertTrue(n.matches(Regex("[A-Za-z0-9._-]+")), n)
        assertEquals("facebook.com_zuck", names[0])
        assertEquals("facebook.com_group_123456", names[2])
        assertEquals("facebook.com_id_100044", names[3])
    }

    @Test fun unwrapsRedirectLinks() {
        assertEquals(
            "https://example.org/a?b=c",
            Facebook.unwrap("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.org%2Fa%3Fb%3Dc&h=AT0")
        )
        assertEquals("https://mbasic.facebook.com/zuck",
            Facebook.unwrap("https://mbasic.facebook.com/zuck"))
    }

    // ---- session detection ----------------------------------------------

    @Test fun spotsTheLoginWall() {
        val login = Jsoup.parse(
            """<html><body><form method="post" action="/login/device-based/regular/login/">
               <input name="email"><input type="password" name="pass">
               </form></body></html>""",
            "https://mbasic.facebook.com/zuck?v=timeline"
        )
        assertTrue(Facebook.looksLoggedOut(login))
        assertFalse(Facebook.looksLoggedOut(Jsoup.parse(FEED_PAGE_1, FEED_1_URL)))
    }

    // ---- enumeration -----------------------------------------------------

    @Test fun walksTheFeedAcrossPagesAndDeduplicates() = runBlocking {
        Http.pages[FEED_1_URL] = FEED_PAGE_1
        Http.pages[FEED_2_URL] = FEED_PAGE_2

        val found = Facebook.scanAll("https://www.facebook.com/zuck", 100) {}

        assertEquals(
            listOf(
                "https://mbasic.facebook.com/story.php?story_fbid=111&id=4",
                "https://mbasic.facebook.com/story.php?story_fbid=222&id=4",
                "https://mbasic.facebook.com/zuck/posts/333",
                "https://mbasic.facebook.com/story.php?story_fbid=444&id=4"
            ),
            found
        )
        // Both feed pages were read, each exactly once.
        assertEquals(listOf(FEED_1_URL, FEED_2_URL), Http.requested)
    }

    @Test fun honoursTheMaximum() = runBlocking {
        Http.pages[FEED_1_URL] = FEED_PAGE_1
        Http.pages[FEED_2_URL] = FEED_PAGE_2
        assertEquals(2, Facebook.scanAll("https://www.facebook.com/zuck", 2) {}.size)
    }

    @Test fun updateStopsAtTheFirstArchivedPost() = runBlocking {
        Http.pages[FEED_1_URL] = FEED_PAGE_1
        Http.pages[FEED_2_URL] = FEED_PAGE_2

        val fresh = Facebook.scanNew(
            "https://www.facebook.com/zuck", setOf("fb222"), 100) {}

        assertEquals(
            listOf("https://mbasic.facebook.com/story.php?story_fbid=111&id=4"), fresh)
        // Stopped on the first page — no needless second request.
        assertEquals(listOf(FEED_1_URL), Http.requested)
    }

    @Test fun signedOutScanStopsAndSaysWhy() = runBlocking {
        Http.pages[FEED_1_URL] =
            """<html><body><form action="/login/"><input name="pass"></form></body></html>"""
        assertTrue(Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.isEmpty())
        assertTrue(ConvertBus.logLines.any { it.contains("not signed in") },
            ConvertBus.logLines.toString())
    }

    @Test fun aPermalinkScansAsItself() = runBlocking {
        val one = Facebook.scanAll(
            "https://www.facebook.com/zuck/posts/999?__tn__=x", 100) {}
        assertEquals(listOf("https://mbasic.facebook.com/zuck/posts/999"), one)
        assertTrue(Http.requested.isEmpty(), "a permalink needs no feed request")
    }

    @Test fun cancellingStopsTheWalk() = runBlocking {
        Http.pages[FEED_1_URL] = FEED_PAGE_1
        Http.pages[FEED_2_URL] = FEED_PAGE_2
        ConvertBus.cancelRequested = true
        assertTrue(Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.isEmpty())
        assertTrue(Http.requested.isEmpty())
    }

    // ---- titles ----------------------------------------------------------

    @Test fun buildsAReadableChapterTitle() {
        val doc = Jsoup.parse(STORY_PAGE, "https://mbasic.facebook.com/story.php?story_fbid=111&id=4")
        val content = doc.selectFirst("#m_story_permalink_view")!!
        val title = Facebook.titleOf(doc, content, "fb111")
        assertTrue(title.startsWith("Mark Zuckerberg · 12 марта"), title)
        assertTrue(title.contains("Сегодня мы запускаем"), title)
        assertTrue(title.length <= 160)
    }

    @Test fun titleFallsBackWhenThePageSaysNothing() {
        val doc = Jsoup.parse("<html><body><div id=x></div></body></html>", "https://mbasic.facebook.com/")
        assertEquals("Пост fb7", Facebook.titleOf(doc, doc.selectFirst("#x")!!, "fb7"))
    }

    companion object {
        const val FEED_1_URL = "https://mbasic.facebook.com/zuck?v=timeline"
        const val FEED_2_URL =
            "https://mbasic.facebook.com/zuck?v=timeline&sectionLoadingID=m_timeline_loading_unit&timeend=1600000000"

        /** Two stories, one repeated link, one like-action, and a "see more". */
        val FEED_PAGE_1 = """
            <html><body>
              <div id="header"><a href="/home.php">Home</a></div>
              <div data-ft='{"top_level_post_id":"111"}'>
                <a href="/story.php?story_fbid=111&amp;id=4&amp;__tn__=-R">Full story</a>
                <a href="/a/comment.php?fbid=111">Comment</a>
                <a href="/ufi/reaction/profile/browser/?ft_ent_identifier=111">2 likes</a>
              </div>
              <div data-ft='{"top_level_post_id":"222"}'>
                <a href="https://www.facebook.com/story.php?story_fbid=222&amp;id=4">Full story</a>
                <a href="/story.php?story_fbid=222&amp;id=4&amp;refid=52">Comments</a>
              </div>
              <a href="/zuck?v=timeline&amp;sectionLoadingID=m_timeline_loading_unit&amp;timeend=1600000000">See more stories</a>
            </body></html>
        """.trimIndent()

        /** Continues the feed; the last link is a dead end (already visited). */
        val FEED_PAGE_2 = """
            <html><body>
              <div data-ft='{"top_level_post_id":"333"}'>
                <a href="/zuck/posts/333">Full story</a>
              </div>
              <div data-ft='{"top_level_post_id":"444"}'>
                <a href="/story.php?story_fbid=444&amp;id=4">Full story</a>
              </div>
              <a href="/zuck?v=timeline&amp;sectionLoadingID=m_timeline_loading_unit&amp;timeend=1600000000">See more stories</a>
            </body></html>
        """.trimIndent()

        val STORY_PAGE = """
            <html><body>
              <div id="m_story_permalink_view">
                <h3><a href="/zuck">Mark Zuckerberg</a></h3>
                <abbr>12 марта 2024 г.</abbr>
                <div>Сегодня мы запускаем большое обновление, о котором давно просили.</div>
              </div>
            </body></html>
        """.trimIndent()
    }
}
