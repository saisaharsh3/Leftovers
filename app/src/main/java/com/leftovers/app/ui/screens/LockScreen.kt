package com.leftovers.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.components.AuroraBackground
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.AppPasscode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lock screen. With [kind] null it unlocks with the phone's own lock (fingerprint, face or screen lock);
 * with an app PIN or password it asks for that, and [onFingerprint] offers fingerprint or face as a shortcut.
 */
@Composable
fun LockScreen(
    kind: AppPasscode.Kind?,
    onPhoneUnlock: () -> Unit,
    onFingerprint: (() -> Unit)?,
    check: suspend (String) -> Boolean,
    waitMs: () -> Long,
    canUsePhoneLock: Boolean,
) {
    val c = LocalAppColors.current
    AuroraBackground()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glass(Modifier.size(76.dp), strong = true, shape = RoundedCornerShape(26.dp)) {
            Icon(Lucide.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(32.dp).align(Alignment.Center))
        }
        Spacer(Modifier.height(24.dp))
        Text("Leftovers is locked", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        if (kind == null) {
            Text(
                "Unlock with your fingerprint, face or screen lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Unlock", onPhoneUnlock, Modifier.width(220.dp), icon = Lucide.Lock)
        } else {
            PasscodeEntry(kind, onFingerprint, check, waitMs, if (canUsePhoneLock) onPhoneUnlock else null)
        }
    }
}

@Composable
private fun PasscodeEntry(
    kind: AppPasscode.Kind,
    onFingerprint: (() -> Unit)?,
    check: suspend (String) -> Boolean,
    waitMs: () -> Long,
    onForgot: (() -> Unit)?,
) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val pin = kind == AppPasscode.Kind.PIN
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(waitMs()) }
    val focus = remember { FocusRequester() }
    // Counts down while too many wrong tries make it wait.
    LaunchedEffect(wait > 0) {
        while (wait > 0) {
            delay(500)
            wait = waitMs()
        }
        if (onFingerprint == null) runCatching { focus.requestFocus() }
    }
    fun submit() {
        if (code.isEmpty() || checking || wait > 0) return
        checking = true
        scope.launch {
            val ok = check(code)
            checking = false
            if (!ok) {
                wrong = true
                code = ""
                wait = waitMs()
            }
        }
    }

    Text(
        when {
            wait > 0 -> "Too many tries. Try again in ${(wait + 999) / 1000} s."
            wrong -> if (pin) "That PIN isn't right" else "That password isn't right"
            else -> if (pin) "Enter your PIN" else "Enter your password"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (wrong || wait > 0) c.negative else c.textSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    GlassTextField(
        code,
        { typed ->
            wrong = false
            code = if (pin) typed.filter { it.isDigit() }.take(AppPasscode.MAX_PIN) else typed.take(64)
        },
        placeholder = if (pin) "PIN" else "Password",
        keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Text,
        password = true,
        focusRequester = focus,
        modifier = Modifier.widthIn(max = 280.dp),
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(if (checking) "Checking…" else "Unlock", ::submit, Modifier.width(220.dp), enabled = code.isNotEmpty() && !checking && wait <= 0)
    if (onFingerprint != null) {
        Spacer(Modifier.height(18.dp))
        Text(
            "Use fingerprint or face",
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            modifier = Modifier.pressable(onFingerprint, pressedScale = 0.95f).padding(8.dp),
        )
    }
    if (onForgot != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            if (pin) "Forgot your PIN? Use the phone's screen lock" else "Forgot your password? Use the phone's screen lock",
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.pressable(onForgot, pressedScale = 0.95f).padding(8.dp),
        )
    }
}
