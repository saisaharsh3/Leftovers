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
import kotlinx.coroutines.launch

/** FragmentActivity because the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN_ADD = "open_add"
        private const val RELOCK_AFTER_MS = 30_000L
    }

    /** Set when the widget or reminder asks to jump straight to "add". */
    private var openAddRequest by mutableStateOf(false)
    private var locked by mutableStateOf(true)
    private var lockChecked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                LeftoversTheme(darkTheme = dark, dynamicColor = s.dynamicColor) {
                    CompositionLocalProvider(
                        LocalMoney provides money,
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
                                LockScreen(onUnlock = ::authenticate)
                            }
                        }
                    }
                }
                LaunchedEffect(showLock) { if (showLock) authenticate() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ADD, false)) openAddRequest = true
    }

    override fun onStart() {
        super.onStart()
        val container = (application as LeftoversApp).container
        lifecycleScope.launch { container.syncRecurring() }
        if (backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
            lifecycleScope.launch { if (container.settings.settings.first().appLock) locked = true }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
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
}
