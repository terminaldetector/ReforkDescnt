package android.graphics

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * Test-harness stand-ins for the bitmap APIs the archiver uses — backed by
 * ImageIO, so decoding, measuring, downscaling and JPEG transcoding really
 * happen instead of being faked. That is what lets the image-mode checks say
 * anything about the code that runs on a phone.
 */
class Bitmap internal constructor(internal val image: BufferedImage) {
    val width: Int get() = image.width
    val height: Int get() = image.height

    enum class CompressFormat { JPEG, PNG, WEBP }

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean = try {
        val name = if (format == CompressFormat.PNG) "png" else "jpg"
        // JPEG has no alpha: drop it the way Android's encoder does.
        val src = if (name == "jpg" && image.type != BufferedImage.TYPE_INT_RGB) {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also {
                val g = it.createGraphics()
                g.drawImage(image, 0, 0, null)
                g.dispose()
            }
        } else image
        ImageIO.write(src, name, out)
    } catch (_: Throwable) { false }

    fun recycle() { /* the JVM has a garbage collector */ }

    companion object {
        @JvmStatic
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
            val scaled = BufferedImage(w.coerceAtLeast(1), h.coerceAtLeast(1), src.image.type
                .takeIf { it != BufferedImage.TYPE_CUSTOM } ?: BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            if (filter) g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            g.drawImage(src.image, 0, 0, scaled.width, scaled.height, null)
            g.dispose()
            return Bitmap(scaled)
        }
    }
}

object BitmapFactory {
    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var outWidth: Int = 0
        var outHeight: Int = 0
    }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
        decodeByteArray(data, offset, length, null)

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        val img = try {
            ImageIO.read(ByteArrayInputStream(data, offset, length))
        } catch (_: Throwable) { null } ?: return null

        if (opts != null) { opts.outWidth = img.width; opts.outHeight = img.height }
        // Bounds-only decoding reports the size and returns nothing, as on Android.
        if (opts?.inJustDecodeBounds == true) return null

        val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
        if (sample == 1) return Bitmap(img)
        return Bitmap.createScaledBitmap(
            Bitmap(img), img.width / sample, img.height / sample, true)
    }
}
