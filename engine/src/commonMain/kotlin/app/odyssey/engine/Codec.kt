package app.odyssey.engine

/**
 * Line-oriented codec for the append-only log.
 *
 * One event per line, pipe-delimited, with a leading schema version. Chosen
 * over a serialization framework on purpose: the on-disk log is the system of
 * record, so its format should be readable by eye, diffable, appendable
 * without rewriting, and free of any dependency that could change encoding
 * between releases.
 *
 *   V|eventId|userId|placeId|startSec|endSec|evidence|deviceId|sourceSeq
 *   R|eventId|refEventId|reason
 *   U|eventId|refEventId|newEvidence
 */
object Codec {

    const val SCHEMA_VERSION = 1
    const val HEADER = "# my-odyssey ledger v$SCHEMA_VERSION"

    fun encode(event: LedgerEvent): String = when (event) {
        is VisitRecorded -> listOf(
            "V",
            event.eventId,
            event.userId,
            event.placeId,
            event.startEpochSec.toString(),
            event.endEpochSec.toString(),
            event.evidence.name,
            event.deviceId ?: "",
            event.sourceSeq?.toString() ?: "",
        ).joinToString("|") { esc(it) }

        is VisitRevoked -> listOf(
            "R",
            event.eventId,
            event.refEventId,
            event.reason,
        ).joinToString("|") { esc(it) }

        is EvidenceUpgraded -> listOf(
            "U",
            event.eventId,
            event.refEventId,
            event.newEvidence.name,
        ).joinToString("|") { esc(it) }

        is MediaAttached -> listOf(
            "M",
            event.eventId,
            event.refEventId,
            event.mediaId,
            event.kind.name,
            event.byteSize.toString(),
            event.exifLatE7?.toString() ?: "",
            event.exifLngE7?.toString() ?: "",
            event.exifUtcEpochSeconds?.toString() ?: "",
        ).joinToString("|") { esc(it) }

        is MediaDetached -> listOf(
            "X",
            event.eventId,
            event.refEventId,
            event.reason,
        ).joinToString("|") { esc(it) }
    }

    fun decode(line: String): LedgerEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val f = trimmed.split("|").map { unesc(it) }
        return try {
            when (f[0]) {
                "V" -> VisitRecorded(
                    eventId = f[1],
                    userId = f[2],
                    placeId = f[3],
                    startEpochSec = f[4].toLong(),
                    endEpochSec = f[5].toLong(),
                    evidence = Evidence.valueOf(f[6]),
                    deviceId = f.getOrNull(7)?.ifEmpty { null },
                    sourceSeq = f.getOrNull(8)?.ifEmpty { null }?.toLong(),
                )

                "R" -> VisitRevoked(eventId = f[1], refEventId = f[2], reason = f[3])

                "U" -> EvidenceUpgraded(
                    eventId = f[1],
                    refEventId = f[2],
                    newEvidence = Evidence.valueOf(f[3]),
                )

                "M" -> MediaAttached(
                    eventId = f[1],
                    refEventId = f[2],
                    mediaId = f[3],
                    kind = MediaKind.valueOf(f[4]),
                    byteSize = f[5].toLong(),
                    exifLatE7 = f[6].ifEmpty { null }?.toLong(),
                    exifLngE7 = f[7].ifEmpty { null }?.toLong(),
                    exifUtcEpochSeconds = f[8].ifEmpty { null }?.toLong(),
                )

                "X" -> MediaDetached(eventId = f[1], refEventId = f[2], reason = f[3])

                else -> null
            }
        } catch (_: Exception) {
            // A corrupt line is dropped, never fatal: a partially written tail
            // must not brick the app. Everything before it still folds.
            null
        }
    }

    fun encodeAll(events: List<LedgerEvent>): String =
        (listOf(HEADER) + events.map { encode(it) }).joinToString("\n")

    fun decodeAll(text: String): List<LedgerEvent> =
        text.split("\n").mapNotNull { decode(it) }

    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")

    private fun unesc(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { out.append('\\'); i += 2 }
                    'p' -> { out.append('|'); i += 2 }
                    'n' -> { out.append('\n'); i += 2 }
                    else -> { out.append(c); i += 1 }
                }
            } else {
                out.append(c)
                i += 1
            }
        }
        return out.toString()
    }
}
