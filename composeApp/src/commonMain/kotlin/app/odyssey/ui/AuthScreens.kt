package app.odyssey.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.Route

/** P1 — login / sign up. */
@Composable
fun LoginScreen(model: AppModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Box(modifier = Modifier.height(40.dp)) }

        item {
            Text(
                "Welcome to MY ODYSSEY",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                "LOGIN / SIGN UP",
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        item {
            OdysseyField(username, { username = it; error = null }, "username")
        }

        item {
            OdysseyField(password, { password = it; error = null }, "password", isPassword = true)
        }

        if (error != null) {
            item {
                Text(error ?: "", fontSize = 12.sp, color = Palette.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            PrimaryButton("SUBMIT") {
                error = model.signIn(username, password)
            }
        }

        item { Box(modifier = Modifier.height(24.dp)) }

        item {
            Text(
                "Not Signed up Yet?",
                fontSize = 13.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                GhostButton("Register to My Odyssey!!!") { model.go(Route.REGISTER) }
            }
        }

        item {
            Text(
                "Accounts are stored on this device only. Your password is never " +
                    "kept — just a salted SHA-256 of it.",
                fontSize = 11.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
    }
}

/** P2 — registration. */
@Composable
fun RegisterScreen(model: AppModel) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun clear() {
        error = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(modifier = Modifier.height(20.dp)) }

        item {
            Text(
                "Welcome to MY ODYSSEY",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                "Registration",
                fontSize = 13.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }

        item { OdysseyField(fullName, { fullName = it; clear() }, "Full Name") }
        item {
            OdysseyField(phone, { phone = it; clear() }, "Ph Number (optional)", keyboardType = KeyboardType.Phone)
        }
        item { OdysseyField(username, { username = it; clear() }, "User Name") }
        item {
            OdysseyField(email, { email = it; clear() }, "Email Id", keyboardType = KeyboardType.Email)
        }
        item { OdysseyField(password, { password = it; clear() }, "password", isPassword = true) }
        item { OdysseyField(confirm, { confirm = it; clear() }, "re-type pwd", isPassword = true) }

        if (error != null) {
            item {
                Text(error ?: "", fontSize = 12.sp, color = Palette.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { Box(modifier = Modifier.height(4.dp)) }

        item {
            PrimaryButton("Sign Me Up!!!") {
                error = model.register(fullName, phone, username, email, password, confirm)
            }
        }

        item {
            Text(
                "Already have an account? Back to login",
                fontSize = 12.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clickable { model.go(Route.LOGIN) },
            )
        }

        item {
            Text(
                "At least 8 characters with a letter and a digit. Phone number is " +
                    "optional — it is not used for anything yet.",
                fontSize = 11.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
