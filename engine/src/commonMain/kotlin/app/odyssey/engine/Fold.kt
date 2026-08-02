package app.odyssey.engine

/**
 * Everything the app displays is derived from [fold]. Nothing in here is
 * stored; there is no boolean anywhere in the system that says "Utah: complete".
 */
data class FoldResult(
    val canonVersion: Int,
    val placesCredited: Set<String>,
    val placesDenominator: Int,
    val statesComplete: Set<String>,
    val frozenStates: Set<String>,
    /** state -> (credited, activeTotal) */
    val statePartial: Map<String, Pair<Int, Int>>,
    val stateDenominator: Int,
    /** placeId -> the evidence level that earned the credit */
    val creditedEvidence: Map<String, Evidence>,
    /** placeId -> visits that exist but do not count (self-reported, or under the dwell floor) */
    val uncreditedVisits: Map<String, Int>,
) {
    val placesCoveragePct: Double
        get() = if (placesDenominator == 0) 0.0 else 100.0 * placesCredited.size / placesDenominator

    val stateCoveragePct: Double
        get() = if (stateDenominator == 0) 0.0 else 100.0 * statesComplete.size / stateDenominator

    fun stateProgress(usState: String): Pair<Int, Int> = statePartial[usState] ?: (0 to 0)

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
    val uncredited = mutableMapOf<String, Int>()

    for (entry in denominator) {
        for ((v, ev) in liveVisits) {
            if (v.placeId != entry.placeId) continue
            val qualifies = ev.counts && v.dwellSeconds >= entry.minDwellSeconds
            if (qualifies) {
                val best = creditedEvidence[entry.placeId]
                if (best == null || ev > best) creditedEvidence[entry.placeId] = ev
            } else {
                uncredited[entry.placeId] = (uncredited[entry.placeId] ?: 0) + 1
            }
        }
    }

    val credited = creditedEvidence.keys.toSet()

    // States in play: any state with at least one ACTIVE or SUSPENDED entry.
    // Proposed-only states are not yet part of the game at all. Frozen: in play
    // but zero ACTIVE entries (everything suspended) — excluded from BOTH sides
    // of state coverage so nothing completes for free.
    val inPlay = canon.entries
        .filter { it.lifecycle == Lifecycle.ACTIVE || it.lifecycle == Lifecycle.SUSPENDED }
        .groupBy { it.usState }
    val frozen = inPlay.filterValues { es -> es.none { it.lifecycle == Lifecycle.ACTIVE } }.keys

    val activeByState = denominator.groupBy { it.usState }
    val partial = activeByState.mapValues { (_, es) ->
        Pair(es.count { it.placeId in credited }, es.size)
    }
    val complete = activeByState.filterValues { es -> es.all { it.placeId in credited } }.keys

    return FoldResult(
        canonVersion = canon.version,
        placesCredited = credited,
        placesDenominator = denominator.size,
        statesComplete = complete,
        frozenStates = frozen,
        statePartial = partial,
        stateDenominator = inPlay.size - frozen.size,
        creditedEvidence = creditedEvidence,
        uncreditedVisits = uncredited,
    )
}
