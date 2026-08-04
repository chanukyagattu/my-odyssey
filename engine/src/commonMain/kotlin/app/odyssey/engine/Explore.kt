package app.odyssey.engine

/**
 * P7 / P8 / P9 — the Memories and Explore tabs.
 *
 * Explore is a *reference list*, not a navigation surface. It answers one
 * question — "what must-go places do I still have left, at the resolution I am
 * currently looking at" — and it never writes selection state. W/C/S stays
 * owned by P3.
 *
 * The three pages are the same list with a different grouping key:
 *
 *   P7 (world)   remaining places, grouped by country
 *   P8 (country) remaining places in the selected country, grouped by state
 *   P9 (state)   remaining places in the selected state, one group
 *
 * All of this lives in the engine rather than the UI so it can be asserted on
 * the JVM without a simulator.
 */

data class ExploreItem(
    val entry: CanonEntry,
    val distanceMeters: Double?,
    /** True when this is the last uncredited ACTIVE place in its state. */
    val completesState: Boolean,
)

data class ExploreGroup(
    val key: String,
    val title: String,
    val remaining: Int,
    val total: Int,
    val items: List<ExploreItem>,
) {
    val credited: Int get() = total - remaining
}

data class MemoryItem(
    val visit: VisitRecorded,
    val entry: CanonEntry?,
    val evidence: Evidence,
    val credited: Boolean,
    val media: List<MediaAttached> = emptyList(),
) {
    val mediaCount: Int get() = media.size
}

object Countries {
    fun name(code: String): String = CountryCatalog.name(code)

    /** Every country there is. Canon v1 covers one of them. */
    val TOTAL_IN_WORLD: Int get() = CountryCatalog.total
}

/**
 * Ordering. With a fix, nearest first — Explore becomes a reachability surface
 * and the top of the list is something you could actually go do today. Without
 * a fix (fresh install, or location declined) fall back to most-complete first,
 * which surfaces the user's own momentum instead of an arbitrary alphabetical
 * accident.
 */
private fun groupKey(entry: CanonEntry, scope: Scope): String =
    if (scope == Scope.WORLD) entry.country else entry.usState

private fun groupTitle(entry: CanonEntry, scope: Scope): String =
    if (scope == Scope.WORLD) Countries.name(entry.country) else CanonV1.stateName(entry.usState)

fun exploreGroups(
    canon: CanonRelease,
    result: FoldResult,
    scope: Scope,
    selectedCountry: String,
    selectedState: String,
    fix: LatLng?,
): List<ExploreGroup> {
    val active = canon.active()

    val scoped = when (scope) {
        Scope.WORLD -> active
        Scope.COUNTRY -> active.filter { it.country == selectedCountry }
        Scope.STATE -> active.filter { it.usState == selectedState }
    }

    // A place is "last in its state" against the whole canon, not the current
    // filter — otherwise P9 would claim every remaining place completes a state.
    val remainingPerState: Map<String, Int> = active
        .filter { it.placeId !in result.placesCredited }
        .groupingBy { it.usState }
        .eachCount()

    val groups = scoped.groupBy { groupKey(it, scope) }.map { (key, entriesInGroup) ->
        val remaining = entriesInGroup.filter { it.placeId !in result.placesCredited }
        val items = remaining.map { entry ->
            ExploreItem(
                entry = entry,
                distanceMeters = fix?.let { haversineMeters(it, entry.centroid) },
                completesState = remainingPerState[entry.usState] == 1,
            )
        }
        val ordered = if (fix != null) {
            items.sortedWith(compareBy({ it.distanceMeters ?: Double.MAX_VALUE }, { it.entry.name }))
        } else {
            items.sortedBy { it.entry.name }
        }
        ExploreGroup(
            key = key,
            title = groupTitle(entriesInGroup.first(), scope),
            remaining = remaining.size,
            total = entriesInGroup.size,
            items = ordered,
        )
    }

    return if (fix != null) {
        // A group is as close as its nearest remaining place. Sorting by a
        // country centroid would rank Brazil above Canada from Miami, which is
        // the wrong answer to "what can I go do".
        groups.sortedWith(
            compareBy(
                { g -> g.items.firstOrNull()?.distanceMeters ?: Double.MAX_VALUE },
                { it.title },
            ),
        )
    } else {
        groups.sortedWith(compareByDescending<ExploreGroup> { it.credited }.thenBy { it.title })
    }
}

/**
 * Memories is flat at every level — your visits, newest first. Only the scope
 * filter changes. Uncredited visits appear here too: they are real memories,
 * they simply do not move a percentage.
 */
fun memories(
    snapshot: AppSnapshot,
    scope: Scope,
    selectedCountry: String,
    selectedState: String,
): List<MemoryItem> {
    val revoked = snapshot.events.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()
    return snapshot.visits
        .filter { it.eventId !in revoked }
        .mapNotNull { visit ->
            val entry = snapshot.canon.byId[visit.placeId]
            val inScope = when (scope) {
                Scope.WORLD -> true
                Scope.COUNTRY -> entry?.country == selectedCountry
                Scope.STATE -> entry?.usState == selectedState
            }
            if (!inScope) {
                null
            } else {
                MemoryItem(
                    visit = visit,
                    entry = entry,
                    evidence = snapshot.effectiveEvidence(visit),
                    credited = snapshot.result.isCredited(visit.placeId),
                    media = snapshot.mediaFor(visit.eventId),
                )
            }
        }
        .sortedByDescending { it.visit.startEpochSec }
}
