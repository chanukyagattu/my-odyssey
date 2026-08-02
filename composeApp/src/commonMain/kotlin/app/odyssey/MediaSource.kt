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
     * [near] and [atEpochSeconds] are used only by the synthetic source to
     * fabricate plausible EXIF. A real picker ignores them — the metadata has
     * to come from the file, or it is not evidence.
     */
    abstract fun pick(
        kind: MediaKind,
        near: LatLng?,
        atEpochSeconds: Long,
        onResult: (ByteArray?) -> Unit,
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

    override val label: String get() = "Synthetic photo (dev)"

    override val isSynthetic: Boolean get() = true

    override fun pick(
        kind: MediaKind,
        near: LatLng?,
        atEpochSeconds: Long,
        onResult: (ByteArray?) -> Unit,
    ) {
        if (kind != MediaKind.PHOTO || near == null) {
            onResult(null)
            return
        }
        onResult(SyntheticJpeg.withGps(near, atEpochSeconds, salt++))
    }
}
