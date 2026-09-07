package com.drmd.lj2pdf

import android.graphics.BitmapFactory
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Image mode: archive the pictures rather than the prose.
 *
 * Plenty of blogs — Facebook photo pages above all — carry all of their value
 * in the images; the surrounding text is a date and a shrug. This walks the
 * posts the scanner found and pulls out **the pictures at full size**, drops
 * the interface junk that also arrives as `<img>`, throws away duplicates, and
 * leaves a numbered gallery plus an index that [AlbumBuilder] turns into a PDF
 * album or a CBZ.
 *
 * Two things make it more than "download every img":
 *
 *  * **Originals, not previews.** A thumbnail usually links to the real file —
 *    on Facebook a `/photo.php?fbid=…` page, elsewhere a plain `.jpg` href.
 *    The link is followed, so what lands on disk is the picture, not a 320px
 *    preview. See [Facebook.fullSizeImage].
 *  * **Duplicates cost nothing.** The same photo shows up in a feed, in the
 *    post and in an album; identical bytes are stored once.
 */
object ImageArchiver {

    /** One archived picture. */
    data class Shot(
        val name: String,       // file name inside the gallery folder
        val source: String,     // page it came from
        val width: Int,         // 0 when the decoder could not say
        val height: Int,
        val bytes: Long,
        val caption: String,
        /** Content hash — what makes a re-run add only genuinely new pictures. */
        val hash: String = ""
    )

    /** What to keep, and how hard to work for it. */
    data class Options(
        /** Follow thumbnails to the original (an extra request per picture). */
        val fullSize: Boolean = true,
        /** Skip anything smaller than this on its longest side (0 = keep all). */
        val minSide: Int = 400,
        /** Skip anything smaller than this many bytes — icons, spacers, 1×1s. */
        val minBytes: Int = 8 * 1024,
        /** Pictures fetched at once. */
        val parallelism: Int = 4,
        /** Stop after this many (0 = no limit). */
        val max: Int = 0
    )

    private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")

    // ---- harvesting ------------------------------------------------------

    /**
     * Pull every picture out of [pages] into the project's gallery folder.
     * Returns the whole gallery — [existing] first, then whatever is new — and
     * writes the index, so the album builders can run later without re-crawling.
     *
     * Passing the previous run's shots as [existing] makes this a top-up: their
     * hashes are already known, so a picture the archive holds is not fetched
     * into it twice, and numbering continues instead of restarting.
     */
    suspend fun harvest(
        project: Project, pages: List<PostEntry>, opts: Options,
        existing: List<Shot> = emptyList(),
        progress: (done: Int, total: Int, status: String) -> Unit = { _, _, _ -> }
    ): List<Shot> {
        val total = pages.size
        val done = AtomicInteger(0)
        val seenHashes = existing.mapNotNull { it.hash.ifBlank { null } }.toHashSet()
        val seenUrls = HashSet<String>()
        val shots = ArrayList<Shot>(existing)
        var index = existing.size
        val before = existing.size

        ConvertBus.log("[img] harvesting pictures from $total page(s)…" +
            if (before > 0) " ($before already saved)" else "")
        for (page in pages) {
            if (ConvertBus.cancelRequested) break
            if (opts.max > 0 && shots.size >= opts.max) break

            val n = done.incrementAndGet()
            progress(n, total, "Pictures: $n/$total (${shots.size} saved)")
            val doc = Http.doc(page.permalink)
            if (doc == null) { ConvertBus.log("[img] ${page.permalink}: page failed"); continue }
            if (Facebook.isFacebook(page.permalink) && Facebook.looksLoggedOut(doc)) {
                ConvertBus.log("[img] Facebook login page — session expired?"); break
            }

            val wanted = candidates(doc, page, opts, seenUrls)
            val fetched = wanted.mapPar(opts.parallelism.coerceIn(1, 8)) { c ->
                if (ConvertBus.cancelRequested) null
                else Http.getBytes(c.url)?.let { c to it }
            }.filterNotNull()

            for ((c, data) in fetched) {
                if (opts.max > 0 && shots.size >= opts.max) break
                val shot = keep(project, index, c, data, opts, seenHashes) ?: continue
                shots.add(shot)
                index++
            }
        }

        saveIndex(project, shots)
        ConvertBus.log("[img] ${shots.size - before} new, ${shots.size} total → " +
            "${project.galleryDir.name}/")
        return shots
    }

    private class Candidate(val url: String, val caption: String, val source: String)

    /**
     * The pictures worth fetching from one page: the original behind each
     * thumbnail where there is one, the thumbnail itself where there is not.
     */
    private suspend fun candidates(
        doc: Document, page: PostEntry, opts: Options, seenUrls: MutableSet<String>
    ): List<Candidate> {
        val fb = Facebook.isFacebook(page.permalink)
        val out = ArrayList<Candidate>()

        for (img in doc.select("img")) {
            val direct = srcOf(img)
            val link = originalLink(img, fb)
            val resolved = link != null && opts.fullSize && fb
            val url = when {
                resolved -> {
                    seenUrls.add(link!!)        // the grid sweep need not redo it
                    Facebook.fullSizeImage(link) ?: direct
                }
                link != null -> link
                else -> direct
            }
            if (url.isNullOrBlank()) continue
            // Facebook serves its interface — avatars, emoji, sprites — from the
            // same CDN as the photographs. Drop those before spending a request
            // on them; a URL we resolved from a photo page is a photo by
            // construction, and an external image is somebody's actual content.
            if (!resolved && fb && Facebook.isFacebookAsset(url) && !Facebook.isPhotoUrl(url)) continue
            if (!seenUrls.add(url)) continue
            out.add(Candidate(url, img.attr("alt").trim().ifBlank { page.title }, page.permalink))
        }

        // Facebook grids link photos without ever inlining a preview; those are
        // exactly the pictures an album archive is after.
        if (fb && opts.fullSize) {
            for (photoPage in Facebook.photoPageLinks(doc)) {
                if (ConvertBus.cancelRequested) break
                if (!seenUrls.add(photoPage)) continue
                val full = Facebook.fullSizeImage(photoPage) ?: continue
                if (!seenUrls.add(full)) continue
                out.add(Candidate(full, page.title, photoPage))
            }
        }
        return out
    }

    /** `src`, or the lazy-loading attributes sites use instead. */
    private fun srcOf(img: Element): String? =
        img.absUrl("src").ifBlank { img.absUrl("data-src") }
            .ifBlank { img.absUrl("data-original") }
            .ifBlank { img.absUrl("data-lazy-src") }
            .ifBlank { null }

    /**
     * The enclosing link when it points at the full picture: a Facebook photo
     * page, or a plain image file — which is how image blogs have linked
     * originals behind thumbnails forever.
     */
    private fun originalLink(img: Element, fb: Boolean): String? {
        var el: Element? = img.parent()
        var hops = 0
        while (el != null && hops < 3) {
            if (el.tagName().equals("a", ignoreCase = true)) {
                val href = el.absUrl("href")
                if (href.isNotBlank()) {
                    if (fb && Facebook.isFacebook(href) && href.contains("fbid=")) return href
                    if (!fb && looksLikeImage(href)) return href
                }
            }
            el = el.parent(); hops++
        }
        return null
    }

    private fun looksLikeImage(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return IMAGE_EXT.any { path.endsWith(it) }
    }

    // ---- storing ---------------------------------------------------------

    /** Filter, de-duplicate and write one picture; null when it is not kept. */
    private fun keep(
        project: Project, index: Int, c: Candidate, data: ByteArray,
        opts: Options, seenHashes: MutableSet<String>
    ): Shot? {
        if (data.size < opts.minBytes) return null
        val (w, h) = dimensions(data)
        // A decoder that could not measure it still gets the benefit of the
        // doubt — better an extra picture than a silently dropped one.
        if (opts.minSide > 0 && w > 0 && h > 0 && maxOf(w, h) < opts.minSide) return null
        val hash = sha256(data)
        if (!seenHashes.add(hash)) return null

        val name = "%05d.%s".format(index + 1, extOf(data, c.url))
        val f = File(project.galleryDir, name)
        return try {
            f.writeBytes(data)
            Shot(name, c.source, w, h, data.size.toLong(), c.caption, hash)
        } catch (t: Throwable) {
            ConvertBus.log("[img] write failed $name: ${t.message}"); null
        }
    }

    private fun dimensions(data: ByteArray): Pair<Int, Int> = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        o.outWidth to o.outHeight
    } catch (_: Throwable) { 0 to 0 }

    /** Format from the bytes themselves — a CDN URL rarely ends in ".jpg". */
    fun extOf(data: ByteArray, url: String = ""): String {
        fun at(i: Int) = if (i < data.size) data[i].toInt() and 0xff else -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "jpg"
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "png"
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "gif"
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "webp"
            at(0) == 0x42 && at(1) == 0x4D -> "bmp"
            else -> IMAGE_EXT.firstOrNull {
                url.substringBefore('?').lowercase().endsWith(it)
            }?.removePrefix(".") ?: "jpg"
        }
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    // ---- index -----------------------------------------------------------

    fun saveIndex(project: Project, shots: List<Shot>) {
        project.dir.mkdirs()
        project.galleryIndex.writeText(shots.joinToString("\n") {
            listOf(
                it.name, it.source, it.width.toString(), it.height.toString(),
                it.bytes.toString(), it.caption.replace('\t', ' ').replace('\n', ' '),
                it.hash
            ).joinToString("\t")
        })
    }

    /**
     * The saved gallery. Falls back to whatever files are in the folder when
     * there is no index, so a half-finished run is still buildable.
     */
    fun loadIndex(project: Project): List<Shot> {
        val f = project.galleryIndex
        if (f.exists() && f.length() > 0) {
            val shots = f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null
                else Shot(
                    name = p[0], source = p[1],
                    width = p[2].toIntOrNull() ?: 0, height = p[3].toIntOrNull() ?: 0,
                    bytes = p[4].toLongOrNull() ?: 0L,
                    caption = if (p.size >= 6) p[5] else "",
                    hash = if (p.size >= 7) p[6] else ""
                )
            }.filter { File(project.galleryDir, it.name).exists() }
            if (shots.isNotEmpty()) return shots
        }
        return project.galleryFiles().map {
            Shot(it.name, "", 0, 0, it.length(), "")
        }
    }
}
