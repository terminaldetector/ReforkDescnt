package com.drmd.lj2pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Turns a harvested gallery into something you can actually read:
 *
 *  * **PDF album** — one picture per page, the page cut to the picture's own
 *    shape so nothing is letterboxed or cropped, a bookmark per picture, and an
 *    optional caption strip underneath.
 *  * **CBZ** — the pictures in order inside a zip, which every comic and image
 *    reader opens. For a photo blog this is usually the nicer artefact: no
 *    re-encoding, no page geometry, just the originals.
 *
 * PDFBox can embed a JPEG as-is; anything else is transcoded once through
 * [ImageCodec] so the album stays a single self-contained file.
 */
object AlbumBuilder {

    /** Cap on a page's side in points, so one huge photo cannot blow up memory. */
    private const val MAX_SIDE_PT = 2200f
    private const val CAPTION_H = 26f
    private const val CAPTION_SIZE = 8f
    private const val JPEG_QUALITY = 88

    // ---- PDF -------------------------------------------------------------

    /**
     * Build [out] from [shots]. Returns true when at least one picture made it
     * in. [captions] adds the picture's caption and source under each image.
     */
    fun buildPdf(
        ctx: Context, project: Project, shots: List<ImageArchiver.Shot>, out: File,
        captions: Boolean = true,
        progress: (done: Int, total: Int, status: String) -> Unit = { _, _, _ -> }
    ): Boolean {
        if (shots.isEmpty()) return false
        out.parentFile?.mkdirs()
        var added = 0

        PDDocument().use { doc ->
            val font: PDFont = loadFont(ctx, doc) ?: PDType1Font.HELVETICA
            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline

            for ((i, shot) in shots.withIndex()) {
                if (ConvertBus.cancelRequested) break
                progress(i + 1, shots.size, "Album: ${i + 1}/${shots.size}")
                val file = File(project.galleryDir, shot.name)
                if (!file.exists() || file.length() == 0L) continue

                val jpeg = ImageCodec.toJpeg(file.readBytes(), JPEG_QUALITY)
                if (jpeg == null) {
                    ConvertBus.log("[album] ${shot.name}: cannot decode — skipped"); continue
                }
                try {
                    val image = JPEGFactory.createFromStream(doc, ByteArrayInputStream(jpeg))
                    val capH = if (captions) CAPTION_H else 0f
                    val (w, h) = fit(image.width.toFloat(), image.height.toFloat())
                    val page = PDPage(PDRectangle(w, h + capH))
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(image, 0f, capH, w, h)
                        if (captions) caption(cs, font, shot, w)
                    }
                    outline.addLast(PDOutlineItem().apply {
                        title = shot.caption.ifBlank { shot.name }
                        destination = PDPageFitWidthDestination().apply { setPage(page) }
                    })
                    added++
                } catch (t: Throwable) {
                    ConvertBus.log("[album] ${shot.name}: ${t.message}")
                }
            }
            if (added == 0) return false
            outline.openNode()
            doc.save(out)
        }
        ConvertBus.log("[album] $added picture(s) → ${out.name} (${out.length() / 1024} KB)")
        return out.length() > 0
    }

    /** Page geometry: the picture's own aspect, bounded so pages stay sane. */
    private fun fit(w: Float, h: Float): Pair<Float, Float> {
        if (w <= 0f || h <= 0f) return PDRectangle.A4.width to PDRectangle.A4.height
        val scale = minOf(1f, MAX_SIDE_PT / maxOf(w, h))
        return (w * scale).coerceAtLeast(1f) to (h * scale).coerceAtLeast(1f)
    }

    private fun caption(cs: PDPageContentStream, font: PDFont, shot: ImageArchiver.Shot, w: Float) {
        val line = listOfNotNull(
            shot.caption.ifBlank { null },
            shot.source.ifBlank { null }
        ).joinToString("  ·  ")
        if (line.isBlank()) return
        try {
            cs.beginText()
            cs.setFont(font, CAPTION_SIZE)
            cs.setNonStrokingColor(0.35f, 0.35f, 0.35f)
            cs.newLineAtOffset(8f, CAPTION_H / 2 - CAPTION_SIZE / 2)
            cs.showText(truncate(font, line, CAPTION_SIZE, w - 16f))
            cs.endText()
        } catch (_: Throwable) { /* a caption is never worth losing the page for */ }
    }

    private fun truncate(font: PDFont, s: String, size: Float, maxW: Float): String {
        fun width(t: String) = try { font.getStringWidth(t) / 1000f * size }
                               catch (_: Throwable) { t.length * size * 0.5f }
        if (width(s) <= maxW) return s
        var str = s
        while (str.isNotEmpty() && width("$str…") > maxW) str = str.dropLast(1)
        return "$str…"
    }

    /** The bundled Cyrillic face — PDFBox's built-ins cannot encode Cyrillic. */
    private fun loadFont(ctx: Context, doc: PDDocument): PDFont? = try {
        ctx.assets.open("DejaVuSans.ttf").use { PDType0Font.load(doc, it, true) }
    } catch (_: Throwable) { null }

    // ---- CBZ -------------------------------------------------------------

    /**
     * The gallery as a comic-book archive: the original files, in order, no
     * re-encoding. Stored (not deflated) — photos are already compressed, so
     * deflate only costs time.
     */
    fun buildCbz(
        project: Project, shots: List<ImageArchiver.Shot>, out: File,
        progress: (done: Int, total: Int, status: String) -> Unit = { _, _, _ -> }
    ): Boolean {
        if (shots.isEmpty()) return false
        out.parentFile?.mkdirs()
        var added = 0
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            for ((i, shot) in shots.withIndex()) {
                if (ConvertBus.cancelRequested) break
                progress(i + 1, shots.size, "CBZ: ${i + 1}/${shots.size}")
                val file = File(project.galleryDir, shot.name)
                if (!file.exists() || file.length() == 0L) continue
                val data = file.readBytes()
                val entry = ZipEntry(shot.name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                }
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
                added++
            }
            // A plain listing so the archive says what it holds even outside a reader.
            if (added > 0) writeListing(zos, project, shots)
        }
        if (added == 0) { out.delete(); return false }
        ConvertBus.log("[album] $added picture(s) → ${out.name} (${out.length() / 1024} KB)")
        return out.length() > 0
    }

    private fun writeListing(zos: ZipOutputStream, project: Project, shots: List<ImageArchiver.Shot>) {
        val text = buildString {
            append(project.name).append('\n')
            append(shots.size).append(" picture(s)\n\n")
            for (s in shots) {
                append(s.name)
                if (s.width > 0) append("  ${s.width}×${s.height}")
                if (s.caption.isNotBlank()) append("  ").append(s.caption)
                if (s.source.isNotBlank()) append("\n    ").append(s.source)
                append('\n')
            }
        }.toByteArray()
        val entry = ZipEntry("000_index.txt").apply {
            method = ZipEntry.STORED
            size = text.size.toLong()
            compressedSize = text.size.toLong()
            crc = CRC32().apply { update(text) }.value
        }
        zos.putNextEntry(entry)
        zos.write(text)
        zos.closeEntry()
    }
}
