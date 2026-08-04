package app.odyssey.engine

/** P3 owns this. Every other page reads it and never writes it. */
enum class Scope { WORLD, COUNTRY, STATE }

data class Selection(
    val scope: Scope = Scope.COUNTRY,
    val country: String = "US",
    val regionCode: String = "US-UT",
)

/** One immutable picture of the whole app, recomputed after every append. */
data class AppSnapshot(
    val canon: CanonRelease,
    val events: List<LedgerEvent>,
    val result: FoldResult,
    val selection: Selection,
) {
    val visits: List<VisitRecorded> get() = events.filterIsInstance<VisitRecorded>()

    fun entriesInSelectedRegion(): List<CanonEntry> = canon.entriesInRegion(selection.regionCode)

    fun visitsFor(placeId: String): List<VisitRecorded> {
        val revoked = events.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()
        return visits.filter { it.placeId == placeId && it.eventId !in revoked }
            .sortedByDescending { it.startEpochSec }
    }

    fun effectiveEvidence(v: VisitRecorded): Evidence {
        val up = events.filterIsInstance<EvidenceUpgraded>()
            .filter { it.refEventId == v.eventId }
            .maxOfOrNull { it.newEvidence }
        return if (up != null && up > v.evidence) up else v.evidence
    }

    /** Live attachments for a visit — detached ones are excluded but not erased. */
    fun mediaFor(visitEventId: String): List<MediaAttached> {
        val detached = events.filterIsInstance<MediaDetached>().map { it.refEventId }.toSet()
        return events.filterIsInstance<MediaAttached>()
            .filter { it.refEventId == visitEventId && it.eventId !in detached }
    }

    val liveMedia: List<MediaAttached>
        get() {
            val detached = events.filterIsInstance<MediaDetached>().map { it.refEventId }.toSet()
            val revokedVisits = events.filterIsInstance<VisitRevoked>().map { it.refEventId }.toSet()
            return events.filterIsInstance<MediaAttached>()
                .filter { it.eventId !in detached && it.refEventId !in revokedVisits }
        }
}

/**
 * The whole app's write path. Three rules, enforced here and nowhere else:
 *
 *  1. Writes are appends. There is no update and no delete.
 *  2. After every append the log is re-encoded and persisted whole, then the
 *     fold is recomputed from scratch. No incremental state.
 *  3. Reads are folds. The UI is handed a snapshot and cannot mutate it.
 */
class OdysseyRepository(
    private val store: KeyValueStore = KeyValueStore(),
    private val media: MediaStore = MediaStore(),
    val userId: String = "local-user",
    val deviceId: String = "device-local",
    initialCanon: CanonRelease = CanonWorld.release,
) {
    private val ledger = InMemoryLedger()
    private var canon: CanonRelease = initialCanon
    private var selection: Selection = Selection()
    private var seq: Long = 0

    init {
        store.read(LOG_KEY)?.let { text ->
            val restored = Codec.decodeAll(text)
            ledger.restore(restored)
            seq = restored.filterIsInstance<VisitRecorded>().mapNotNull { it.sourceSeq }.maxOrNull() ?: 0
        }
        val country = store.read(COUNTRY_KEY) ?: "US"
        selection = Selection(
            country = country,
            regionCode = store.read(SELECTION_KEY) ?: defaultRegion(country),
        )
    }

    fun snapshot(): AppSnapshot = AppSnapshot(
        canon = canon,
        events = ledger.events(),
        result = fold(ledger.events(), canon, userId),
        selection = selection,
    )

    // ---------- P3: selection context ----------

    fun select(scope: Scope): AppSnapshot {
        selection = selection.copy(scope = scope)
        return snapshot()
    }

    fun selectRegion(regionCode: String): AppSnapshot {
        selection = selection.copy(regionCode = regionCode, scope = Scope.STATE)
        store.write(SELECTION_KEY, regionCode)
        return snapshot()
    }

    /**
     * The C/S cascade is one-way: choosing a country resets the state beneath
     * it, but choosing a state never changes the country.
     */
    fun selectCountry(code: String): AppSnapshot {
        val statesThere = canon.entries
            .filter { it.country == code && it.lifecycle != Lifecycle.RETIRED }
            .map { it.regionCode }
            .distinct()
            .sorted()
        selection = selection.copy(
            country = code,
            regionCode = statesThere.firstOrNull() ?: selection.regionCode,
        )
        store.write(COUNTRY_KEY, code)
        statesThere.firstOrNull()?.let { store.write(SELECTION_KEY, it) }
        return snapshot()
    }

    /** Countries the canon actually covers. Everything else is listed but not selectable. */
    fun countriesInCanon(): Set<String> = canon.countriesInPlay().toSet()

    /**
     * Default state: the one from the most recent visit, alphabetical fallback.
     * Deliberately derived rather than remembered, so a fresh install and a
     * restored install agree.
     */
    fun defaultRegion(country: String = selection.country): String {
        val latest = ledger.events().filterIsInstance<VisitRecorded>().maxByOrNull { it.startEpochSec }
        val fromHistory = latest?.let { canon.byId[it.placeId]?.regionCode }
        if (fromHistory != null) return fromHistory

        // Alphabetical fallback, but *within the selected country*. Taking the
        // global minimum would open a US user on Abu Dhabi now that the canon
        // spans 46 countries.
        val here = canon.entries
            .filter { it.country == country && it.lifecycle != Lifecycle.RETIRED }
            .map { it.regionCode }
            .minOrNull()
        return here ?: canon.regionsInPlay().minOrNull() ?: "US-AL"
    }

    // ---------- writes ----------

    data class RecordOutcome(val result: IngestResult, val visitEventId: String?)

    fun recordVisit(
        placeId: String,
        startEpochSec: Long,
        endEpochSec: Long,
        evidence: Evidence,
    ): RecordOutcome {
        seq += 1
        val event = VisitRecorded(
            eventId = newEventId("v"),
            userId = userId,
            placeId = placeId,
            startEpochSec = startEpochSec,
            endEpochSec = endEpochSec,
            evidence = evidence,
            deviceId = deviceId,
            sourceSeq = seq,
        )
        val result = append(event)
        return RecordOutcome(result, if (result.isAccepted) event.eventId else null)
    }

    fun revokeVisit(eventId: String, reason: String): IngestResult =
        append(VisitRevoked(newEventId("r"), eventId, reason))

    fun upgradeEvidence(eventId: String, newEvidence: Evidence): IngestResult =
        append(EvidenceUpgraded(newEventId("u"), eventId, newEvidence))

    private fun append(event: LedgerEvent): IngestResult {
        val result = ledger.ingest(event, canon)
        if (result.isAccepted) store.write(LOG_KEY, Codec.encodeAll(ledger.events()))
        return result
    }

    // ---------- media ----------

    /**
     * Copies bytes into the app's own container and appends a [MediaAttached]
     * event referencing their content address. Nothing leaves the device.
     *
     * If the media is a photo whose EXIF corroborates the visit, an
     * [EvidenceUpgraded] is appended immediately after — two events, so the
     * ledger screen shows exactly why a percentage moved. Evidence is still
     * upgrade-only, so a corroborating photo can never demote a GPS visit.
     */
    fun attachMedia(visitEventId: String, bytes: ByteArray, kind: MediaKind): IngestResult {
        val visit = ledger.events().filterIsInstance<VisitRecorded>()
            .firstOrNull { it.eventId == visitEventId }
            ?: return IngestResult.Rejected("no such visit $visitEventId")

        val exif = if (kind == MediaKind.PHOTO) parseJpegExif(bytes) else ExifData()
        val mediaId = media.put(bytes)

        val attach = MediaAttached(
            eventId = newEventId("m"),
            refEventId = visitEventId,
            mediaId = mediaId,
            kind = kind,
            byteSize = bytes.size.toLong(),
            exifLatE7 = exif.gps?.lat?.toE7(),
            exifLngE7 = exif.gps?.lng?.toE7(),
            exifUtcEpochSeconds = exif.utcEpochSeconds,
        )

        val result = append(attach)
        if (!result.isAccepted) return result

        val entry = canon.byId[visit.placeId]
        // A backfilled claim must never promote itself to verified using the
        // photos that made it a claim in the first place. Auto-upgrade applies
        // to live captures only.
        val isBackfill = visit.evidence == Evidence.IMPORT_VERIFIED
        if (!isBackfill && entry != null && photoCorroborates(attach, visit, entry)) {
            val current = snapshot().effectiveEvidence(visit)
            if (current < Evidence.PHOTO_VERIFIED) {
                append(EvidenceUpgraded(newEventId("u"), visit.eventId, Evidence.PHOTO_VERIFIED))
            }
        }
        return result
    }

    sealed interface ClaimOutcome {
        data class Accepted(val visitEventId: String, val photos: Int) : ClaimOutcome
        data class Refused(val reason: String) : ClaimOutcome

        val error: String? get() = (this as? Refused)?.reason
    }

    /**
     * Claims a place visited before the app existed.
     *
     * Requires a *set* of photos rather than one — see [checkBackfill] for why
     * a single file can never establish that the user was there. What lands is
     * an [Evidence.IMPORT_VERIFIED] visit, which the fold scores separately
     * from live capture and which can never complete a state.
     */
    fun claimPastVisit(placeId: String, photos: List<ByteArray>): ClaimOutcome {
        val entry = canon.byId[placeId] ?: return ClaimOutcome.Refused("No such place.")

        val evidence = photos.map { bytes ->
            val exif = parseJpegExif(bytes)
            PhotoEvidence(
                mediaId = mediaIdOf(bytes),
                gps = exif.gps,
                utcEpochSeconds = exif.utcEpochSeconds,
                byteSize = bytes.size.toLong(),
            )
        }

        // Identical files are one photo, however many times they were added.
        val distinct = evidence.distinctBy { it.mediaId }

        return when (val check = checkBackfill(entry, distinct)) {
            is BackfillCheck.Rejected -> ClaimOutcome.Refused(check.reason)

            is BackfillCheck.Accepted -> {
                seq += 1
                val visit = VisitRecorded(
                    eventId = newEventId("v"),
                    userId = userId,
                    placeId = placeId,
                    startEpochSec = check.startEpochSec,
                    endEpochSec = check.endEpochSec,
                    evidence = Evidence.IMPORT_VERIFIED,
                    deviceId = deviceId,
                    sourceSeq = seq,
                )
                val result = append(visit)
                if (!result.isAccepted) {
                    return ClaimOutcome.Refused(result.message ?: "That claim conflicts with your history.")
                }
                val keep = check.photos.map { it.mediaId }.toSet()
                photos.filter { mediaIdOf(it) in keep }
                    .forEach { attachMedia(visit.eventId, it, MediaKind.PHOTO) }
                ClaimOutcome.Accepted(visit.eventId, check.photos.size)
            }
        }
    }

    /**
     * Detaches and reclaims the blob. The [MediaAttached] event stays in the
     * log — the history of what was attached is not rewritten — and any
     * evidence upgrade it caused also stays, because a photo that was verified
     * at the time was still verified at the time.
     */
    fun detachMedia(attachmentEventId: String, reason: String = "removed by user"): IngestResult {
        val attachment = ledger.events().filterIsInstance<MediaAttached>()
            .firstOrNull { it.eventId == attachmentEventId }
            ?: return IngestResult.Rejected("no such attachment")
        val result = append(MediaDetached(newEventId("x"), attachmentEventId, reason))
        if (result.isAccepted) {
            val stillReferenced = snapshot().liveMedia.any { it.mediaId == attachment.mediaId }
            if (!stillReferenced) media.delete(attachment.mediaId)
        }
        return result
    }

    fun mediaBytes(mediaId: String): ByteArray? = media.read(mediaId)

    fun mediaExists(mediaId: String): Boolean = media.exists(mediaId)

    fun libraryStats(): LibraryStats {
        val live = snapshot().liveMedia
        return LibraryStats(
            itemCount = live.size,
            photoCount = live.count { it.kind == MediaKind.PHOTO },
            videoCount = live.count { it.kind == MediaKind.VIDEO },
            ledgerBytes = live.sumOf { it.byteSize },
            onDiskBytes = media.totalBytes(),
            missingBlobs = live.count { !media.exists(it.mediaId) },
            excludedFromBackup = media.isExcludedFromBackup(),
        )
    }

    fun setExcludedFromBackup(excluded: Boolean) = media.setExcludedFromBackup(excluded)

    /** Publishes a new canon release. No user data moves; the next fold just answers differently. */
    fun publishCanon(release: CanonRelease): AppSnapshot {
        canon = release
        return snapshot()
    }

    fun rawLog(): String = Codec.encodeAll(ledger.events())

    companion object {
        const val LOG_KEY = "odyssey.ledger.v1"
        const val SELECTION_KEY = "odyssey.selection.state"
        const val COUNTRY_KEY = "odyssey.selection.country"
    }
}
