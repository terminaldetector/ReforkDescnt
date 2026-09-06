package android.graphics

import java.io.OutputStream

/**
 * Test-harness stand-ins for the bitmap downscaler HtmlArchiver uses when the
 * user caps image size. Decoding always reports "no bitmap", which is the
 * documented failure path — the archiver then stores the original bytes.
 */
class Bitmap internal constructor(val width: Int, val height: Int) {
    enum class CompressFormat { JPEG, PNG, WEBP }
    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean = false

    companion object {
        @JvmStatic
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap =
            Bitmap(w, h)
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
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? = null
}
