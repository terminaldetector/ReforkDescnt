package android.content

import java.io.File
import java.io.InputStream

/**
 * Test-harness stand-in for android.content.Context: only the two things the
 * archiver asks of it — where its private files live, and the asset stream the
 * PDF book's Cyrillic font is loaded from.
 */
open class Context(private val filesRoot: File, private val assetsRoot: File) {
    fun getExternalFilesDir(type: String?): File? = filesRoot.apply { mkdirs() }
    val assets: AssetManager get() = AssetManager(assetsRoot)
}

class AssetManager(private val root: File) {
    fun open(name: String): InputStream = File(root, name).inputStream()
}
