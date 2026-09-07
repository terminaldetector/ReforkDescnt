import android.content.Context
import com.drmd.lj2pdf.AlbumBuilder
import com.drmd.lj2pdf.ConvertBus
import com.drmd.lj2pdf.Facebook
import com.drmd.lj2pdf.Http
import com.drmd.lj2pdf.ImageArchiver
import com.drmd.lj2pdf.ImageCodec
import com.drmd.lj2pdf.PostEntry
import com.drmd.lj2pdf.Project
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Image mode: the pictures are the archive. These run the real ImageArchiver,
 * ImageCodec and AlbumBuilder over served fixtures, with genuine JPEG/PNG bytes
 * so decoding, measuring, de-duplication and transcoding actually happen.
 */
class ImageModeTest {

    private lateinit var work: File
    private lateinit var ctx: Context
    private lateinit var project: Project

    @BeforeTest fun setUp() {
        Http.reset(); ConvertBus.reset()
        work = File(System.getProperty("java.io.tmpdir"), "lj2pdf-img-${System.nanoTime()}")
        work.mkdirs()
        ctx = Context(File(work, "files"), File(ASSETS))
        project = Project(File(work, "projects/facebook.com_zuck")).also { it.dir.mkdirs() }
    }

    @AfterTest fun tearDown() {
        work.deleteRecursively()
        Http.reset(); ConvertBus.reset()
    }

    // ---- full-size resolution -------------------------------------------

    @Test fun aThumbnailIsFollowedToTheOriginal() = runBlocking {
        Http.pages["https://mbasic.facebook.com/photo.php?fbid=777"] = PHOTO_PAGE_HTML
        // The page's own "full size" link is taken whole — its parameters are
        // Facebook's, and trimming them turns the fetch into a redirect back to
        // the login page.
        val full = Facebook.fullSizeImage("https://www.facebook.com/photo.php?fbid=777")
        assertEquals(FULL_URL, full)
    }

    @Test fun aPhotoPageWithoutAFullSizeLinkFallsBackToItsBiggestPicture() = runBlocking {
        Http.pages["https://mbasic.facebook.com/photo.php?fbid=888"] = """
            <html><body><img src="https://scontent.xx.fbcdn.net/v/p720x720/888.jpg"></body></html>
        """.trimIndent()
        assertEquals(
            "https://scontent.xx.fbcdn.net/v/p720x720/888.jpg",
            Facebook.fullSizeImage("https://mbasic.facebook.com/photo.php?fbid=888")
        )
    }

    @Test fun facebookChromeIsNotMistakenForContent() {
        assertTrue(Facebook.isPhotoUrl("https://scontent.xx.fbcdn.net/v/t1/photo.jpg"))
        assertFalse(Facebook.isPhotoUrl("https://static.xx.fbcdn.net/rsrc.php/v3/ye/emoji.png"))
        assertFalse(Facebook.isPhotoUrl("https://example.org/photo.jpg"))
    }

    @Test fun theCdnCountsAsFacebookEvenThoughItIsAnotherDomain() {
        // The site is facebook.com, the files come from fbcdn.net — telling
        // "their asset" from "someone else's image" needs both.
        assertTrue(Facebook.isFacebookAsset("https://scontent.xx.fbcdn.net/v/t1/photo.jpg"))
        assertTrue(Facebook.isFacebookAsset("https://static.xx.fbcdn.net/rsrc.php/emoji.png"))
        assertTrue(Facebook.isFacebookAsset("https://mbasic.facebook.com/photo.php?fbid=1"))
        assertFalse(Facebook.isFacebookAsset("https://example.org/photo.jpg"))
        assertFalse(Facebook.isFacebookAsset("https://notfbcdn.net.evil.example/x.jpg"))
    }

    // ---- harvesting -------------------------------------------------------

    @Test fun harvestSavesOriginalsSkipsJunkAndDropsDuplicates() = runBlocking {
        serveStory()
        val shots = ImageArchiver.harvest(
            project,
            listOf(PostEntry("fb111", STORY_URL, "Отпуск 2024")),
            ImageArchiver.Options(minSide = 200, minBytes = 100, parallelism = 2)
        )

        // The big photo (via the thumbnail's photo page) and the second one from
        // the grid link. The 40×40 avatar and the emoji sprite are not pictures.
        assertEquals(2, shots.size, "kept: ${shots.map { it.name }} / ${ConvertBus.logLines}")
        // Sprites are recognisable from their URL, so they cost nothing at all.
        assertFalse(Http.requested.contains(EMOJI_URL), "an emoji sprite was downloaded")
        // An avatar sits on the same content CDN as the photographs and cannot
        // be told apart until it is measured — so it is fetched, then dropped
        // by the size filter. What matters is that it is not in the archive.
        assertTrue(Http.requested.contains(AVATAR_URL))
        assertTrue(
            project.galleryFiles().none { ImageIO.read(it).width < 200 },
            "something avatar-sized was kept: ${project.galleryFiles().map { it.name }}"
        )
        for (s in shots) {
            assertTrue(File(project.galleryDir, s.name).length() > 0, s.name)
            assertTrue(maxOf(s.width, s.height) >= 200, "${s.name} is ${s.width}×${s.height}")
            assertTrue(s.hash.isNotBlank())
        }
        // The saved bytes are the ORIGINAL, not the 320px preview.
        val first = ImageIO.read(File(project.galleryDir, shots[0].name))
        assertEquals(900, first.width)

        // The index round-trips.
        val loaded = ImageArchiver.loadIndex(project)
        assertEquals(shots.map { it.name }, loaded.map { it.name })
        assertEquals(shots.map { it.hash }, loaded.map { it.hash })
        assertTrue(loaded[0].source.isNotBlank())
    }

    @Test fun theSamePictureUnderTwoUrlsIsStoredOnce() = runBlocking {
        val a = "https://example.org/a.jpg"
        val b = "https://example.org/b.jpg"
        val same = jpeg(800, 600, Color.BLUE)
        Http.blobs[a] = same
        Http.blobs[b] = same.copyOf()          // identical bytes, different URL
        Http.pages[PAGE] = """
            <html><body><img src="$a"><img src="$b"></body></html>
        """.trimIndent()

        val shots = ImageArchiver.harvest(
            project, listOf(PostEntry("p1", PAGE, "")),
            ImageArchiver.Options(fullSize = false, minSide = 100, minBytes = 100)
        )
        assertEquals(1, shots.size, "duplicate kept: ${shots.map { it.name }}")
    }

    @Test fun aSecondRunOnlyAddsWhatIsNew() = runBlocking {
        Http.blobs["https://example.org/1.jpg"] = jpeg(700, 500, Color.RED)
        Http.pages[PAGE] = """<html><body><img src="https://example.org/1.jpg"></body></html>"""
        val opts = ImageArchiver.Options(fullSize = false, minSide = 100, minBytes = 100)
        val page = listOf(PostEntry("p1", PAGE, ""))

        val firstRun = ImageArchiver.harvest(project, page, opts)
        assertEquals(1, firstRun.size)

        // Same page again → nothing new; the existing shot is kept, not renamed.
        val second = ImageArchiver.harvest(project, page, opts, firstRun)
        assertEquals(1, second.size)
        assertEquals(firstRun[0].name, second[0].name)

        // Now the page gains a picture → exactly one is added, numbered after it.
        Http.blobs["https://example.org/2.jpg"] = jpeg(700, 500, Color.GREEN)
        Http.pages[PAGE] = """<html><body>
            <img src="https://example.org/1.jpg"><img src="https://example.org/2.jpg"></body></html>"""
        val third = ImageArchiver.harvest(project, page, opts, second)
        assertEquals(2, third.size)
        assertEquals(listOf("00001.jpg", "00002.jpg"), third.map { it.name })
    }

    @Test fun aThumbnailLinkedToItsOriginalIsTakenAtFullSize() = runBlocking {
        Http.blobs["https://blog.example/thumb.jpg"] = jpeg(150, 100, Color.GRAY)
        Http.blobs["https://blog.example/full.jpg"] = jpeg(1600, 1200, Color.GRAY)
        Http.pages[PAGE] = """
            <html><body>
              <a href="https://blog.example/full.jpg">
                <img src="https://blog.example/thumb.jpg">
              </a>
            </body></html>
        """.trimIndent()

        val shots = ImageArchiver.harvest(
            project, listOf(PostEntry("p1", PAGE, "")),
            ImageArchiver.Options(minSide = 300, minBytes = 100)
        )
        assertEquals(1, shots.size)
        assertEquals(1600, shots[0].width)
        assertFalse(Http.requested.contains("https://blog.example/thumb.jpg"),
            "the preview should not be downloaded when the original is linked")
    }

    @Test fun formatIsReadFromTheBytesNotTheUrl() {
        assertEquals("jpg", ImageArchiver.extOf(jpeg(10, 10, Color.RED), "https://cdn/x?a=b"))
        assertEquals("png", ImageArchiver.extOf(png(10, 10), "https://cdn/x?a=b"))
        assertEquals("jpg", ImageArchiver.extOf(ByteArray(4), "https://cdn/photo.jpg"))
    }

    // ---- transcoding ------------------------------------------------------

    @Test fun jpegIsPassedThroughAndPngIsConverted() {
        val j = jpeg(50, 50, Color.RED)
        assertTrue(ImageCodec.toJpeg(j) === j, "a JPEG should not be re-encoded")

        val p = png(50, 50)
        val out = ImageCodec.toJpeg(p)
        assertNotNull(out)
        assertTrue(ImageCodec.isJpeg(out))
        assertEquals(50, ImageIO.read(out.inputStream()).width)

        assertNull(ImageCodec.toJpeg("not an image".toByteArray()))
    }

    // ---- albums -----------------------------------------------------------

    @Test fun pdfAlbumIsOnePagePerPictureShapedToIt() = runBlocking {
        val shots = harvestThree()
        val out = File(work, "album.pdf")

        assertTrue(AlbumBuilder.buildPdf(ctx, project, shots, out, captions = true))

        PDDocument.load(out).use { doc ->
            assertEquals(3, doc.numberOfPages)
            // Landscape stays landscape; portrait stays portrait (plus caption strip).
            val p0 = doc.getPage(0).mediaBox
            assertTrue(p0.width > p0.height, "${p0.width}×${p0.height}")
            val p1 = doc.getPage(1).mediaBox
            assertTrue(p1.height > p1.width, "${p1.width}×${p1.height}")
            // One bookmark per picture, carrying the caption.
            val outline = doc.documentCatalog.documentOutline
            assertNotNull(outline)
            val titles = generateSequence(outline.firstChild) { it.nextSibling }.map { it.title }.toList()
            assertEquals(3, titles.size)
            assertTrue(titles[0].contains("Отпуск"), titles.toString())
        }
    }

    @Test fun pdfAlbumEmbedsPngsToo() = runBlocking {
        Http.blobs["https://example.org/shot.png"] = png(600, 400)
        Http.pages[PAGE] = """<html><body><img src="https://example.org/shot.png"></body></html>"""
        val shots = ImageArchiver.harvest(
            project, listOf(PostEntry("p1", PAGE, "")),
            ImageArchiver.Options(fullSize = false, minSide = 100, minBytes = 100))
        assertEquals(1, shots.size)
        assertTrue(shots[0].name.endsWith(".png"))

        val out = File(work, "png-album.pdf")
        assertTrue(AlbumBuilder.buildPdf(ctx, project, shots, out, captions = false))
        PDDocument.load(out).use { assertEquals(1, it.numberOfPages) }
    }

    @Test fun cbzHoldsTheOriginalsUntouchedAndInOrder() = runBlocking {
        val shots = harvestThree()
        val out = File(work, "album.cbz")

        assertTrue(AlbumBuilder.buildCbz(project, shots, out))

        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(shots.map { it.name }, names.filter { it != "000_index.txt" })
            assertNotNull(zip.getEntry("000_index.txt"))
            // Byte-for-byte the harvested file — a CBZ must not re-encode.
            val entry = zip.getEntry(shots[0].name)
            assertContentEqualsBytes(
                File(project.galleryDir, shots[0].name).readBytes(),
                zip.getInputStream(entry).readBytes()
            )
        }
    }

    @Test fun anEmptyGalleryProducesNoAlbum() {
        assertFalse(AlbumBuilder.buildPdf(ctx, project, emptyList(), File(work, "a.pdf")))
        val cbz = File(work, "a.cbz")
        assertFalse(AlbumBuilder.buildCbz(project, emptyList(), cbz))
        assertFalse(cbz.exists(), "an empty CBZ should not be left behind")
    }

    // ---- helpers ----------------------------------------------------------

    private fun assertContentEqualsBytes(a: ByteArray, b: ByteArray) {
        assertEquals(a.size, b.size, "size differs")
        assertTrue(a.contentEquals(b), "bytes differ")
    }

    private suspend fun harvestThree(): List<ImageArchiver.Shot> {
        Http.blobs["https://example.org/wide.jpg"] = jpeg(1200, 800, Color.RED)     // landscape
        Http.blobs["https://example.org/tall.jpg"] = jpeg(800, 1200, Color.GREEN)   // portrait
        Http.blobs["https://example.org/sq.jpg"] = jpeg(900, 900, Color.BLUE)
        Http.pages[PAGE] = """
            <html><body>
              <img src="https://example.org/wide.jpg" alt="Отпуск, день 1">
              <img src="https://example.org/tall.jpg" alt="Отпуск, день 2">
              <img src="https://example.org/sq.jpg" alt="Отпуск, день 3">
            </body></html>
        """.trimIndent()
        val shots = ImageArchiver.harvest(
            project, listOf(PostEntry("p1", PAGE, "")),
            ImageArchiver.Options(fullSize = false, minSide = 100, minBytes = 100))
        assertEquals(3, shots.size)
        return shots
    }

    private fun serveStory() {
        Http.pages[STORY_URL] = STORY_HTML
        Http.pages[PHOTO_PAGE] = PHOTO_PAGE_HTML
        Http.pages[PHOTO_PAGE_2] = PHOTO_PAGE_2_HTML
        Http.blobs[FULL_URL] = jpeg(900, 700, Color.RED)
        Http.blobs[FULL_URL_2] = jpeg(1000, 800, Color.BLUE)
        Http.blobs[THUMB_URL] = jpeg(320, 240, Color.RED)
        Http.blobs[AVATAR_URL] = jpeg(40, 40, Color.DARK_GRAY)
        Http.blobs[EMOJI_URL] = png(16, 16)
    }

    private fun jpeg(w: Int, h: Int, color: Color): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        // Noise, so different sizes cannot compress to identical bytes.
        g.color = Color.WHITE
        for (i in 0 until minOf(w, h) step 7) g.drawLine(0, i, w, i + 3)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", out)
        return out.toByteArray()
    }

    private fun png(w: Int, h: Int): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = Color(20, 120, 200, 255)
        g.fillRect(0, 0, w, h)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    companion object {
        const val ASSETS = "../../android/lj2pdf/app/src/main/assets"
        const val PAGE = "https://blog.example/post/1"

        const val STORY_URL = "https://mbasic.facebook.com/story.php?story_fbid=111&id=4"
        // As mbasic actually links them: the album context rides along, and the
        // resolver keeps it, so these are the URLs it will ask for.
        const val PHOTO_PAGE = "https://mbasic.facebook.com/photo.php?fbid=777&set=a.5&type=3"
        const val PHOTO_PAGE_2 = "https://mbasic.facebook.com/photo.php?fbid=778&set=a.5&type=3"
        const val THUMB_URL = "https://scontent.xx.fbcdn.net/v/s320x320/777_thumb.jpg"
        const val FULL_URL =
            "https://mbasic.facebook.com/photo/view_full_size/?fbid=777&ref_component=mbasic_photo_permalink"
        const val FULL_URL_2 =
            "https://mbasic.facebook.com/photo/view_full_size/?fbid=778&ref_component=mbasic_photo_permalink"
        const val AVATAR_URL = "https://scontent.xx.fbcdn.net/v/t1/avatar.jpg"
        const val EMOJI_URL = "https://static.xx.fbcdn.net/rsrc.php/v3/ye/emoji.png"

        /** A story: an avatar, a linked photo, an emoji, and a second photo by link only. */
        val STORY_HTML = """
            <html><body>
              <div id="m_story_permalink_view">
                <h3><a href="/zuck"><img src="$AVATAR_URL"></a></h3>
                <a href="/photo.php?fbid=777&amp;set=a.5&amp;type=3">
                  <img src="$THUMB_URL" alt="Отпуск, день 1">
                </a>
                <img src="$EMOJI_URL">
                <a href="/photo.php?fbid=778&amp;set=a.5&amp;type=3">Ещё 1 фото</a>
              </div>
            </body></html>
        """.trimIndent()

        val PHOTO_PAGE_HTML = """
            <html><body>
              <img src="https://scontent.xx.fbcdn.net/v/p480x480/777_medium.jpg">
              <a href="/photo/view_full_size/?fbid=777&amp;ref_component=mbasic_photo_permalink">
                Открыть оригинал</a>
            </body></html>
        """.trimIndent()

        val PHOTO_PAGE_2_HTML = """
            <html><body>
              <a href="/photo/view_full_size/?fbid=778&amp;ref_component=mbasic_photo_permalink">
                Открыть оригинал</a>
            </body></html>
        """.trimIndent()
    }
}
