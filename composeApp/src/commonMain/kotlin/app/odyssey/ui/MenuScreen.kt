package app.odyssey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Route
import app.odyssey.engine.ThemeMode

/**
 * The hamburger menu from P3. Account actions live here, per the locked flow
 * decision, alongside the ledger inspector — which is not in the wireframe but
 * is the screen that makes the architecture inspectable.
 */
@Composable
fun MenuScreen(model: AppModel) {
    val account = model.session
    val stats = model.libraryStats()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        account?.fullName ?: "Not signed in",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                    )
                    Text("@${account?.username ?: "guest"}", fontSize = 13.sp, color = Palette.Verified)
                    if (account != null) {
                        Text(account.email, fontSize = 12.sp, color = Palette.Muted)
                        account.phone?.let { Text(it, fontSize = 12.sp, color = Palette.Muted) }
                    }
                }
            }
        }

        item { SectionTitle("Appearance") }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Theme", fontSize = 15.sp, color = Palette.Text)
                        ThemeToggle(model.themeMode) { model.setTheme(it) }
                    }
                    Box(modifier = Modifier.height(10.dp))
                    Text(
                        when (model.themeMode) {
                            ThemeMode.SYSTEM ->
                                "Following your phone. It already knows whether you're in " +
                                    "bright sun or a tent."
                            ThemeMode.DARK -> "Always dark. Easier on battery, and on your eyes at a campsite."
                            ThemeMode.LIGHT -> "Always light. Accents are darkened so percentages stay readable outdoors."
                        },
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
        }

        item { SectionTitle("Your data") }

        item {
            MenuRow(
                title = "Your activity",
                subtitle = "Your ledger, read back as plain sentences",
            ) { model.go(Route.FEED) }
        }

        item {
            MenuRow(
                title = "Ledger",
                subtitle = "${model.snapshot.events.size} events · every number in the app is a fold of these",
            ) { model.go(Route.LEDGER) }
        }

        item {
            MenuRow(
                title = "Media library",
                subtitle = "${stats.itemCount} items · ${stats.humanSize} · on this device only",
            ) { model.go(Route.LEDGER) }
        }

        item { SectionTitle("Account") }

        item {
            MenuRow(
                title = "Change password",
                subtitle = "Email-only reset links — P10 / P11, not built yet",
            ) { model.toast = "Password reset lands with the backend (flow spec P10 / P11)." }
        }

        item {
            MenuRow(
                title = "Sign out",
                subtitle = "Clears the session. Your ledger and media stay on this device.",
                danger = true,
            ) {
                val pending = model.pendingSyncWork()
                if (pending > 0) {
                    model.toast = "$pending unsynced item(s) still queued — not signing out yet."
                } else {
                    model.signOut()
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "My Odyssey · canon v${model.snapshot.result.canonVersion} · " +
                        "${model.snapshot.result.placesDenominator} places across " +
                        "${model.snapshot.result.stateDenominator} states",
                    fontSize = 11.sp,
                    color = Palette.Muted,
                )
                Text(
                    "Completion is never stored. It is recomputed from the ledger every time.",
                    fontSize = 11.sp,
                    color = Palette.Muted,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) Palette.Danger else Palette.Text,
                )
                Box(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Palette.Muted)
            }
            Text("›", fontSize = 20.sp, color = Palette.Muted)
        }
    }
}
