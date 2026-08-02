package app.odyssey.engine

import java.io.File

actual class MediaStore actual constructor() {

    private val dir: File = File(System.getProperty("java.io.tmpdir"), "my-odyssey/media")
        .apply { mkdirs() }

    private var excluded = false

    private fun fileFor(mediaId: String) = File(dir, "$mediaId.blob")

    actual fun put(bytes: ByteArray): String {
        val id = mediaIdOf(bytes)
        val f = fileFor(id)
        if (!f.exists()) f.writeBytes(bytes)
        return id
    }

    actual fun exists(mediaId: String): Boolean = fileFor(mediaId).exists()

    actual fun read(mediaId: String): ByteArray? {
        val f = fileFor(mediaId)
        return if (f.exists()) f.readBytes() else null
    }

    actual fun delete(mediaId: String): Boolean = fileFor(mediaId).delete()

    actual fun totalBytes(): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0L

    actual fun count(): Int = dir.listFiles()?.size ?: 0

    actual fun setExcludedFromBackup(excluded: Boolean) {
        this.excluded = excluded
    }

    actual fun isExcludedFromBackup(): Boolean = excluded
}
