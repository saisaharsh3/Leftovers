package com.leftovers.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.LeftoversApp
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.EmailChecker
import com.leftovers.app.util.EmailConnection
import com.leftovers.app.util.MailProvider
import com.leftovers.app.util.friendlyLabel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EmailSetupViewModel(app: Application) : AndroidViewModel(app) {
    private val account = (app as LeftoversApp).container.emailAccount
    val connection: StateFlow<EmailConnection?> = account.connection

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Signs in once to check the details, then saves them (encrypted) and checks for payments. */
    fun connect(host: String, address: String, password: String) {
        busy = true
        message = null
        viewModelScope.launch {
            EmailChecker.testSignIn(host, address, password).fold(
                {
                    account.save(host, address, password)
                    val found = EmailChecker.check(getApplication()).getOrNull() ?: 0
                    message = if (found > 0) "Connected. Found $found ${if (found == 1) "payment" else "payments"} from the last two days." else "Connected. New payment emails will show up on Home."
                },
                { message = it.message },
            )
            busy = false
        }
    }

    fun checkNow() {
        busy = true
        message = null
        viewModelScope.launch {
            message = EmailChecker.check(getApplication()).fold(
                { if (it > 0) "Found $it new ${if (it == 1) "payment" else "payments"}" else "No new payments" },
                { it.message },
            )
            busy = false
        }
    }

    /** Forgets the address and app password and stops checking. */
    fun disconnect() {
        account.clear()
        message = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailSetupSheet(onDismiss: () -> Unit, viewModel: EmailSetupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val c = LocalAppColors.current
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Payments from email", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)

            val conn = connection
            if (conn != null) {
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Mail, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(conn.address, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                if (conn.lastCheckedAt == 0L) "Not checked yet" else "Last checked ${lastChecked(conn.lastCheckedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
                Text(
                    "Checked about every 30 minutes when you're online. Payments show up on Home for you to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                viewModel.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(if (viewModel.busy) "Checking…" else "Check now", { if (!viewModel.busy) viewModel.checkNow() }, Modifier.weight(1f))
                    SecondaryButton("Disconnect", viewModel::disconnect, Modifier.weight(1f))
                }
                Text(
                    "Disconnecting deletes the address and app password from this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
                return@Column
            }

            // What happens, before anything is entered.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Reads only payment alerts from banks, cards and payment apps; shops, social networks and promotions are skipped unopened",
                    "Never changes your mail or marks it as read",
                    "Keeps just the amount, merchant and date; email text never leaves the phone or gets saved",
                    "Your app password is encrypted on this phone and deleted when you disconnect",
                    "Nothing is added until you confirm it on Home",
                ).forEach { line ->
                    Row {
                        Icon(Lucide.Check, contentDescription = null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }

            var provider by rememberSaveable { mutableStateOf(MailProvider.GMAIL) }
            var host by rememberSaveable { mutableStateOf("") }
            var address by rememberSaveable { mutableStateOf("") }
            // Not saveable on purpose: the password isn't kept in saved UI state.
            var password by androidx.compose.runtime.remember { mutableStateOf("") }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MailProvider.entries.forEach { p -> Chip(p.label, { provider = p }, selected = p == provider) }
            }
            if (provider == MailProvider.OTHER) {
                GlassTextField(host, { host = it.trim().take(100) }, placeholder = "imap.example.com", label = "IMAP server", keyboardType = KeyboardType.Uri)
            }
            GlassTextField(address, { address = it.trim().take(120) }, placeholder = "you@example.com", label = "Email", keyboardType = KeyboardType.Email)
            GlassTextField(password, { password = it.take(100) }, placeholder = "App password, not your normal password", label = "App password", password = true)
            Text(provider.hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (provider.helpUrl.isNotEmpty()) {
                Text(
                    "How to make an app password",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.pressable({ uri.openUri(provider.helpUrl) }),
                )
            }
            Text(
                "Outlook and Hotmail no longer allow app passwords, so they can't be connected this way.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
            )
            viewModel.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.negative) }

            val serverHost = if (provider == MailProvider.OTHER) host else provider.host
            PrimaryButton(
                if (viewModel.busy) "Connecting…" else "Connect",
                { if (!viewModel.busy) viewModel.connect(serverHost, address, password) },
                Modifier.fillMaxWidth(),
                enabled = serverHost.isNotBlank() && address.contains('@') && password.isNotBlank() && !viewModel.busy,
            )
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun lastChecked(at: Long): String {
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return "${time.toLocalDate().friendlyLabel().lowercase().replaceFirstChar { if (it.isDigit()) it else it }} at ${time.format(timeFormat)}"
}
