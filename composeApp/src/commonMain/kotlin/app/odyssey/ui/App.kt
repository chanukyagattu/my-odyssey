package app.odyssey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Route
import app.odyssey.engine.Scope

@Composable
fun OdysseyApp(model: AppModel) {
    OdysseyTheme(model.themeMode) {
        Box(modifier = Modifier.fillMaxSize().background(Palette.Ink)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                when (model.route) {
                    Route.LOGIN -> Body { LoginScreen(model) }

                    Route.REGISTER -> {
                        OdysseyBar(onBack = { model.go(Route.LOGIN) })
                        Body { RegisterScreen(model) }
                    }

                    Route.HOME -> {
                        OdysseyBar(
                            onMenu = { model.go(Route.MENU) },
                            initials = model.initials(),
                            onAvatar = { model.go(Route.MENU) },
                        )
                        TabsFor(model, timelineScope = Scope.WORLD)
                        Box(modifier = Modifier.height(14.dp))
                        Body { HomeScreen(model) }
                    }

                    Route.WORLD -> TrackerPage(model, Scope.WORLD, "Countries", Scope.WORLD)
                    Route.COUNTRY -> TrackerPage(model, Scope.COUNTRY, "States", Scope.COUNTRY)
                    Route.STATE -> TrackerPage(model, Scope.STATE, "Places", Scope.STATE)

                    Route.TIMELINE -> {
                        OdysseyBar(
                            onBack = { model.back() },
                            initials = model.initials(),
                            onAvatar = { model.go(Route.MENU) },
                        )
                        Body { TimelineScreen(model) }
                    }

                    Route.CAPTURE -> {
                        val entry = model.capturing
                        if (entry != null) Body { CaptureScreen(model, entry) }
                    }

                    Route.MENU -> {
                        OdysseyBar(onBack = { model.back() }, leadingLabel = "Menu")
                        Body { MenuScreen(model) }
                    }

                    Route.LEDGER -> {
                        OdysseyBar(onBack = { model.back() }, leadingLabel = "Ledger")
                        Body { LedgerScreen(model) }
                    }

                    Route.COUNTRY_PICKER -> {
                        OdysseyBar(onBack = { model.back() }, leadingLabel = "Choose country")
                        Body { CountryPickerScreen(model) }
                    }

                    Route.STATE_PICKER -> {
                        OdysseyBar(onBack = { model.back() }, leadingLabel = "Choose state")
                        Body { RegionPickerScreen(model) }
                    }

                    Route.FEED -> {
                        OdysseyBar(
                            onBack = { model.back() },
                            leadingLabel = "Feed",
                            initials = model.initials(),
                            onAvatar = { model.go(Route.MENU) },
                        )
                        Body { FeedScreen(model) }
                    }
                }
            }

            val toast = model.toast
            if (toast != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.SurfaceHigh)
                            .clickable { model.toast = null }
                            .padding(14.dp),
                    ) {
                        Text(toast, fontSize = 13.sp, color = Palette.Text)
                    }
                }
            }
        }
    }
}

/**
 * P4 / P5 / P6 share one frame. The TIMELINE tab carries the page's scope
 * across — W from P4, C from P5, S from P6, exactly as the wireframe's
 * "default:" annotations specify.
 */
@Composable
private fun ColumnScope.TrackerPage(
    model: AppModel,
    scope: Scope,
    label: String,
    timelineScope: Scope,
) {
    OdysseyBar(
        onBack = { model.back() },
        leadingLabel = label,
        initials = model.initials(),
        onAvatar = { model.go(Route.MENU) },
    )
    TabsFor(model, timelineScope)
    Box(modifier = Modifier.height(14.dp))
    Body { TrackerDetailScreen(model, scope) }
}

/** Every screen fills whatever height is left under the bar. */
@Composable
private fun ColumnScope.Body(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
}

@Composable
private fun TabsFor(model: AppModel, timelineScope: Scope) {
    TopTabs(
        trackerSelected = true,
        onTracker = { },
        onTimeline = { model.openTimeline(timelineScope) },
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text(subtitle, fontSize = 13.sp, color = Palette.Muted)
    }
}

private fun AppModel.initials(): String {
    val name = session?.fullName?.trim().orEmpty()
    if (name.isEmpty()) return "PP"
    val parts = name.split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> parts[0].take(2).uppercase()
    }
}
