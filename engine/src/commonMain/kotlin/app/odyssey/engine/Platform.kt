package app.odyssey.engine

import kotlin.random.Random

/** Wall clock, in epoch seconds. */
expect fun nowEpochSeconds(): Long

/**
 * The narrowest persistence surface the app needs: an opaque blob keyed by
 * name. Deliberately swappable — the whole storage layer can move to SQLDelight
 * without any caller changing, because callers only ever hand it the encoded
 * log.
 */
expect class KeyValueStore() {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

private const val HEX = "0123456789abcdef"

/** Event ids are opaque and client-generated; the ledger dedupes on them. */
fun newEventId(prefix: String): String {
    val sb = StringBuilder(prefix.length + 17)
    sb.append(prefix).append('-')
    repeat(16) { sb.append(HEX[Random.nextInt(16)]) }
    return sb.toString()
}
