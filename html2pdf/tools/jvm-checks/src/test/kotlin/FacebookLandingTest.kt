import com.drmd.lj2pdf.ConvertBus
import com.drmd.lj2pdf.Facebook
import com.drmd.lj2pdf.Http
import com.drmd.lj2pdf.ImageArchiver
import com.drmd.lj2pdf.PostEntry
import com.drmd.lj2pdf.Project
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A profile's **landing page is not its feed**, and that is where scans used to
 * die: mbasic serves navigation, a photos grid and a friends grid at `/<name>`,
 * with the stories below the fold or behind a "Хроника" link entirely. These
 * cover getting through it — and not turning its photo grid into a crawl.
 */
class FacebookLandingTest {

    private lateinit var work: File

    @BeforeTest fun setUp() {
        Http.reset(); ConvertBus.reset()
        work = File(System.getProperty("java.io.tmpdir"), "lj2pdf-land-${System.nanoTime()}")
        work.mkdirs()
    }

    @AfterTest fun tearDown() {
        work.deleteRecursively(); Http.reset(); ConvertBus.reset()
    }

    // ---- entry points -----------------------------------------------------

    @Test fun offersMoreThanOneDoorIntoAProfile() {
        assertEquals(
            listOf(
                "https://mbasic.facebook.com/zuck?v=timeline",
                "https://mbasic.facebook.com/zuck",
                "https://mbasic.facebook.com/zuck/posts/"
            ),
            Facebook.feedUrls("https://www.facebook.com/zuck")
        )
        assertEquals(
            listOf(
                "https://mbasic.facebook.com/profile.php?id=100044&v=timeline",
                "https://mbasic.facebook.com/profile.php?id=100044"
            ),
            Facebook.feedUrls("https://www.facebook.com/profile.php?id=100044")
        )
        // A group and a permalink each have exactly one sensible entry.
        assertEquals(1, Facebook.feedUrls("https://www.facebook.com/groups/123456").size)
        assertEquals(1, Facebook.feedUrls("https://www.facebook.com/zuck/posts/999").size)
        // feedUrl stays the first of them, for callers that want just one.
        assertEquals(
            Facebook.feedUrls("https://www.facebook.com/zuck").first(),
            Facebook.feedUrl("https://www.facebook.com/zuck")
        )
    }

    @Test fun aPhotoTileIsNavigationNotAStory() {
        // Every tile of a profile's photos grid links a photo page. They are
        // content when asked for directly, but they are not the wall's posts.
        for (u in listOf(
            "https://mbasic.facebook.com/photo.php?fbid=901&set=a.1",
            "https://mbasic.facebook.com/photo/view_full_size/?fbid=901",
            "https://mbasic.facebook.com/zuck/photos/a.123/456/"
        )) {
            assertTrue(Facebook.isPhotoPage(u), u)
            assertTrue(!Facebook.isStory(u), "counted as a story: $u")
        }
        for (u in listOf(
            "https://mbasic.facebook.com/story.php?story_fbid=111&id=4",
            "https://mbasic.facebook.com/zuck/posts/111",
            "https://mbasic.facebook.com/permalink.php?story_fbid=111&id=4",
            "https://mbasic.facebook.com/groups/9/permalink/8/"
        )) {
            assertTrue(!Facebook.isPhotoPage(u), "mistaken for a photo page: $u")
            assertTrue(Facebook.isStory(u), "not counted as a story: $u")
        }
    }

    @Test fun aPastedPhotoPageStillArchivesAsItself() = runBlocking {
        // Excluding photo tiles from feed enumeration must not stop someone
        // archiving one photo on purpose.
        val one = Facebook.scanAll("https://www.facebook.com/photo.php?fbid=901", 10) {}
        assertEquals(listOf("https://mbasic.facebook.com/photo.php?fbid=901"), one)
        assertTrue(Http.requested.isEmpty())
    }

    // ---- getting past the landing page ------------------------------------

    @Test fun aLandingPageIsFollowedThroughToTheTimeline() = runBlocking {
        Http.pages[TIMELINE_URL] = LANDING_PAGE
        Http.pages[TIMELINE_TAB] = TIMELINE_WITH_STORIES

        val found = Facebook.scanAll("https://www.facebook.com/zuck", 100) {}

        assertEquals(
            listOf(
                "https://mbasic.facebook.com/story.php?story_fbid=111&id=4",
                "https://mbasic.facebook.com/story.php?story_fbid=222&id=4"
            ),
            found
        )
        assertTrue(ConvertBus.logLines.any { it.contains("timeline link") },
            ConvertBus.logLines.toString())
    }

    @Test fun aBarrenLandingPageFallsThroughToTheNextEntryPoint() = runBlocking {
        Http.pages[TIMELINE_URL] = DEAD_END
        Http.pages[PROFILE_URL] = TIMELINE_WITH_STORIES

        val found = Facebook.scanAll("https://www.facebook.com/zuck", 100) {}

        assertEquals(2, found.size, "found: $found / ${ConvertBus.logLines}")
        assertEquals(listOf(TIMELINE_URL, PROFILE_URL), Http.requested)
    }

    @Test fun aBarrenPageSaysWhatItActuallyContained() = runBlocking {
        Http.pages[TIMELINE_URL] = DEAD_END

        assertTrue(Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.isEmpty())
        assertTrue(ConvertBus.logLines.any { it.contains("no stories among") },
            ConvertBus.logLines.toString())
        assertTrue(ConvertBus.logLines.any { it.contains("nothing found") },
            ConvertBus.logLines.toString())
    }

    @Test fun everyEntryPointIsTriedExactlyOnceAndThenItStops() = runBlocking {
        for (u in Facebook.feedUrls("https://www.facebook.com/zuck")) Http.pages[u] = DEAD_END

        assertTrue(Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.isEmpty())
        assertEquals(Facebook.feedUrls("https://www.facebook.com/zuck"), Http.requested)
        assertEquals(Http.requested.size, Http.requested.toSet().size, "a page was fetched twice")
    }

    @Test fun onceStoriesAreFoundTheOtherDoorsAreLeftAlone() = runBlocking {
        Http.pages[TIMELINE_URL] = TIMELINE_WITH_STORIES

        assertEquals(2, Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.size)
        assertEquals(listOf(TIMELINE_URL), Http.requested)
    }

    @Test fun aMissingPageEndsTheWalkInsteadOfKnockingForever() = runBlocking {
        // Nothing served at all: one attempt per door, then done.
        assertTrue(Facebook.scanAll("https://www.facebook.com/zuck", 100) {}.isEmpty())
        assertEquals(1, Http.requested.size, "a failed fetch should not fan out: ${Http.requested}")
    }

    // ---- the photos grid must not become a crawl --------------------------

    @Test fun aProfilePhotoGridIsNotSweptAsIfItWereAStory() = runBlocking {
        // 60 photo tiles, each linking a photo page. Only the tiles' own
        // thumbnails have originals worth resolving — the grid sweep belongs
        // to stories ("Ещё N фото"), not to a profile's navigation.
        Http.pages[PROFILE_URL] = photoGrid(60)
        val project = Project(File(work, "p")).also { it.dir.mkdirs() }

        ImageArchiver.harvest(
            project, listOf(PostEntry("x", PROFILE_URL, "")),
            ImageArchiver.Options(minSide = 0, minBytes = 0, maxPhotoPages = 10, parallelism = 4)
        )

        // The cap holds: at most 10 photo pages opened, not 60.
        val opened = Http.requested.count { it.contains("/photo.php") }
        assertTrue(opened <= 10, "opened $opened photo pages")
    }

    @Test fun aStorysExtraPhotosAreStillSwept() = runBlocking {
        // The same markup reached as a story: here the linked photos are the
        // post's own, so they are followed.
        Http.pages[STORY_URL] = photoGrid(5)
        val project = Project(File(work, "p2")).also { it.dir.mkdirs() }

        ImageArchiver.harvest(
            project, listOf(PostEntry("fb1", STORY_URL, "")),
            ImageArchiver.Options(minSide = 0, minBytes = 0, maxPhotoPages = 40, parallelism = 4)
        )

        assertTrue(Http.requested.any { it.contains("/photo.php") },
            "a story's photo pages should be opened: ${Http.requested}")
    }

    companion object {
        const val TIMELINE_URL = "https://mbasic.facebook.com/zuck?v=timeline"
        const val PROFILE_URL = "https://mbasic.facebook.com/zuck"
        const val TIMELINE_TAB = "https://mbasic.facebook.com/zuck?v=timeline&lst=4%3A4%3A1"
        const val STORY_URL = "https://mbasic.facebook.com/story.php?story_fbid=111&id=4"

        /** Navigation only — no story link anywhere, and no way onward. */
        val DEAD_END = """<html><body><a href="/zuck/friends">Друзья</a></body></html>"""

        /**
         * A profile landing page as mbasic serves it: header, cover, name,
         * an about link, a friends link, a photos grid — and the stories only
         * behind "Хроника".
         */
        val LANDING_PAGE = """
            <html><body>
              <div id="header"><a href="/home.php">Главная</a></div>
              <img src="https://scontent.xx.fbcdn.net/v/t1/cover.jpg">
              <h3>Mark Zuckerberg</h3>
              <a href="/zuck/friends">Друзья</a>
              <a href="/zuck/about">Информация</a>
              <div id="photos">
                <a href="/photo.php?fbid=901&amp;set=a.1"><img src="https://scontent.xx.fbcdn.net/v/s160x160/901.jpg"></a>
                <a href="/photo.php?fbid=902&amp;set=a.1"><img src="https://scontent.xx.fbcdn.net/v/s160x160/902.jpg"></a>
              </div>
              <a href="/zuck?v=timeline&amp;lst=4%3A4%3A1">Хроника</a>
            </body></html>
        """.trimIndent()

        val TIMELINE_WITH_STORIES = """
            <html><body>
              <div data-ft='{"top_level_post_id":"111"}'>
                <a href="/story.php?story_fbid=111&amp;id=4">Полностью</a>
              </div>
              <div data-ft='{"top_level_post_id":"222"}'>
                <a href="/story.php?story_fbid=222&amp;id=4">Полностью</a>
              </div>
            </body></html>
        """.trimIndent()

        /** [n] photo tiles, each a thumbnail wrapped in a photo-page link. */
        fun photoGrid(n: Int): String = buildString {
            append("<html><body>")
            for (i in 1..n) {
                append("""<a href="/photo.php?fbid=$i&amp;set=a.1">""")
                append("""<img src="https://scontent.xx.fbcdn.net/v/s160x160/$i.jpg"></a>""")
            }
            append("</body></html>")
        }
    }
}
