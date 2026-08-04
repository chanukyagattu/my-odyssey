package app.odyssey

import androidx.compose.ui.window.ComposeUIViewController
import app.odyssey.engine.AccountStore
import app.odyssey.engine.OdysseyRepository
import app.odyssey.engine.SettingsStore
import app.odyssey.ui.OdysseyApp
import platform.UIKit.UIViewController

/**
 * Single entry point into Kotlin from Swift.
 *
 * [USE_SIMULATED_LOCATION] drives the app from the in-app teleport controls
 * instead of CoreLocation — useful in the Simulator, where there is no GPS.
 *
 * [USE_SYNTHETIC_MEDIA] swaps the system photo picker for fabricated JPEGs
 * carrying real EXIF — useful in a simulator with an empty photo library.
 */
private const val USE_SIMULATED_LOCATION = true
private const val USE_SYNTHETIC_MEDIA = false

fun MainViewController(): UIViewController {
    val location: LocationSource =
        if (USE_SIMULATED_LOCATION) SimulatedLocationSource() else IosLocationSource()
    location.start()

    val model = AppModel(
        accounts = AccountStore(),
        settings = SettingsStore(),
        // A fresh repository per signed-in user. The ledger is shared on disk
        // and the fold filters by userId, so two accounts on one device keep
        // separate histories without separate files.
        repoFor = { username -> OdysseyRepository(userId = username) },
        location = location,
        mediaSource = if (USE_SYNTHETIC_MEDIA) SyntheticMediaSource() else IosPhotoPicker(),
        sharer = IosSharer(),
    )

    return ComposeUIViewController { OdysseyApp(model) }
}
