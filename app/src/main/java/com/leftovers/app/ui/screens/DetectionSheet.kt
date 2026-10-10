package com.leftovers.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.leftovers.app.util.DetectionLog
import com.leftovers.app.data.matchFor
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.RowDivider
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.SmsParser

/**
 * The user's own payment keywords, for banks the built-in rules miss, and a box to paste a sample bank
 * message and see what the app makes of it. Nothing typed here leaves the phone or is stored except the keywords.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetectionSheet(keywords: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val money = LocalMoney.current
    val context = LocalContext.current
    val accounts by remember { (context.applicationContext as com.leftovers.app.LeftoversApp).container.accounts.accounts }
        .collectAsState(initial = emptyList())
    var newWord by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }

    fun addWord() {
        val w = newWord.trim().lowercase()
        if (w.length >= 2) onChange(keywords + w)
        newWord = ""
    }

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Detection keywords", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Bank SMS and email alerts are recognised by words like \"debited\", \"paid\" and, for money in, \"credited\". If your bank uses " +
                    "other words, add them here.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (keywords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keywords.sorted().forEach { w -> Chip(w, { onChange(keywords - w) }, icon = Lucide.X, selected = true) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextField(newWord, { newWord = it.take(30) }, placeholder = "e.g. txn done, used for", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Chip("Add", ::addWord, icon = Lucide.Plus, selected = newWord.trim().length >= 2)
            }

            RowDivider(inset = 0.dp)
            Text("Test a message", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                "Paste a bank SMS or alert to see what would be suggested. It's checked on the phone and not saved.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            GlassTextField(sample, { sample = it.take(1_000) }, placeholder = "Rs.250 debited from A/c XX1234 to VPA swiggy@icici", singleLine = false)
            if (sample.isNotBlank()) {
                val check = SmsParser.check(sample, keywords)
                val digits = SmsParser.accountDigits(sample)
                val account = accounts.matchFor(sample)
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val p = check.parsed
                        if (p != null) {
                            Text(
                                (if (p.isIncome) "+" else "") + money.format(p.amountMinor) +
                                    if (p.merchant.isNotBlank()) (if (p.isIncome) " from " else " to ") + p.merchant else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = c.positive,
                            )
                            Text(
                                "${check.reason}: this would show up on Home to confirm" + if (p.isIncome) ", as income." else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                            when {
                                account != null -> Text("Goes to your ${account.name} account", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                digits != null -> Text(
                                    "Names account or card ending $digits. Add it to an account (Accounts → edit) to send these there.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
                                // No match yet: say what the message calls the account, so the user knows what to name theirs.
                                else -> com.leftovers.app.data.namedAccountIn(sample)?.let { named ->
                                    Text(
                                        "Mentions your \"$named\" account. Name one of your accounts \"$named\" (Accounts) to send these there.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                            }
                        } else {
                            Text("Not suggested", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Text(check.reason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }

            // What happened to real messages, so a missed payment can be explained.
            RowDivider(inset = 0.dp)
            Text("Recent detections", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            val smsAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            if (!smsAllowed) {
                Text(
                    "SMS permission is off, so bank SMS can't be read. Allow it in Android Settings → Apps → Leftovers → Permissions → SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning,
                )
            }
            val log = remember { DetectionLog.read(context) }
            if (log.isEmpty()) {
                Text(
                    "Nothing yet. Bank SMS and email alerts with an amount appear here with what happened to them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            } else {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        log.forEachIndexed { i, e ->
                            if (i > 0) RowDivider(inset = 16.dp)
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(e.result, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                                Text(
                                    "${e.source} · ${e.from} · ${logTime.format(java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault()))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val logTime = java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a")
