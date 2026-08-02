package app.odyssey

import app.odyssey.engine.LatLng
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Real CoreLocation. The app asks for When-In-Use only: milestone 0 verifies a
 * visit while you are standing in it, and asking for Always before that is
 * earned would be a bad trade for the user.
 */
class IosLocationSource : LocationSource() {

    override val isSimulated: Boolean get() = false

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    override fun start() {
        status = "Requesting permission…"
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    override fun stop() {
        manager.stopUpdatingLocation()
        status = "Stopped"
    }

    @OptIn(ExperimentalForeignApi::class)
    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            location.coordinate.useContents {
                this@IosLocationSource.fix = LatLng(latitude, longitude)
            }
            this@IosLocationSource.accuracyMeters = location.horizontalAccuracy
            this@IosLocationSource.status = "Live GPS"
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            this@IosLocationSource.status = "Location unavailable: ${didFailWithError.localizedDescription}"
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            manager.startUpdatingLocation()
            this@IosLocationSource.status = "Authorization changed — restarting updates"
        }
    }
}
