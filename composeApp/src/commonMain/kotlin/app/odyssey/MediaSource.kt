package app.odyssey

import app.odyssey.engine.LatLng
import app.odyssey.engine.MediaKind
import app.odyssey.engine.SyntheticJpeg

/**
 * Where media comes from. Deliberately one-way: this hands bytes *in*, and
 * nothing anywhere in the app can hand bytes back out to a network.
 */
abstract class MediaSource {

    abstract val label: String

    abstract val isSynthetic: Boolean

    /**
     * Returns however many the user chose — empty if they cancelled.
     *
     * Multi-select rather than one at a time because a backfill claim needs at
     * least three photos, and three separate trips through the picker to make
     * one claim is a flow nobody finishes.
     *
     * [near] and [atEpochSeconds] are used only by the synthetic source to
     * fabricate plausible EXIF. A real picker ignores them — metadata has to
     * come from the file, or it is not evidence.
     */
    abstract fun pick(
        kind: MediaKind,
        near: LatLng?,
        atEpochSeconds: Long,
        onResult: (List<ByteArray>) -> Unit,
    )
}

/**
 * The Simulator has no camera roll, so this stands in: a real EXIF container
 * built around the current fix. It gets no special treatment downstream — the
 * bytes go through the same hash, the same parser and the same evidence rules
 * as a photo off a phone.
 */
class SyntheticMediaSource : MediaSource() {

    private var salt = 0

    override val label: String get() = "Synthetic photos (dev)"

    override val isSynthetic: Boolean get() = true

    /**
     * Returns a small set, spread across space and time.
     *
     * Identical coordinates and one timestamp would be rejected by
     * `checkBackfill` — correctly, since that is what a forged claim looks
     * like. Fake data that cannot satisfy the real rule makes the claim flow
     * impossible to exercise, so the jitter is deliberate: two hours apart and
     * roughly fifty metres of drift per shot, which is what an afternoon
     * actually leaves behind.
     */
    override fun pick(
        kind: MediaKind,
        near: LatLng?,
        atEpochSeconds: Long,
        onResult: (List<ByteArray>) -> Unit,
    ) {
        if (kind != MediaKind.PHOTO || near == null) {
            onResult(emptyList())
            return
        }
        val batch = (0 until BATCH).map { i ->
            SyntheticJpeg.withGps(
                gps = LatLng(near.lat + i * LAT_STEP, near.lng),
                utcEpochSeconds = atEpochSeconds - (BATCH - 1 - i) * TIME_STEP,
                salt = salt++,
            )
        }
        onResult(batch)
    }

    private companion object {
        const val BATCH = 3

        /** ~55 m per step: past the 25 m movement floor without being a hike. */
        const val LAT_STEP = 0.0005

        /** Two hours between shots, so the set spans even the longest dwell floor. */
        const val TIME_STEP = 7_200L
    }
}
