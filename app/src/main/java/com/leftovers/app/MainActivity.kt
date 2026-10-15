package com.leftovers.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.leftovers.app.data.ThemeMode
import com.leftovers.app.ui.LeftoversNavHost
import com.leftovers.app.ui.components.AuroraBackground
import com.leftovers.app.ui.components.LocalHazeState
import com.leftovers.app.ui.screens.LockScreen
import com.leftovers.app.ui.screens.WelcomeScreen
import com.leftovers.app.ui.theme.LeftoversTheme
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** FragmentActivity because the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN_ADD = "open_add"
        private const val OWN_SCREEN_GRACE_MS = 10 * 60_000L
        private const val SYNC_AFTER_LAUNCH_MS = 1_200L
    }

    /** Set when the widget or reminder asks to jump straight to "add". */
    private var openAddRequest by mutableStateOf(false)
    private var locked by mutableStateOf(true)
    private var lockChecked by mutableStateOf(false)
    private var backgroundedAt = 0L
    private var firstStart = true
    /** When the app itself last opened another screen (photo picker, camera, share sheet, settings). */
    private var openedOwnScreenAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        val container = (application as LeftoversApp).container
        openAddRequest = intent.getBooleanExtra(EXTRA_OPEN_ADD, false)
        lifecycleScope.launch {
            locked = container.settings.settings.first().appLock
            lockChecked = true
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            val s = settings
            if (s != null && lockChecked) {
                val dark = when (s.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                // Keep status/navigation bar icons readable when the app theme differs from the system theme.
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                // With app lock on, hide balances from the Recents preview and block screenshots.
                LaunchedEffect(s.appLock) {
                    if (s.appLock) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                val money = remember(s.currencyCode) { Money(s.currencyCode) }
                val hazeState = rememberHazeState()
                val showLock = s.appLock && locked
                LeftoversTheme(darkTheme = dark, dynamicColor = s.dynamicColor, glassEffects = s.glassEffects, pureBlack = s.pureBlack) {
                    CompositionLocalProvider(
                        LocalMoney provides money,
                        com.leftovers.app.util.LocalUse24Hour provides when (s.timeFormat) {
                            com.leftovers.app.data.TimeFormat.H12 -> false
                            com.leftovers.app.data.TimeFormat.H24 -> true
                            com.leftovers.app.data.TimeFormat.PHONE -> android.text.format.DateFormat.is24HourFormat(this@MainActivity)
                        },
                        LocalHazeState provides hazeState,
                        LocalContentColor provides LocalAppColors.current.textPrimary,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // The aurora is a blur source so frosted bars pick up its colour.
                            AuroraBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
                            when {
                                !s.onboarded -> WelcomeScreen(defaultCurrency = s.currencyCode) { code ->
                                    scope.launch { container.settings.completeOnboarding(code) }
                                }
                                !showLock -> LeftoversNavHost(
                                    openAdd = openAddRequest,
                                    onOpenAddHandled = { openAddRequest = false },
                                )
                            }
                            AnimatedVisibility(showLock, enter = fadeIn(), exit = fadeOut()) {
                                val kind by container.passcode.kind.collectAsStateWithLifecycle()
                                val code = kind
                                LockScreen(
                                    kind = code,
                                    onPhoneUnlock = ::authenticate,
                                    onFingerprint = if (code != null && s.lockFingerprint && biometricAvailable()) {
                                        { authenticateBiometric(code) }
                                    } else {
                                        null
                                    },
                                    check = { typed -> container.passcode.check(typed).also { if (it) locked = false } },
                                    waitMs = container.passcode::waitMs,
                                    canUsePhoneLock = phoneLockAvailable(),
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(showLock) {
                    if (showLock) {
                        val code = container.passcode.kind.value
                        when {
                            code == null -> authenticate()
                            s.lockFingerprint -> authenticateBiometric(code)
                        }
                    }
                }
            }
        }
    }

    /**
     * Esc on a keyboard (tablets, Chromebooks) counts as exactly one Back. Left to the system it went back twice,
     * removing Home as well and leaving an empty screen.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == android.view.KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ADD, false)) openAddRequest = true
    }

    override fun onStart() {
        super.onStart()
        val container = (application as LeftoversApp).container
        lifecycleScope.launch {
            // On a cold start, let the first screen load its data before logging due subscriptions, which
            // writes to the database and makes every screen reload once more.
            if (firstStart) delay(SYNC_AFTER_LAUNCH_MS)
            firstStart = false
            container.syncRecurring()
        }
        // Background email checks can be held back for hours to save battery; opening the app catches up.
        container.emailAccount.connection.value?.let { com.leftovers.app.util.EmailSync.checkSoon(this, it.lastCheckedAt) }
        if (backgroundedAt > 0) {
            val away = SystemClock.elapsedRealtime() - backgroundedAt
            // Picking a photo or sharing a file leaves the app on purpose; that doesn't lock it, unless it's long.
            val ownTrip = openedOwnScreenAt > 0 && away < OWN_SCREEN_GRACE_MS
            openedOwnScreenAt = 0
            if (!ownTrip) {
                lifecycleScope.launch {
                    val s = container.settings.settings.first()
                    if (s.appLock && away >= s.lockAfterSeconds * 1000L) locked = true
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Ask for the fastest mode at the current resolution (90/120 Hz on phones that have it). */
    private fun preferHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    // Every screen the app opens goes through here, including startActivity and the photo and file pickers.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        openedOwnScreenAt = SystemClock.elapsedRealtime()
        super.startActivityForResult(intent, requestCode, options)
    }

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** With an app PIN or password: fingerprint or face as a shortcut, with the code as the way back. */
    private fun authenticateBiometric(kind: com.leftovers.app.util.AppPasscode.Kind) {
        if (!biometricAvailable()) return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Leftovers")
            .setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText(if (kind == com.leftovers.app.util.AppPasscode.Kind.PIN) "Use PIN" else "Use password")
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    private fun authenticate() {
        val manager = BiometricManager.from(this)
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val usable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ||
                getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
        }
        // Without any screen lock there's nothing to verify against, so don't trap the user.
        if (!usable) {
            locked = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Leftovers")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }

    /** Whether the phone has a screen lock (or fingerprint) that can unlock the app. */
    private fun phoneLockAvailable(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
    }
}
