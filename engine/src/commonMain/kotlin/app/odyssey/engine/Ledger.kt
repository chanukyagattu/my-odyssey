package app.odyssey.engine

import kotlin.math.roundToLong

sealed interface IngestResult {
    data object Accepted : IngestResult
    data object DuplicateNoOp : IngestResult
    data class Rejected(val reason: String) : IngestResult

    val isAccepted: Boolean get() = this is Accepted
    val message: String?
        get() = when (this) {
            is Rejected -> reason
            is DuplicateNoOp -> "Already recorded — no-op"
            is Accepted -> null
        }
}

/**
 * Append-only ledger. Ingest is idempotent (by eventId and by producer
 * idempotency key) and enforces the plausibility invariants at the door, so
 * the ledger never contains a physically impossible history.
 *
 * The store is intentionally dumb: it appends and it refuses. Every number the
 * app shows comes from [fold], never from state kept in here.
 */
class InMemoryLedger(
    private val maxSpeedMps: Double = 344.0,
    private val overlapToleranceSec: Long = 120,
) {
    private val log = mutableListOf<LedgerEvent>()
    private val eventIds = mutableSetOf<String>()
    private val sourceKeys = mutableSetOf<Triple<String, String, Long>>()

    fun events(): List<LedgerEvent> = log.toList()

    fun size(): Int = log.size

    fun ingest(event: LedgerEvent, canon: CanonRelease): IngestResult {
        if (event.eventId in eventIds) return IngestResult.DuplicateNoOp

        when (event) {
            is VisitRecorded -> {
                val key = if (event.deviceId != null && event.sourceSeq != null) {
                    Triple(event.userId, event.deviceId, event.sourceSeq)
                } else {
                    null
                }
                if (key != null && key in sourceKeys) return IngestResult.DuplicateNoOp

                checkPlausibility(event, canon)?.let { return IngestResult.Rejected(it) }

                if (key != null) sourceKeys.add(key)
            }

            is EvidenceUpgraded -> {
                val original = log.filterIsInstance<VisitRecorded>()
                    .firstOrNull { it.eventId == event.refEventId }
                    ?: return IngestResult.Rejected("upgrade references unknown event ${event.refEventId}")
                val current = effectiveEvidence(original)
                if (event.newEvidence.ordinal <= current.ordinal) {
                    return IngestResult.Rejected(
                        "evidence downgrade $current -> ${event.newEvidence} not allowed",
                    )
                }
            }

            is VisitRevoked -> {
                if (log.none { it.eventId == event.refEventId }) {
                    return IngestResult.Rejected("revoke references unknown event ${event.refEventId}")
                }
            }

            is MediaAttached -> {
                if (log.filterIsInstance<VisitRecorded>().none { it.eventId == event.refEventId }) {
                    return IngestResult.Rejected("media references unknown visit ${event.refEventId}")
                }
                // Content addressing makes re-attaching the same photo to the
                // same visit a no-op rather than a duplicate row.
                val detached = log.filterIsInstance<MediaDetached>().map { it.refEventId }.toSet()
                val stillAttached = log.filterIsInstance<MediaAttached>().any {
                    it.refEventId == event.refEventId &&
                        it.mediaId == event.mediaId &&
                        it.eventId !in detached
                }
                if (stillAttached) return IngestResult.DuplicateNoOp
            }

            is MediaDetached -> {
                if (log.filterIsInstance<MediaAttached>().none { it.eventId == event.refEventId }) {
                    return IngestResult.Rejected("detach references unknown attachment ${event.refEventId}")
                }
            }
        }

        log.add(event)
        eventIds.add(event.eventId)
        return IngestResult.Accepted
    }

    /** Replays a persisted log without re-running plausibility (it already passed once). */
    fun restore(events: List<LedgerEvent>) {
        for (event in events) {
            if (event.eventId in eventIds) continue
            log.add(event)
            eventIds.add(event.eventId)
            if (event is VisitRecorded && event.deviceId != null && event.sourceSeq != null) {
                sourceKeys.add(Triple(event.userId, event.deviceId, event.sourceSeq))
            }
        }
    }

    private fun effectiveEvidence(v: VisitRecorded): Evidence =
        log.filterIsInstance<EvidenceUpgraded>()
            .filter { it.refEventId == v.eventId }
            .maxOfOrNull { it.newEvidence } ?: v.evidence

    /** Returns a rejection reason, or null if the visit is plausible. */
    private fun checkPlausibility(v: VisitRecorded, canon: CanonRelease): String? {
        val revoked = log.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()
        val existing = log.filterIsInstance<VisitRecorded>()
            .filter { it.userId == v.userId && it.eventId !in revoked }

        for (other in existing) {
            if (other.placeId == v.placeId) continue
            val overlap = minOf(v.endEpochSec, other.endEpochSec) -
                maxOf(v.startEpochSec, other.startEpochSec)
            if (overlap > overlapToleranceSec) {
                return "overlaps visit at ${other.placeId} by ${overlap}s — a body is in one place at a time"
            }
        }

        val here = canon.byId[v.placeId]?.centroid ?: return null
        for (other in existing) {
            val there = canon.byId[other.placeId]?.centroid ?: continue
            val gapSec = when {
                other.endEpochSec <= v.startEpochSec -> v.startEpochSec - other.endEpochSec
                v.endEpochSec <= other.startEpochSec -> other.startEpochSec - v.endEpochSec
                else -> continue // same place, or an overlap already tolerated above
            }
            val meters = haversineMeters(here, there)
            if (meters > 1_000 && gapSec >= 0) {
                val requiredSec = meters / maxSpeedMps
                if (gapSec < requiredSec) {
                    val km = (meters / 1_000).roundToLong()
                    return "teleport: ${km}km from ${other.placeId} in ${gapSec}s " +
                        "(needs ${requiredSec.roundToLong()}s at Mach 1)"
                }
            }
        }
        return null
    }
}
