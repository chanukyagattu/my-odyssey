package app.odyssey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.odyssey.engine.LatLng

/**
 * The app's only source of position. GPS is the product's trust mechanism, so
 * this is deliberately a narrow, observable surface: a fix, an accuracy, and a
 * permission state. Everything above it treats a fix as untrusted input.
 */
abstract class LocationSource {

    var fix: LatLng? by mutableStateOf(null)
        internal set

    var accuracyMeters: Double? by mutableStateOf(null)
        internal set

    var status: String by mutableStateOf("Not started")
        internal set

    abstract val isSimulated: Boolean

    abstract fun start()

    abstract fun stop()

    /** Dev affordance: drop the pin somewhere specific. No-op for real GPS. */
    open fun teleportTo(target: LatLng) = Unit
}

/**
 * Used in the Simulator and for demoing the invariants without leaving the
 * couch. A simulated fix is still fed through the exact same evidence rules —
 * it does not get a free pass.
 */
class SimulatedLocationSource(start: LatLng = LatLng(37.2982, -113.0263)) : LocationSource() {

    init {
        fix = start
        accuracyMeters = 5.0
        status = "Simulated"
    }

    override val isSimulated: Boolean get() = true

    override fun start() {
        status = "Simulated"
    }

    override fun stop() {
        status = "Stopped"
    }

    override fun teleportTo(target: LatLng) {
        fix = target
        accuracyMeters = 5.0
        status = "Simulated"
    }
}
