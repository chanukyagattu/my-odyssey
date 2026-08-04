package app.odyssey.engine

enum class ActivityKind { VERIFIED, RECORDED, UPGRADED, REVOKED, MEDIA_ADDED, MEDIA_REMOVED }

/**
 * One line of the FEED — your own history, in plain language.
 *
 * There is no server, so there is no feed of other people. What there is
 * instead is a readable rendering of your own ledger, which turns out to be the
 * honest version of the same idea: the app cannot show you a story it did not
 * witness.
 */
data class ActivityEntry(
    /** Position in the append-only log. */
    val index: Int,
    val kind: ActivityKind,
    val headline: String,
    val detail: String,
    /** Only visits carry a wall clock; compensating events do not. */
    val dateLabel: String?,
)

/**
 * Ordered by log position, newest first — not by timestamp.
 *
 * Only [VisitRecorded] carries a wall clock. Revocations, upgrades and
 * attachments record *that* they happened, not when, so append order is the
 * only ordering the ledger can honestly support. It is also the order in which
 * the fold consumed them.
 */
fun activityFeed(snapshot: AppSnapshot): List<ActivityEntry> {
    val events = snapshot.events
    val byId = events.associateBy { it.eventId }
    val credited = snapshot.result.placesCredited

    fun placeOf(eventId: String?): CanonEntry? {
        var current = eventId?.let { byId[it] }
        // Walk back to the visit: media detach -> attach -> visit.
        repeat(3) {
            when (val e = current) {
                is VisitRecorded -> return snapshot.canon.byId[e.placeId]
                is VisitRevoked -> current = byId[e.refEventId]
                is EvidenceUpgraded -> current = byId[e.refEventId]
                is MediaAttached -> current = byId[e.refEventId]
                is MediaDetached -> current = byId[e.refEventId]
                else -> return null
            }
        }
        return null
    }

    fun where(entry: CanonEntry?): String =
        entry?.let { "${it.name} · ${snapshot.canon.regionName(it.regionCode)}" } ?: "an unknown place"

    return events.mapIndexed { index, event ->
        when (event) {
            is VisitRecorded -> {
                val entry = snapshot.canon.byId[event.placeId]
                val counts = event.placeId in credited
                ActivityEntry(
                    index = index,
                    kind = if (counts) ActivityKind.VERIFIED else ActivityKind.RECORDED,
                    headline = if (counts) {
                        "Verified ${entry?.name ?: event.placeId}"
                    } else {
                        "Recorded ${entry?.name ?: event.placeId}"
                    },
                    detail = "${snapshot.canon.regionName(entry?.regionCode ?: "")} · " +
                        "${event.dwellSeconds / 60} min · ${snapshot.effectiveEvidence(event).label}" +
                        if (counts) "" else " · does not count",
                    dateLabel = formatDate(event.startEpochSec),
                )
            }

            is EvidenceUpgraded -> ActivityEntry(
                index = index,
                kind = ActivityKind.UPGRADED,
                headline = "Evidence upgraded to ${event.newEvidence.label}",
                detail = where(placeOf(event.refEventId)),
                dateLabel = null,
            )

            is VisitRevoked -> ActivityEntry(
                index = index,
                kind = ActivityKind.REVOKED,
                headline = "Visit revoked",
                detail = "${where(placeOf(event.refEventId))} · ${event.reason}",
                dateLabel = null,
            )

            is MediaAttached -> ActivityEntry(
                index = index,
                kind = ActivityKind.MEDIA_ADDED,
                headline = if (event.kind == MediaKind.PHOTO) "Photo added" else "Video added",
                detail = "${where(placeOf(event.refEventId))} · ${formatBytes(event.byteSize)} · stays on this device",
                dateLabel = null,
            )

            is MediaDetached -> ActivityEntry(
                index = index,
                kind = ActivityKind.MEDIA_REMOVED,
                headline = "Media removed",
                detail = "${where(placeOf(event.refEventId))} · bytes reclaimed, event kept",
                dateLabel = null,
            )
        }
    }.reversed()
}
