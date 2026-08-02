package app.odyssey

import androidx.compose.ui.window.ComposeUIViewController
import app.odyssey.engine.OdysseyRepository
import app.odyssey.ui.OdysseyApp
import platform.UIKit.UIViewController

/**
 * Single entry point into Kotlin from Swift.
 *
 * [USE_SIMULATED_LOCATION] drives the app from the in-app teleport controls
 * instead of CoreLocation — useful in the Simulator when you want to exercise
 * the geofence and dwell rules without moving.
 *
 * Media always comes from [SyntheticMediaSource] today. The real photo-library
 * picker lives in `docs/IosPhotoPicker.kt.txt`, ready to drop into this source
 * set; see the README for why it ships uncompiled.
 */
private const val USE_SIMULATED_LOCATION = true

fun MainViewController(): UIViewController {
    val location: LocationSource =
        if (USE_SIMULATED_LOCATION) SimulatedLocationSource() else IosLocationSource()
    location.start()

    val model = AppModel(
        repo = OdysseyRepository(),
        location = location,
        mediaSource = SyntheticMediaSource(),
    )

    return ComposeUIViewController { OdysseyApp(model) }
}
