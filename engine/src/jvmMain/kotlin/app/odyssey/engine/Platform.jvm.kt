package app.odyssey.engine

import java.io.File

actual fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L

actual class KeyValueStore actual constructor() {

    private val dir: File = File(System.getProperty("java.io.tmpdir"), "my-odyssey").apply { mkdirs() }

    private fun fileFor(key: String) = File(dir, "$key.log")

    actual fun read(key: String): String? {
        val f = fileFor(key)
        return if (f.exists()) f.readText() else null
    }

    actual fun write(key: String, value: String) {
        fileFor(key).writeText(value)
    }

    actual fun remove(key: String) {
        fileFor(key).delete()
    }
}
