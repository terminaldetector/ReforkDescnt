import android.content.Context
import com.drmd.lj2pdf.BookBuilder
import com.drmd.lj2pdf.ConvertBus
import com.drmd.lj2pdf.EpubBuilder
import com.drmd.lj2pdf.Facebook
import com.drmd.lj2pdf.HtmlArchiver
import com.drmd.lj2pdf.Http
import com.drmd.lj2pdf.Project
import com.drmd.lj2pdf.Projects
import com.drmd.lj2pdf.RagExporter
import com.drmd.lj2pdf.SiteScan
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end check of the archiving pipeline off-device: scan → HTML base →
 * EPUB / RAG / merged PDF book. Runs the app's real HtmlArchiver, EpubBuilder,
 * RagExporter and BookBuilder against served fixtures.
 */
class PipelineTest {

    private lateinit var work: File
    private lateinit var ctx: Context

    @BeforeTest fun setUp() {
        Http.reset(); ConvertBus.reset()
        work = File(System.getProperty("java.io.tmpdir"), "lj2pdf-test-${System.nanoTime()}")
        work.mkdirs()
        ctx = Context(File(work, "files"), File(ASSETS))
    }

    @AfterTest fun tearDown() {
        work.deleteRecursively()
        Http.reset(); ConvertBus.reset()
    }

    // ================= 1. page content extraction =========================

    @Test fun facebookStoriesLandInTheHtmlBaseWithTextAndImages() = runBlocking {
        serveFacebook()
        val base = "https://www.facebook.com/zuck"
        val project = Projects.forBase(ctx, base)

        val permalinks = SiteScan.scanAll(base, 100, 8) {}
        assertEquals(2, permalinks.size, "scan: $permalinks")

        val entries = HtmlArchiver.download(project, permalinks, parallelism = 2)
        assertEquals(2, entries.size, ConvertBus.logLines.toString())

        val first = entries.first { it.id == "fb111" }
        // Title is built from author · date · opening words (a story has no <h1>).
        assertTrue(first.title.startsWith("Mark Zuckerberg"), first.title)
        assertTrue(first.title.contains("Сегодня мы запускаем"), first.title)

        val saved = Jsoup.parse(project.postHtml("fb111"), "UTF-8")
        val text = saved.body().text()
        assertTrue(text.contains("Сегодня мы запускаем большое обновление"), text)
        // Facebook chrome and the "like/comment" action rows are gone…
        assertFalse(text.contains("Спонсируемая реклама"), text)
        assertFalse(text.contains("Войти в аккаунт"), text)
        // …and the image is local, not a remote scontent URL.
        val img = saved.selectFirst("img")
        assertNotNull(img)
        assertEquals("img/fb111/0.jpg", img.attr("src"))
        assertTrue(File(project.imgDir("fb111"), "0.jpg").length() > 0)
        assertTrue(saved.selectFirst("title")!!.text().startsWith("Mark Zuckerberg"))
    }

    @Test fun liveJournalStillExtractsItsArticleAndTitle() = runBlocking {
        val url = "https://technolirik.livejournal.com/12345.html"
        Http.pages[url] = LJ_POST
        Http.blobs["https://ic.pics.livejournal.com/pic.jpg"] = JPEG

        val project = Projects.forBase(ctx, "https://technolirik.livejournal.com")
        val entries = HtmlArchiver.download(project, listOf(url), parallelism = 1)

        assertEquals(1, entries.size, ConvertBus.logLines.toString())
        assertEquals("12345", entries[0].id)
        assertEquals("Как я строил сарай", entries[0].title)

        val text = Jsoup.parse(project.postHtml("12345"), "UTF-8").body().text()
        assertTrue(text.contains("Начнём с фундамента"), text)
        assertFalse(text.contains("РЕКЛАМА"), "promo block survived: $text")
    }

    @Test fun anExpiredFacebookSessionIsReportedNotSilentlyEmpty() = runBlocking {
        val url = "https://mbasic.facebook.com/story.php?story_fbid=111&id=4"
        Http.pages[url] = LOGIN_WALL
        val project = Projects.forBase(ctx, "https://www.facebook.com/zuck")

        assertTrue(HtmlArchiver.download(project, listOf(url), parallelism = 1).isEmpty())
        assertTrue(ConvertBus.logLines.any { it.contains("Facebook login page") },
            ConvertBus.logLines.toString())
    }

    // ================= 2. HTML base → EPUB ================================

    @Test fun epubIsAValidBookWithChaptersTocAndImages() = runBlocking {
        serveFacebook()
        val project = archiveFacebook()
        val out = File(work, "book.epub")

        assertTrue(EpubBuilder.build(project, project.entries(), out, fontSize = 18))
        assertTrue(out.length() > 0)

        ZipFile(out).use { zip ->
            // EPUB requires "mimetype" first and STORED.
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(java.util.zip.ZipEntry.STORED.toLong(), first.method.toLong())
            assertEquals("application/epub+zip", zip.getInputStream(first).readBytes().decodeToString())

            for (required in listOf(
                "META-INF/container.xml", "OEBPS/content.opf",
                "OEBPS/nav.xhtml", "OEBPS/toc.ncx", "OEBPS/style.css",
                "OEBPS/chap0001.xhtml", "OEBPS/chap0002.xhtml"
            )) assertNotNull(zip.getEntry(required), "missing $required")

            val chapter = zip.getInputStream(zip.getEntry("OEBPS/chap0001.xhtml")).readBytes().decodeToString()
            assertTrue(chapter.contains("Сегодня мы запускаем"), chapter.take(400))
            assertTrue(chapter.contains("img/fb111/0.jpg"), "chapter lost its image")

            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue(opf.contains("""href="chap0001.xhtml""""), opf.take(600))
            assertTrue(opf.contains("""href="img/fb111/0.jpg""""), "image not in the manifest")
            assertNotNull(zip.getEntry("OEBPS/img/fb111/0.jpg"), "image bytes not packed")

            val nav = zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml")).readBytes().decodeToString()
            assertTrue(nav.contains("Mark Zuckerberg"), nav.take(600))
        }
    }

    // ================= 3. HTML base → RAG corpus ==========================

    @Test fun ragCorpusIsOneJsonRecordPerChunkOfRealText() = runBlocking {
        serveFacebook()
        val project = archiveFacebook()
        val out = File(work, "corpus.jsonl")

        val docs = RagExporter.export(listOf(project), out, chunkSize = 200, overlap = 40) { _, _, _ -> }

        assertEquals(2, docs)
        val lines = out.readLines().filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty())
        var sawStory = false
        for (line in lines) {
            val o = JSONObject(line)
            for (key in listOf("source", "id", "title", "url", "chunk", "text"))
                assertTrue(o.has(key), "record without «$key»: $line")
            assertTrue(o.getString("text").isNotBlank())
            assertTrue(o.getString("text").length <= 200, "chunk over the limit: $line")
            if (o.getString("text").contains("Сегодня мы запускаем")) sawStory = true
            assertTrue(o.getString("url").startsWith("https://mbasic.facebook.com/"))
        }
        assertTrue(sawStory, "the post text never made it into the corpus")
    }

    @Test fun mempalaceEngineWritesTheSpatialHierarchy() = runBlocking {
        serveFacebook()
        val project = archiveFacebook()
        val out = File(work, "palace.jsonl")

        RagExporter.export(listOf(project), out, 1000, 150, "mempalace") { _, _, _ -> }

        val o = JSONObject(out.readLines().first { it.isNotBlank() })
        for (key in listOf("wing", "hall", "room", "drawer", "url", "text"))
            assertTrue(o.has(key), "record without «$key»: $o")
        assertEquals("facebook.com_zuck", o.getString("wing"))
    }

    @Test fun chunkingOverlapsAndCoversTheWholeText() {
        val text = (1..400).joinToString(" ") { "слово$it" }
        val chunks = RagExporter.chunk(text, 300, 60)

        assertTrue(chunks.size > 1)
        for (c in chunks) assertTrue(c.length <= 300, "chunk of ${c.length}")
        // Nothing is dropped: every word survives somewhere.
        val joined = chunks.joinToString(" ")
        for (w in listOf("слово1", "слово200", "слово400"))
            assertTrue(joined.contains(w), "lost $w")
        // Consecutive chunks really do overlap.
        val tail = chunks[0].takeLast(30)
        assertTrue(chunks[1].contains(tail.trim().substringAfter(' ')), "no overlap between chunks")
    }

    // ================= 4. per-post PDFs → book.pdf =========================

    @Test fun bookPdfMergesChaptersAndGetsAClickableCyrillicToc() {
        val project = Project(File(work, "projects/blog"))
        project.dir.mkdirs()
        val titles = listOf("Как я строил сарай", "Mark Zuckerberg · 12 марта 2024 г.")
        // The chapter bodies stay ASCII (PDType1Font.HELVETICA cannot encode
        // Cyrillic); the Cyrillic titles are what BookBuilder must render, and
        // it embeds DejaVu for exactly that.
        val parts = titles.indices.map { i ->
            makePdf(File(project.postsDir, "p$i.pdf"), "Chapter ${i + 1} body", pages = i + 1)
        }
        val out = File(project.dir, "book.pdf")

        assertTrue(BookBuilder.mergeWithToc(ctx, parts, titles, out))

        PDDocument.load(out).use { doc ->
            // 1 TOC page + 1 + 2 content pages.
            assertEquals(4, doc.numberOfPages)

            val outline = doc.documentCatalog.documentOutline
            assertNotNull(outline, "no bookmark outline")
            val bookmarks = generateSequence(outline.firstChild) { it.nextSibling }.map { it.title }.toList()
            assertEquals(titles, bookmarks)

            // The TOC page carries the (Cyrillic) titles as real text…
            val toc = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue(toc.contains("Содержание"), toc)
            for (t in titles) assertTrue(toc.contains(t.take(20)), "TOC missing «$t»:\n$toc")

            // …and each row is a link annotation jumping into the book.
            assertEquals(titles.size, doc.getPage(0).annotations.size)

            // Chapter text survived the merge.
            val all = PDFTextStripper().getText(doc)
            assertTrue(all.contains("Chapter 1"), all)
            assertTrue(all.contains("Chapter 2"), all)
        }
    }

    @Test fun mergeReportsFailureInsteadOfWritingAnEmptyBook() {
        val project = Project(File(work, "projects/empty"))
        project.dir.mkdirs()
        val missing = File(project.postsDir, "nope.pdf")
        assertFalse(BookBuilder.mergeWithToc(ctx, listOf(missing), listOf("x"), File(project.dir, "book.pdf")))
    }

    // ================= fixtures ===========================================

    private fun serveFacebook() {
        Http.pages[FEED_URL] = FB_FEED
        Http.pages[STORY_1_URL] = FB_STORY_1
        Http.pages[STORY_2_URL] = FB_STORY_2
        Http.blobs["https://scontent.xx.fbcdn.net/v/photo1.jpg"] = JPEG
    }

    private suspend fun archiveFacebook(): Project {
        val base = "https://www.facebook.com/zuck"
        val project = Projects.forBase(ctx, base)
        val permalinks = SiteScan.scanAll(base, 100, 8) {}
        project.saveEntries(HtmlArchiver.download(project, permalinks, parallelism = 2))
        return project
    }

    /** Stands in for what PdfRenderer2 writes on the device: a text PDF. */
    private fun makePdf(file: File, text: String, pages: Int): File {
        file.parentFile?.mkdirs()
        PDDocument().use { doc ->
            repeat(pages) { p ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(60f, 760f)
                    cs.showText("$text (page ${p + 1})")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    companion object {
        /** The app's real bundled Cyrillic fonts, so the TOC test is honest. */
        const val ASSETS = "../../android/lj2pdf/app/src/main/assets"

        const val FEED_URL = "https://mbasic.facebook.com/zuck?v=timeline"
        const val STORY_1_URL = "https://mbasic.facebook.com/story.php?story_fbid=111&id=4"
        const val STORY_2_URL = "https://mbasic.facebook.com/story.php?story_fbid=222&id=4"

        val FB_FEED = """
            <html><body>
              <div id="header"><a href="/login/">Войти в аккаунт</a></div>
              <div data-ft='{"top_level_post_id":"111"}'>
                <a href="/story.php?story_fbid=111&amp;id=4&amp;__tn__=-R">Full story</a>
              </div>
              <div data-ft='{"top_level_post_id":"222"}'>
                <a href="/story.php?story_fbid=222&amp;id=4">Full story</a>
              </div>
            </body></html>
        """.trimIndent()

        val FB_STORY_1 = """
            <html><head><title>Facebook</title></head><body>
              <div id="header"><a href="/login/">Войти в аккаунт</a></div>
              <div id="m_story_permalink_view">
                <h3><a href="/zuck">Mark Zuckerberg</a></h3>
                <abbr>12 марта 2024 г.</abbr>
                <div>Сегодня мы запускаем большое обновление, о котором давно просили.
                     Оно меняет то, как работает лента и уведомления.</div>
                <img src="https://scontent.xx.fbcdn.net/v/photo1.jpg" alt="photo"/>
                <div class="mbasic_ads">Спонсируемая реклама</div>
                <form action="/a/comment.php?fbid=111"><input name="comment"></form>
              </div>
              <div id="footer"><a href="/reg/">Регистрация</a></div>
            </body></html>
        """.trimIndent()

        val FB_STORY_2 = """
            <html><head><title>Facebook</title></head><body>
              <div id="m_story_permalink_view">
                <h3><a href="/zuck">Mark Zuckerberg</a></h3>
                <abbr>3 апреля 2024 г.</abbr>
                <div>Второй пост про то, как мы измеряем задержку на слабых сетях
                     и почему это важно для пользователей.</div>
              </div>
            </body></html>
        """.trimIndent()

        val LOGIN_WALL = """
            <html><body><form method="post" action="/login/device-based/regular/login/">
            <input name="email"><input type="password" name="pass"></form></body></html>
        """.trimIndent()

        val LJ_POST = """
            <html><head><title>Как я строил сарай - technolirik</title></head><body>
              <h1 class="entry-title">Как я строил сарай</h1>
              <div class="b-singlepost-body">
                <p>Начнём с фундамента: он должен быть ровным.</p>
                <img src="https://ic.pics.livejournal.com/pic.jpg"/>
                <div class="lj-promo">РЕКЛАМА</div>
                <ins class="adsbygoogle">РЕКЛАМА</ins>
              </div>
            </body></html>
        """.trimIndent()

        /** Smallest bytes that pass the archiver's "not a tracking pixel" check. */
        val JPEG = ByteArray(256) { (it % 251).toByte() }
    }
}
