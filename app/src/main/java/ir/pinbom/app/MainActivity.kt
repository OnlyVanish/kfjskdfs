package ir.pinbom.app

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.pinbom.app.data.PinBomRepository
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.FeaturePreferences
import ir.pinbom.app.data.ShareTargetException
import ir.pinbom.app.data.ShareTargetRules
import ir.pinbom.app.data.copySharedImageToCache
import ir.pinbom.app.ui.components.PinBomBootScreen
import ir.pinbom.app.ui.components.NavBarStyle
import ir.pinbom.app.ui.components.PinImagePreferences
import ir.pinbom.app.ui.nav.AppNavGraph
import ir.pinbom.app.ui.screens.AuthScreen
import ir.pinbom.app.ui.screens.InitialSetupScreen
import ir.pinbom.app.ui.screens.ServerSetupScreen
import ir.pinbom.app.ui.theme.AppLanguage
import ir.pinbom.app.ui.theme.BrandRed
import androidx.compose.ui.graphics.Color
import ir.pinbom.app.ui.theme.PinBomRoot
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.rememberReduceMotion
import ir.pinbom.app.ui.theme.syncSystemLocale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedImagePath = mutableStateOf<String?>(null)
    private val incomingDeepLink = mutableStateOf<Uri?>(null)
    @Inject
    lateinit var session: SessionManager

    @Inject
    lateinit var repo: PinBomRepository

    @Inject
    lateinit var pushRegistrar: ir.pinbom.app.push.PushRegistrar

    override fun onCreate(savedInstanceState: Bundle?) {
        /* The splash must be installed before super.onCreate() so the launch
         * theme is swapped to Theme.PinBom before the first frame is drawn. */
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingDeepLink.value = intent?.data
        handleIncomingShare(intent)

        /* Android 13+ needs a runtime grant before activity notifications can
         * show. Asked once, on launch, and never again if the user says no. */
        val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            savedInstanceState == null
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        /* Brand exit: the glyph scales up a hair while fading out, so the splash
         * dissolves into the app instead of hard-cutting. */
        val systemAnimationsEnabled = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
        splash.setOnExitAnimationListener { provider ->
            if (!systemAnimationsEnabled) {
                provider.remove()
            } else {
                provider.view.animate()
                    .alpha(0f)
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(200L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { provider.remove() }
                    .start()
            }
        }

        setContent {
            var boot by remember { mutableStateOf(false) }
            var serverReady by remember { mutableStateOf(false) }
            var loggedIn by remember { mutableStateOf(false) }
            var sessionExpiredNotice by remember { mutableStateOf(false) }
            /* Nothing stored yet? Then the app follows the phone: a dark phone
             * gets the dark theme, and there is no window the app disagrees with
             * before the user has said anything. An explicit choice in Settings
             * is stored and always wins. */
            val systemDark = remember {
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            var dark by remember { mutableStateOf(systemDark) }
            var navStyle by remember { mutableStateOf(NavBarStyle.Default) }
            var language by remember { mutableStateOf<AppLanguage?>(null) }
            var accentColor by remember { mutableStateOf(BrandRed) }
            var specialEffectsEnabled by remember { mutableStateOf(true) }
            var needsInitialSetup by remember { mutableStateOf(false) }
            var needsInterestsSetup by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            /* What the activity came up with: the device language, or the system's
             * per-app locale. Used until DataStore answers, so the first frame is
             * never in the wrong language. */
            val deviceLanguage = remember { AppLanguage.deviceDefault(resources.configuration) }

            LaunchedEffect(Unit) {
                session.authInvalidations.collect { invalidation ->
                    if (invalidation > 0) {
                        sessionExpiredNotice = true
                        loggedIn = false
                        needsInitialSetup = false
                        needsInterestsSetup = false
                    }
                }
            }

            /* Keep this device's push token registered for the signed-in account. */
            LaunchedEffect(boot, loggedIn) {
                if (boot && loggedIn) pushRegistrar.register() else if (boot) pushRegistrar.forget()
            }

            // Session rows are revoked server-side when an administrator bans a
            // user. Check the existing authenticated /me route while the app is
            // foregrounded so a quiet public feed cannot leave a banned user signed in.
            LaunchedEffect(boot, loggedIn, lifecycleOwner) {
                if (boot && loggedIn) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            runCatching { repo.verifyCurrentSession() }
                            delay(30_000L)
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                /* Independent settings reads share one wait. None blocks the
                 * main thread while it runs. */
                val theme = async { ThemeStore(this@MainActivity).read() }
                val stored = async { LanguageStore(this@MainActivity).read() }
                val accent = async { AccentColorStore(this@MainActivity).read() }
                val setupComplete = async { OnboardingStore(this@MainActivity).isComplete() }
                val interestsComplete = async { FeaturePreferences(this@MainActivity).interestsOnboardingComplete() }
                val saverEnabled = async { FeaturePreferences(this@MainActivity).dataSaver() }
                val specialEffects = async { FeaturePreferences(this@MainActivity).specialEffectsEnabled() }
                val pendingShared = async { FeaturePreferences(this@MainActivity).pendingSharePath() }
                session.load()
                // Server URL is shipped with the app; never show the server picker on
                // a fresh install or when upgrading from an older saved address.
                serverReady = true

                dark = theme.await() ?: systemDark
                PinImagePreferences.dataSaverEnabled = saverEnabled.await()
                specialEffectsEnabled = specialEffects.await()
                pendingShared.await()?.let { path -> if (File(path).isFile && sharedImagePath.value == null) sharedImagePath.value = path }
                // Migrate the old concave notch style once; keep other user choices.
                val savedNavStyle = NavBarStyleStore(this@MainActivity).read()
                navStyle = if (savedNavStyle == NavBarStyle.Notch) NavBarStyle.Tile else savedNavStyle
                if (savedNavStyle == NavBarStyle.Notch) {
                    NavBarStyleStore(this@MainActivity).set(NavBarStyle.Tile)
                }
                accentColor = accent.await() ?: BrandRed
                val resolved = stored.await() ?: deviceLanguage
                language = resolved
                syncSystemLocale(this@MainActivity, resolved)
                loggedIn = session.accessToken != null
                needsInitialSetup = loggedIn && !setupComplete.await()
                needsInterestsSetup = loggedIn && !interestsComplete.await()
                boot = true
            }

            val current = language ?: deviceLanguage

            /* Language changes are crossfaded through the app background: the veil
             * rises *before* the swap, so the new language is revealed by a fade —
             * no white flash, nothing recreated, the back stack stays put. */
            val veil = remember { Animatable(0f) }
            val systemReduceMotion = rememberReduceMotion()

            PinBomTheme(
                darkTheme = dark,
                language = current,
                accentColor = accentColor,
                specialEffectsEnabled = specialEffectsEnabled,
                onSelectLanguage = { picked ->
                    scope.launch {
                        if (!systemReduceMotion) veil.animateTo(0.55f, tween(durationMillis = 90, easing = Motion.standard))
                        language = picked
                        LanguageStore(this@MainActivity).set(picked)
                        syncSystemLocale(this@MainActivity, picked)
                        if (!systemReduceMotion) veil.animateTo(0f, tween(durationMillis = Motion.medium, easing = Motion.standard))
                        else veil.snapTo(0f)
                    }
                },
            ) {
                val colors = PinBomTheme.colors

                /* One themed surface under the entire app — boot screen, server
                 * setup, auth and the shell alike. Screens without a background
                 * of their own are the norm here, and before this surface the
                 * window behind Compose (which follows the *system* theme while
                 * the app follows the user's choice) showed through them. Every
                 * Text without an explicit colour also now inherits the theme's
                 * text colour instead of the platform's black. */
                PinBomRoot {
                    Box(Modifier.fillMaxSize()) {
                        /* Startup instrumentation. `reportFullyDrawn()` is the system's
                         * own “the app is usable now” marker: `adb shell am start -W`
                         * prints it and the Play console reads it. The log line below
                         * is debug-only and prints a real measurement — process start
                         * to the shell's first composition — never an estimate. */
                        var fullyDrawn by remember { mutableStateOf(false) }

                        when {
                            // Never a blank white screen: the brand mark pops in while
                            // the session is read from DataStore.
                            !boot -> PinBomBootScreen()
                            !serverReady -> ServerSetupScreen(session) { serverReady = true }
                            !loggedIn -> AuthScreen(
                                sessionExpiredNotice = sessionExpiredNotice,
                                onSuccess = {
                                    sessionExpiredNotice = false
                                    loggedIn = true
                                },
                                onRegistered = {
                                    sessionExpiredNotice = false
                                    scope.launch {
                                        OnboardingStore(this@MainActivity).setComplete(false)
                                        FeaturePreferences(this@MainActivity).setInterestsOnboardingComplete(false)
                                        needsInitialSetup = true
                                        needsInterestsSetup = true
                                        loggedIn = true
                                    }
                                },
                            )
                            needsInitialSetup -> InitialSetupScreen(
                                dark = dark,
                                onDarkChange = { picked ->
                                    dark = picked
                                    scope.launch { ThemeStore(this@MainActivity).set(picked) }
                                },
                                accentColor = accentColor,
                                onAccentChange = { picked ->
                                    accentColor = picked
                                    scope.launch { AccentColorStore(this@MainActivity).set(picked) }
                                },
                                onLanguageChange = { picked ->
                                    if (picked != language) {
                                        scope.launch {
                                            veil.animateTo(0.55f, tween(durationMillis = 90, easing = Motion.standard))
                                            language = picked
                                            LanguageStore(this@MainActivity).set(picked)
                                            syncSystemLocale(this@MainActivity, picked)
                                            veil.animateTo(0f, tween(durationMillis = Motion.medium, easing = Motion.standard))
                                        }
                                    }
                                },
                                onComplete = {
                                    scope.launch {
                                        OnboardingStore(this@MainActivity).setComplete(true)
                                        needsInterestsSetup = !FeaturePreferences(this@MainActivity).interestsOnboardingComplete()
                                    }
                                    needsInitialSetup = false
                                },
                            )
                            needsInterestsSetup -> ir.pinbom.app.ui.screens.InterestsScreen(
                                onDone = {
                                    scope.launch { FeaturePreferences(this@MainActivity).setInterestsOnboardingComplete(true) }
                                    needsInterestsSetup = false
                                },
                                onSkip = {
                                    scope.launch { FeaturePreferences(this@MainActivity).setInterestsOnboardingComplete(true) }
                                    needsInterestsSetup = false
                                },
                            )
                            else -> {
                                LaunchedEffect(Unit) {
                                    if (!fullyDrawn) {
                                        fullyDrawn = true
                                        reportFullyDrawn()
                                        if (BuildConfig.DEBUG) {
                                            Log.d(
                                                "PinBomPerf",
                                                "shell composed " +
                                                    "${SystemClock.uptimeMillis() - PinBomApplication.startedAt}ms " +
                                                    "after process start",
                                            )
                                        }
                                    }
                                }

                                val nav = rememberNavController()
                                val deepLink = incomingDeepLink.value
                                AppNavGraph(
                                    nav = nav,
                                    session = session,
                                    dark = dark,
                                    accentColor = accentColor,
                                    specialEffectsEnabled = specialEffectsEnabled,
                                    onSpecialEffectsChange = { enabled ->
                                        specialEffectsEnabled = enabled
                                        scope.launch { FeaturePreferences(this@MainActivity).setSpecialEffectsEnabled(enabled) }
                                    },
                                    onAccentChange = { picked ->
                                        accentColor = picked
                                        scope.launch { AccentColorStore(this@MainActivity).set(picked) }
                                    },
                                    navStyle = navStyle,
                                    initialSharePath = sharedImagePath.value,
                                    incomingDeepLink = deepLink,
                                    onSharePathConsumed = { sharedImagePath.value = null },
                                    onDarkChange = {
                                        dark = it
                                        scope.launch { ThemeStore(this@MainActivity).set(it) }
                                    },
                                    onLogout = {
                                        scope.launch {
                                            repo.logout()
                                            sessionExpiredNotice = false
                                            loggedIn = false
                                        }
                                    }
                                )
                            }
                        }

                        /* The crossfade veil: painted in the app's own background, so
                         * even the fade itself is theme-correct. */
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = veil.value }
                                .background(colors.background),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink.value = intent.data
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(incoming: Intent?) {
        val intent = incoming ?: return
        val payload = ShareTargetRules.sharedUris(intent) ?: return
        if (!payload.imageMime) {
            Toast.makeText(this, getString(R.string.share_error_not_image), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = payload.uris.firstOrNull()
        if (uri == null) {
            Toast.makeText(this, getString(R.string.share_error_unreadable), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = runCatching { copySharedImageToCache(this@MainActivity, uri) }
            result.onSuccess { file ->
                FeaturePreferences(this@MainActivity).setPendingSharePath(file.absolutePath)
                sharedImagePath.value = file.absolutePath
                if (payload.multiple && payload.uris.size > 1) {
                    Toast.makeText(this@MainActivity, getString(R.string.share_multiple_only_first), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                val message = when ((error as? ShareTargetException)?.reason) {
                    ShareTargetException.Reason.NOT_IMAGE -> R.string.share_error_not_image
                    ShareTargetException.Reason.TOO_LARGE -> R.string.share_error_too_large
                    ShareTargetException.Reason.EMPTY -> R.string.share_error_empty
                    ShareTargetException.Reason.UNREADABLE, null -> R.string.share_error_unreadable
                }
                Toast.makeText(this@MainActivity, getString(message), Toast.LENGTH_SHORT).show()
            }
        }
    }

}
