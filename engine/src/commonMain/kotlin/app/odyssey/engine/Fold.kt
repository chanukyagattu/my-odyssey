package app.odyssey.engine

/**
 * Everything the app displays is derived from [fold]. Nothing in here is
 * stored; there is no boolean anywhere in the system that says "Utah: complete".
 */
data class FoldResult(
    val canonVersion: Int,
    val placesCredited: Set<String>,
    val placesDenominator: Int,
    val regionsComplete: Set<String>,
    val frozenRegions: Set<String>,
    /** state -> (credited, activeTotal) */
    val regionPartial: Map<String, Pair<Int, Int>>,
    val regionDenominator: Int,
    /** placeId -> the evidence level that earned the credit */
    val creditedEvidence: Map<String, Evidence>,
    /** placeId -> visits that exist but do not count (self-reported, or under the dwell floor) */
    val uncreditedVisits: Map<String, Int>,
    /**
     * Backfilled claims — places the user says they visited before installing,
     * evidenced by imported photos. Deliberately disjoint from [placesCredited]
     * and excluded from every headline number and from state completion.
     */
    val placesClaimed: Set<String> = emptySet(),
) {
    val placesCoveragePct: Double
        get() = if (placesDenominator == 0) 0.0 else 100.0 * placesCredited.size / placesDenominator

    /** Claimed but not verified. Shown beside the headline, never as the headline. */
    val claimedCoveragePct: Double
        get() = if (placesDenominator == 0) 0.0 else 100.0 * placesClaimed.size / placesDenominator

    val regionCoveragePct: Double
        get() = if (regionDenominator == 0) 0.0 else 100.0 * regionsComplete.size / regionDenominator

    fun isClaimed(placeId: String): Boolean = placeId in placesClaimed

    fun regionProgress(regionCode: String): Pair<Int, Int> = regionPartial[regionCode] ?: (0 to 0)

    fun isCredited(placeId: String): Boolean = placeId in placesCredited
}

/**
 * Deterministic and order-independent: operates on the event *set*, keyed by
 * eventId, folded over event-time semantics. Ingest order can never change the
 * answer (global invariant 1).
 */
/**
 * Media events are inert here on purpose. Attaching a photo never moves a
 * percentage by itself; it can only cause an [EvidenceUpgraded] to be appended,
 * and that upgrade is what the fold sees. Keeping the derivation blind to media
 * means the numbers stay explainable from the evidence tiers alone.
 */
fun fold(events: Collection<LedgerEvent>, canon: CanonRelease, userId: String): FoldResult {
    val revoked = events.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()

    val upgrades: Map<String, Evidence> = events.filterIsInstance<EvidenceUpgraded>()
        .groupBy { it.refEventId }
        .mapValues { (_, ups) -> ups.maxOf { it.newEvidence } }

    val liveVisits: List<Pair<VisitRecorded, Evidence>> = events.filterIsInstance<VisitRecorded>()
        .filter { it.userId == userId && it.eventId !in revoked }
        .map { v -> v to maxOf(v.evidence, upgrades[v.eventId] ?: v.evidence) }

    val denominator = canon.active()

    val creditedEvidence = mutableMapOf<String, Evidence>()
    val claimed = mutableSetOf<String>()
    val uncredited = mutableMapOf<String, Int>()

    for (entry in denominator) {
        for ((v, ev) in liveVisits) {
            if (v.placeId != entry.placeId) continue
            val meetsDwell = v.dwellSeconds >= entry.minDwellSeconds
            when {
                // Verified at the time: this is what the headline counts.
                ev.isVerified && meetsDwell -> {
                    val best = creditedEvidence[entry.placeId]
                    if (best == null || ev > best) creditedEvidence[entry.placeId] = ev
                }
                // Backfilled: real history, separate number.
                ev.isClaimOnly && meetsDwell -> claimed.add(entry.placeId)

                else -> uncredited[entry.placeId] = (uncredited[entry.placeId] ?: 0) + 1
            }
        }
    }

    val credited = creditedEvidence.keys.toSet()
    // A place that was later verified live stops being merely claimed.
    claimed.removeAll(credited)

    // States in play: any state with at least one ACTIVE or SUSPENDED entry.
    // Proposed-only states are not yet part of the game at all. Frozen: in play
    // but zero ACTIVE entries (everything suspended) — excluded from BOTH sides
    // of state coverage so nothing completes for free.
    val inPlay = canon.entries
        .filter { it.lifecycle == Lifecycle.ACTIVE || it.lifecycle == Lifecycle.SUSPENDED }
        .groupBy { it.regionCode }
    val frozen = inPlay.filterValues { es -> es.none { it.lifecycle == Lifecycle.ACTIVE } }.keys

    val activeByState = denominator.groupBy { it.regionCode }
    val partial = activeByState.mapValues { (_, es) ->
        Pair(es.count { it.placeId in credited }, es.size)
    }
    val complete = activeByState.filterValues { es -> es.all { it.placeId in credited } }.keys

    return FoldResult(
        canonVersion = canon.version,
        placesCredited = credited,
        placesDenominator = denominator.size,
        regionsComplete = complete,
        frozenRegions = frozen,
        regionPartial = partial,
        regionDenominator = inPlay.size - frozen.size,
        creditedEvidence = creditedEvidence,
        uncreditedVisits = uncredited,
        placesClaimed = claimed.toSet(),
    )
}
