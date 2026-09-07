package com.drmd.lj2pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * The one place that re-encodes a picture.
 *
 * PDFBox can embed a JPEG byte-for-byte but nothing else, so a PNG / GIF /
 * WebP has to become a JPEG before it can go into a PDF album. Everything
 * else in the image path — the gallery folder, the CBZ — keeps the original
 * file untouched, which is why the CBZ is the lossless artefact of the two.
 *
 * Transparency is flattened by JPEG; that only shows on pictures that had an
 * alpha channel to begin with.
 */
object ImageCodec {

    /**
     * JPEG bytes for [data] — the same array back when it already is one, so
     * the common case costs nothing. Null when the picture cannot be decoded.
     */
    fun toJpeg(data: ByteArray, quality: Int = 88): ByteArray? {
        if (isJpeg(data)) return data
        return try {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
            val out = ByteArrayOutputStream(data.size.coerceAtLeast(1024))
            val ok = bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
            bmp.recycle()
            if (ok && out.size() > 0) out.toByteArray() else null
        } catch (t: Throwable) {
            ConvertBus.log("[img] transcode failed: ${t.message}")
            null
        }
    }

    fun isJpeg(data: ByteArray): Boolean =
        data.size > 3 && (data[0].toInt() and 0xff) == 0xFF &&
            (data[1].toInt() and 0xff) == 0xD8 && (data[2].toInt() and 0xff) == 0xFF
}
