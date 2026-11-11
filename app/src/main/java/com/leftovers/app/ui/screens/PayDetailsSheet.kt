package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.SharedOwed

/**
 * How friends can pay you back. UPI, for India: a UPI ID and the name it's under. Other ways, for everyone else: a
 * payment link (PayPal.me, Revolut, Wise or any https link) and bank details to copy. They go in the reminders you
 * send from Money owed, where the link's page and a friend's Leftovers turn them into buttons.
 */
@Composable
fun PayDetailsSheet(current: SharedOwed.Pay, onSave: (SharedOwed.Pay) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var upi by rememberSaveable { mutableStateOf(current.upi) }
    var name by rememberSaveable { mutableStateOf(current.name) }
    var link by rememberSaveable { mutableStateOf(current.link) }
    var bank by rememberSaveable { mutableStateOf(current.bank) }
    val upiBad = upi.isNotBlank() && !SharedOwed.isUpi(upi)
    val linkBad = link.isNotBlank() && SharedOwed.payLink(link) == null
    // A PIN or password has no place here: anyone with the message would have it.
    val secret = Regex("(?i)\\b(pin|cvv|otp|password)\\b").containsMatchIn(bank)
    // Looks like a card number (15 or 16 digits that pass the card check): a warning only, as account numbers can be as long.
    val cardLike = Regex("\\d{15,16}").findAll(bank.replace(" ", "").replace("-", "")).any { luhn(it.value) }
    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("How to pay you", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Put in the reminders you send from Money owed, so a friend can pay you back in one tap. Add whichever you use.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Text("UPI (India)", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.padding(top = 4.dp))
            GlassTextField(upi, { upi = it.trim().take(80) }, placeholder = "yourname@okaxis", label = "UPI ID")
            if (upiBad) Text("A UPI ID looks like name@bank.", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 6.dp))
            GlassTextField(name, { name = it.take(40) }, placeholder = "As your bank has it (optional)", label = "Name on it")

            Text("Other ways to pay", style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.padding(top = 8.dp))
            Text("For friends where UPI isn't used.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            GlassTextField(link, { link = it.trim().take(200) }, placeholder = "paypal.me/you or revolut.me/you", label = "Payment link")
            if (linkBad) Text("A web link, like paypal.me/yourname.", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 6.dp))
            GlassTextField(bank, { bank = it.take(140) }, placeholder = "IBAN, or account and sort code", label = "Bank details (optional)", singleLine = false)
            if (secret) {
                Text("Only details for paying you in: never a PIN, OTP, CVV or password.", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 6.dp))
            } else if (cardLike) {
                Text("That looks like a card number. Share an account number or IBAN instead: anyone with the message would see it.", style = MaterialTheme.typography.bodySmall, color = c.warning, modifier = Modifier.padding(start = 6.dp))
            }

            PrimaryButton("Save", { onSave(SharedOwed.Pay(upi, name, link, bank)) }, Modifier.fillMaxWidth(), enabled = !upiBad && !linkBad && !secret)
            if (!current.isEmpty) SecondaryButton("Remove all", { onSave(SharedOwed.Pay()) }, Modifier.fillMaxWidth())
        }
    }
}

/** The check card numbers pass (Luhn), so a long account number isn't taken for one. */
private fun luhn(digits: String): Boolean {
    var sum = 0
    digits.reversed().forEachIndexed { i, ch ->
        var d = ch - '0'
        if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
        sum += d
    }
    return sum % 10 == 0
}
