package app.odyssey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.odyssey.engine.Account
import app.odyssey.engine.AccountStore
import app.odyssey.engine.AppSnapshot
import app.odyssey.engine.AuthResult
import app.odyssey.engine.BACKFILL_MIN_PHOTOS
import app.odyssey.engine.CanonEntry
import app.odyssey.engine.CanonV1
import app.odyssey.engine.Countries
import app.odyssey.engine.Evidence
import app.odyssey.engine.ExifData
import app.odyssey.engine.ExploreGroup
import app.odyssey.engine.IngestResult
import app.odyssey.engine.LatLng
import app.odyssey.engine.LibraryStats
import app.odyssey.engine.MediaAttached
import app.odyssey.engine.MediaKind
import app.odyssey.engine.MemoryItem
import app.odyssey.engine.OdysseyRepository
import app.odyssey.engine.Scope
import app.odyssey.engine.SettingsStore
import app.odyssey.engine.ThemeMode
import app.odyssey.engine.VisitRecorded
import app.odyssey.engine.exploreGroups
import app.odyssey.engine.haversineMeters
import app.odyssey.engine.mediaIdOf
import app.odyssey.engine.memories
import app.odyssey.engine.nowEpochSeconds
import app.odyssey.engine.parseJpegExif
import app.odyssey.engine.photoCorroborates
import app.odyssey.engine.shareCardFor
import app.odyssey.engine.toE7

/**
 * Every page in the wireframe.
 *
 *   P1 LOGIN -> P2 REGISTER -> P1 -> P3 HOME
 *   P3 -> P4 WORLD | P5 COUNTRY | P6 STATE   (tracker drill-down)
 *   P3/P4 -> P7, P5 -> P8, P6 -> P9          (timeline, scope carried across)
 */
enum class Route { LOGIN, REGISTER, HOME, WORLD, COUNTRY, STATE, TIMELINE, CAPTURE, MENU, LEDGER, FEED }

/** The two top tabs on P3–P6. */
enum class TopTab { TRACKER, TIMELINE }

/** The two tabs on P7 / P8 / P9. */
enum class TimelineTab { MEMORIES, EXPLORE }

/** A photo chosen but not yet committed — it lands in the ledger with the visit. */
data class StagedMedia(
    val bytes: ByteArray,
    val kind: MediaKind,
    val exif: ExifData,
) {
    val mediaId: String get() = mediaIdOf(bytes)

    override fun equals(other: Any?): Boolean = other is StagedMedia && other.mediaId == mediaId

    override fun hashCode(): Int = mediaId.hashCode()
}

class AppModel(
    val accounts: AccountStore,
    private val settings: SettingsStore,
    private val repoFor: (String) -> OdysseyRepository,
    val location: LocationSource,
    val mediaSource: MediaSource,
    val sharer: Sharer,
) {
    /**
     * Appearance belongs to the phone, not the account, so it survives sign-out
     * and is restored before the first frame.
     */
    var themeMode: ThemeMode by mutableStateOf(settings.themeMode())
        private set

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        settings.setThemeMode(mode)
    }

    var session: Account? by mutableStateOf(accounts.currentAccount())
        private set

    var repo: OdysseyRepository by mutableStateOf(repoFor(session?.username ?: GUEST))
        private set

    var snapshot: AppSnapshot by mutableStateOf(repo.snapshot())
        private set

    // ---------- navigation ----------

    private val backStack = mutableListOf<Route>()

    var route: Route by mutableStateOf(if (session == null) Route.LOGIN else Route.HOME)
        private set

    var topTab: TopTab by mutableStateOf(TopTab.TRACKER)

    var timelineTab: TimelineTab by mutableStateOf(TimelineTab.MEMORIES)

    var toast: String? by mutableStateOf(null)

    fun go(next: Route) {
        if (next != route) backStack.add(route)
        route = next
        toast = null
    }

    fun back() {
        route = backStack.removeLastOrNull() ?: Route.HOME
        if (route == Route.LOGIN || route == Route.REGISTER) route = Route.HOME
    }

    /** Enters a tracker page and sets the scope it represents. P3 owns the write. */
    fun openTracker(scope: Scope) {
        snapshot = repo.select(scope)
        topTab = TopTab.TRACKER
        go(
            when (scope) {
                Scope.WORLD -> Route.WORLD
                Scope.COUNTRY -> Route.COUNTRY
                Scope.STATE -> Route.STATE
            },
        )
    }

    /** The TIMELINE tab. Scope carries across, per the wireframe's defaults. */
    fun openTimeline(scope: Scope) {
        snapshot = repo.select(scope)
        topTab = TopTab.TIMELINE
        go(Route.TIMELINE)
    }

    // ---------- P1 / P2 ----------

    fun signIn(username: String, password: String): String? {
        val result = accounts.signIn(username, password)
        if (result is AuthResult.Success) {
            adopt(result.account)
            return null
        }
        return result.error
    }

    fun register(
        fullName: String,
        phone: String,
        username: String,
        email: String,
        password: String,
        confirm: String,
    ): String? {
        val result = accounts.register(fullName, phone, username, email, password, confirm)
        if (result is AuthResult.Success) {
            adopt(result.account)
            return null
        }
        return result.error
    }

    private fun adopt(account: Account) {
        session = account
        repo = repoFor(account.username)
        snapshot = repo.snapshot()
        backStack.clear()
        route = Route.HOME
        topTab = TopTab.TRACKER
    }

    /**
     * Guarded, per the locked decision: never clear a session while GPS traces
     * or uploads are still queued. Nothing is queued in this build, so the
     * guard reports clean — but the check lives where it will matter.
     */
    fun pendingSyncWork(): Int = 0

    fun signOut() {
        accounts.signOut()
        session = null
        repo = repoFor(GUEST)
        snapshot = repo.snapshot()
        backStack.clear()
        route = Route.LOGIN
    }

    // ---------- selection (P3 owns it) ----------

    fun selectState(usState: String) {
        snapshot = repo.selectState(usState)
    }

    /** Changes scope without navigating — the W/C/S pills on P7 / P8 / P9. */
    fun setScope(scope: Scope) {
        snapshot = repo.select(scope)
    }

    val scope: Scope get() = snapshot.selection.scope

    val stateName: String get() = CanonV1.stateName(snapshot.selection.usState)

    val countryName: String get() = Countries.name(snapshot.selection.country)

    // ---------- share ----------

    /**
     * Renders progress at [scope] as a 1080x1920 card and opens the share
     * sheet. This is the app's only egress path: no feed, no cloud library,
     * just an image the user chooses to post. Card copy is aggregate by
     * construction — see [shareCardFor].
     */
    fun shareCard(scope: Scope) {
        sharer.shareCard(shareCardFor(snapshot, scope, session?.username))
    }

    fun shareCurrent() = shareCard(scope)

    // ---------- tracker / capture ----------

    var capturing: CanonEntry? by mutableStateOf(null)

    private fun refresh() {
        snapshot = repo.snapshot()
    }

    fun openCapture(entry: CanonEntry) {
        capturing = entry
        toast = null
        staged = emptyList()
        if (location.isSimulated) location.teleportTo(entry.centroid)
        go(Route.CAPTURE)
    }

    fun closeCapture() {
        capturing = null
        staged = emptyList()
        back()
    }

    fun distanceTo(entry: CanonEntry): Double? =
        location.fix?.let { haversineMeters(it, entry.centroid) }

    fun insideGeofence(entry: CanonEntry): Boolean =
        distanceTo(entry)?.let { it <= entry.geofenceRadiusMeters } == true

    fun evidenceFor(entry: CanonEntry): Evidence =
        if (insideGeofence(entry)) Evidence.GPS_VERIFIED else Evidence.SELF_REPORTED

    fun record(entry: CanonEntry, dwellSeconds: Long) {
        val end = nowEpochSeconds()
        val outcome = repo.recordVisit(
            placeId = entry.placeId,
            startEpochSec = end - dwellSeconds,
            endEpochSec = end,
            evidence = evidenceFor(entry),
        )
        val result = outcome.result
        outcome.visitEventId?.let { visitId ->
            staged.forEach { item -> repo.attachMedia(visitId, item.bytes, item.kind) }
        }
        refresh()
        toast = when (result) {
            is IngestResult.Accepted ->
                if (snapshot.result.isCredited(entry.placeId)) {
                    "Credited — ${entry.name}"
                } else {
                    "Recorded, not credited: " +
                        if (evidenceFor(entry) == Evidence.SELF_REPORTED) {
                            "no GPS fix inside the geofence"
                        } else {
                            "under the ${entry.minDwellSeconds / 60}-minute dwell floor"
                        }
                }

            is IngestResult.DuplicateNoOp -> "Already in the ledger — no-op"
            is IngestResult.Rejected -> "Rejected: ${result.reason}"
        }
        if (result.isAccepted) {
            clearStaged()
            capturing = null
            back()
        }
    }

    /**
     * Claims a place visited before the app existed, from the staged photos.
     *
     * Deliberately a different action from [record]: it needs a set of photos
     * rather than a fix, and what it earns is a claim rather than a
     * verification. The UI never lets the two look interchangeable.
     */
    fun claimPast(entry: CanonEntry) {
        val outcome = repo.claimPastVisit(entry.placeId, staged.map { it.bytes })
        refresh()
        toast = when (outcome) {
            is OdysseyRepository.ClaimOutcome.Accepted ->
                "Claimed ${entry.name} from ${outcome.photos} photos. It counts toward claimed, " +
                    "not verified — capture it live to verify."

            is OdysseyRepository.ClaimOutcome.Refused -> outcome.reason
        }
        if (outcome is OdysseyRepository.ClaimOutcome.Accepted) {
            clearStaged()
            capturing = null
            back()
        }
    }

    /** How many more photos a claim for [entry] still needs. */
    fun photosStillNeeded(): Int = (BACKFILL_MIN_PHOTOS - staged.size).coerceAtLeast(0)

    fun revoke(eventId: String) {
        val r = repo.revokeVisit(eventId, "revoked by user")
        refresh()
        toast = r.message ?: "Revoked — the original event is still in the log"
    }

    fun upgrade(eventId: String, to: Evidence) {
        val r = repo.upgradeEvidence(eventId, to)
        refresh()
        toast = r.message ?: "Evidence upgraded to ${to.label}"
    }

    fun teleport(target: LatLng) = location.teleportTo(target)

    // ---------- P7 / P8 / P9 ----------

    fun explore(): List<ExploreGroup> = exploreGroups(
        canon = snapshot.canon,
        result = snapshot.result,
        scope = snapshot.selection.scope,
        selectedCountry = snapshot.selection.country,
        selectedState = snapshot.selection.usState,
        fix = location.fix,
    )

    fun memoryFeed(): List<MemoryItem> = memories(
        snapshot = snapshot,
        scope = snapshot.selection.scope,
        selectedCountry = snapshot.selection.country,
        selectedState = snapshot.selection.usState,
    )

    // ---------- media ----------

    var staged: List<StagedMedia> by mutableStateOf(emptyList())
        private set

    fun stagePhoto(entry: CanonEntry) {
        mediaSource.pick(
            kind = MediaKind.PHOTO,
            near = location.fix ?: entry.centroid,
            atEpochSeconds = nowEpochSeconds(),
        ) { bytes ->
            if (bytes == null) {
                toast = "No photo selected."
            } else {
                val item = StagedMedia(bytes, MediaKind.PHOTO, parseJpegExif(bytes))
                staged = if (staged.any { it.mediaId == item.mediaId }) staged else staged + item
            }
        }
    }

    fun unstage(item: StagedMedia) {
        staged = staged.filterNot { it.mediaId == item.mediaId }
    }

    fun clearStaged() {
        staged = emptyList()
    }

    fun stagedCorroborates(item: StagedMedia, entry: CanonEntry, dwellSeconds: Long): Boolean {
        val end = nowEpochSeconds()
        val probeVisit = VisitRecorded(
            eventId = "probe",
            userId = repo.userId,
            placeId = entry.placeId,
            startEpochSec = end - dwellSeconds,
            endEpochSec = end,
            evidence = Evidence.SELF_REPORTED,
        )
        val probeMedia = MediaAttached(
            eventId = "probe-m",
            refEventId = "probe",
            mediaId = item.mediaId,
            kind = item.kind,
            byteSize = item.bytes.size.toLong(),
            exifLatE7 = item.exif.gps?.lat?.toE7(),
            exifLngE7 = item.exif.gps?.lng?.toE7(),
            exifUtcEpochSeconds = item.exif.utcEpochSeconds,
        )
        return photoCorroborates(probeMedia, probeVisit, entry)
    }

    fun libraryStats(): LibraryStats = repo.libraryStats()

    fun toggleBackupExclusion() {
        val now = repo.libraryStats().excludedFromBackup
        repo.setExcludedFromBackup(!now)
        refresh()
        toast = if (!now) {
            "Media excluded from your device backup. Losing this phone loses the library."
        } else {
            "Media included in your own iCloud device backup. It still never touches our servers."
        }
    }

    fun detachMedia(attachmentEventId: String) {
        val r = repo.detachMedia(attachmentEventId)
        refresh()
        toast = r.message ?: "Detached. The attachment event stays in the log; the bytes are reclaimed."
    }

    private companion object {
        const val GUEST = "guest"
    }
}

internal fun Double.oneDp(): String {
    val r = (this * 10).let { if (it < 0) 0L else it.toLong() }
    return "${r / 10}.${r % 10}"
}
