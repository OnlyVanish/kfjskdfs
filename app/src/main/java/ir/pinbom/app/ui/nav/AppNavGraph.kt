package ir.pinbom.app.ui.nav

import android.os.SystemClock
import android.net.Uri
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ir.pinbom.app.ui.components.rememberCollapsingHeaderState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.ui.UploadViewModel
import ir.pinbom.app.ui.NavigationProfileViewModel
import ir.pinbom.app.ui.components.PinBomBottomBar
import ir.pinbom.app.ui.components.PinBomRail
import ir.pinbom.app.ui.components.NavBarStyle
import ir.pinbom.app.ui.components.UploadProgressHost
import ir.pinbom.app.ui.screens.BoardScreen
import ir.pinbom.app.ui.screens.BoardsScreen
import ir.pinbom.app.ui.screens.CreatePinScreen
import ir.pinbom.app.ui.screens.HomeScreen
import ir.pinbom.app.ui.screens.NotificationsScreen
import ir.pinbom.app.ui.screens.PinDetailScreen
import ir.pinbom.app.ui.screens.ProfileScreen
import ir.pinbom.app.ui.screens.SearchScreen
import ir.pinbom.app.ui.screens.SimilarPinsScreen
import ir.pinbom.app.ui.screens.TagResultsScreen
import ir.pinbom.app.ui.screens.SettingsScreen
import ir.pinbom.app.ui.screens.InterestsScreen
import ir.pinbom.app.ui.screens.ActiveSessionsScreen
import ir.pinbom.app.ui.screens.BlockedUsersScreen
import ir.pinbom.app.ui.screens.PeopleScreen
import ir.pinbom.app.ui.screens.PushPrefsScreen
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.BrandRed
import androidx.compose.ui.graphics.Color
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.reduceMotion
import kotlin.math.roundToInt

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · navigation shell
 *  One NavHost, one shell. The floating pill (phones) or the floating rail
 *  (≥600dp) stays up on the five tabs and slides away on any pushed detail, so a
 *  pin can own the whole screen. Route strings, arguments and deep links are
 *  untouched: `pinbom://pin/{id}`, `pinbom://create`, `pinbom://search` still land here.
 *
 *  Shared bounds are hosted only around the NavHost (never the rail/floating bar),
 *  and can be disabled with PIN_SHARED_TRANSITIONS_ENABLED when Lookahead hurts scroll.
 *
 *  Motion is directional now. A tab switch is a fade-through (no slide: the two
 *  screens are siblings, not a hierarchy), a pushed screen travels a *fraction*
 *  of the width while the one behind it steps back and dims, the create screen
 *  rises from the bottom edge, and a pop is the exact mirror of the push. With
 *  reduced motion everything collapses to a hard cut.
 * ──────────────────────────────────────────────────────────────────────────── */

/** Tab switches: out fast, in settled — 220ms end to end. */
private const val TAB_OUT_MS = 140
private const val TAB_IN_MS = 300

/** A tab slides this fraction of the width: enough to read as direction, cheap to draw. */
private const val TAB_TRAVEL = 0.16f

/** A pushed screen travels 12% of the window; the one behind steps back 6%. */
private const val PUSH_TRAVEL = 0.12f
private const val PUSH_REST = 0.06f

/** While the shell is this busy, a second tap is a bounce, not a navigation. */
private const val NAV_DEBOUNCE_MS = 320L

@Composable
fun AppNavGraph(
    nav: NavHostController,
    session: SessionManager,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    accentColor: Color = BrandRed,
    onAccentChange: (Color) -> Unit = {},
    specialEffectsEnabled: Boolean = true,
    onSpecialEffectsChange: (Boolean) -> Unit = {},
    navStyle: NavBarStyle = NavBarStyle.Tile,
    initialSharePath: String? = null,
    incomingDeepLink: Uri? = null,
    onSharePathConsumed: () -> Unit = {},
) {
    AppNavShell(
        nav = nav,
        session = session,
        dark = dark,
        onDarkChange = onDarkChange,
        onLogout = onLogout,
        accentColor = accentColor,
        onAccentChange = onAccentChange,
        specialEffectsEnabled = specialEffectsEnabled,
        onSpecialEffectsChange = onSpecialEffectsChange,
        navStyle = navStyle,
        initialSharePath = initialSharePath,
        incomingDeepLink = incomingDeepLink,
        onSharePathConsumed = onSharePathConsumed,
    )
}

@Composable
private fun AppNavShell(
    nav: NavHostController,
    session: SessionManager,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    accentColor: Color,
    onAccentChange: (Color) -> Unit,
    specialEffectsEnabled: Boolean,
    onSpecialEffectsChange: (Boolean) -> Unit,
    navStyle: NavBarStyle,
    initialSharePath: String?,
    incomingDeepLink: Uri?,
    onSharePathConsumed: () -> Unit,
) {
    val colors = PinBomTheme.colors
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val shellVisible = isTabRoute(currentRoute)
    var lastHandledSharePath by remember { mutableStateOf<String?>(null) }
    var lastHandledDeepLink by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(initialSharePath, incomingDeepLink) {
        if (!initialSharePath.isNullOrBlank() && File(initialSharePath).isFile && initialSharePath != lastHandledSharePath) {
            lastHandledSharePath = initialSharePath
            nav.navigate(Destination.Create.route) { launchSingleTop = true }
            return@LaunchedEffect
        }
        // A previously consumed share must not suppress a later pinbom:// or bumo:// link.
        val deepLink = incomingDeepLink
        if (deepLink == null || deepLink == lastHandledDeepLink) return@LaunchedEffect
        lastHandledDeepLink = deepLink
        when (deepLink.scheme) {
            "pinbom", "bumo" -> when (deepLink.host) {
                "pin" -> deepLink.pathSegments.firstOrNull()?.toLongOrNull()?.let { nav.navigate("pin/$it") }
                "create" -> nav.navigate(Destination.Create.route)
                "search" -> nav.navigate(Destination.Search.route)
            }
        }
    }

    val navProfileVm: NavigationProfileViewModel = hiltViewModel()
    val navProfile by navProfileVm.profile.collectAsStateWithLifecycle()
    LaunchedEffect(session.username, currentRoute) {
        if (currentRoute == Destination.Home.route || currentRoute == Destination.Profile.route) {
            /* Wait for the tab transition to finish: the refresh may recompose the
             * shell and the bar, and that used to land on the busiest frames. */
            kotlinx.coroutines.delay(600L)
            navProfileVm.load(session.username, force = true)
        }
    }
    val tabs = pinbomTabs(
        profileName = navProfile?.display_name?.ifBlank { session.username } ?: session.username,
        profileUrl = navProfile?.avatar_url,
    )

    /* One upload, one card, for the whole shell. Hoisted *above* the NavHost on
     * purpose: the card is resolved against the activity, so it survives every
     * navigation and rotation, and the create screen borrows this very instance
     * instead of owning an upload of its own. */
    val uploads: UploadViewModel = hiltViewModel()

    /* Rapid taps on the bar used to stack duplicate entries — and a duplicate
     * entry means a second ViewModel, a second pager and a second round of
     * network calls for a screen the user was already on. One gate, one frame of
     * patience. */
    var lastNavAt by remember { mutableStateOf(0L) }

    fun open(route: String) {
        /* Tapping the tab you are on scrolls it back to the top instead of
         * re-navigating to the screen you are already looking at. */
        if (route == currentRoute) {
            ScrollToTopEvents.emit(route)
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastNavAt < NAV_DEBOUNCE_MS) return
        lastNavAt = now
        nav.navigate(route) {
            /* The canonical shell navigation: one copy of each tab, each tab
             * keeps what it had (scroll, ViewModel, draft) and the back stack
             * stays a back stack instead of a history of visits. */
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val reduce = reduceMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    /* The floating pill steps out of the way while a wall is scrolled down and
     * comes back with the first scroll up, a tab change or the top of the list.
     * Same engine as the collapsing header: one float, read in a graphics layer,
     * fed by the scroll the content really consumed. */
    val barScroll = rememberCollapsingHeaderState()
    val barHidePx = with(LocalDensity.current) { 120.dp.toPx() }
    SideEffect { barScroll.heightPx = barHidePx }
    LaunchedEffect(currentRoute) { barScroll.show() }

    /* While a finger is down the screen follows it (damped, so it reads as
     * “pulling” rather than “moving”). Read only inside graphicsLayer, so a
     * drag redraws one layer and recomposes nothing. On release it springs home. */
    val dragPx = remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    /* Without this, a new swipe that started while the previous one was still
     * springing home had two animations writing the same offset: the jitter people
     * feel as “lag” when they swipe several tabs in a row. */
    val settleJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    fun cancelSettle() {
        settleJob[0]?.cancel()
        settleJob[0] = null
    }
    fun settleDrag() {
        cancelSettle()
        settleJob[0] = dragScope.launch {
            Animatable(dragPx.floatValue).animateTo(
                0f,
                spring(dampingRatio = 0.9f, stiffness = 700f),
            ) { dragPx.floatValue = value }
        }
    }
    /* +1 when “forward” means “towards the right edge”. */
    val forwardSign = if (rtl) -1f else 1f

    /* A fraction of the window, not the whole width: the pushed screen travels
     * far enough to read as “in front”, not far enough to feel like a slideshow. */
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val travelPx = windowWidth * PUSH_TRAVEL
    val restPx = windowWidth * PUSH_REST

    /* ── The four motions ─────────────────────────────────────────────────── */

    /* Siblings: a short directional slide + fade. The direction follows the tab
     * order (and flips in RTL) so swiping and tapping feel like moving along a
     * strip. Only translation and alpha animate — both are layer-only, so the
     * feed grid is never re-measured while it moves. */
    fun tabDir(from: String?, to: String?): Int {
        val a = Tabs.indexOfFirst { it.route == from }
        val b = Tabs.indexOfFirst { it.route == to }
        if (a < 0 || b < 0 || a == b) return 0
        val logical = if (b > a) 1 else -1
        return if (rtl) -logical else logical
    }
    fun tabIn(dir: Int): EnterTransition = slideInHorizontally(
        animationSpec = tween(durationMillis = TAB_IN_MS, easing = Motion.emphasized),
        initialOffsetX = { (it * TAB_TRAVEL * dir).roundToInt() },
    ) + fadeIn(animationSpec = tween(durationMillis = TAB_IN_MS - 60, easing = Motion.standard))
    fun tabOut(dir: Int): ExitTransition = slideOutHorizontally(
        animationSpec = tween(durationMillis = TAB_IN_MS, easing = Motion.emphasized),
        targetOffsetX = { (-it * TAB_TRAVEL * dir).roundToInt() },
    ) + fadeOut(animationSpec = tween(durationMillis = TAB_OUT_MS, easing = Motion.standard))

    /* A push: forward 12%, fade in; the screen behind steps back 6% and dims. */
    val pushIn: EnterTransition = slideInHorizontally(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        initialOffsetX = { (it * PUSH_TRAVEL * forwardSign).roundToInt() },
    ) + fadeIn(animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard)) +
        scaleIn(initialScale = 0.985f, animationSpec = tween(Motion.medium, easing = Motion.emphasized))

    val pushBack: ExitTransition = slideOutHorizontally(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        targetOffsetX = { (-it * PUSH_REST * forwardSign).roundToInt() },
    ) + fadeOut(
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard),
        targetAlpha = 0.92f,
    ) + scaleOut(targetScale = 0.985f, animationSpec = tween(Motion.medium, easing = Motion.emphasized))

    /* The exact mirror: the screen behind comes back, the pushed one leaves. */
    val popBackIn: EnterTransition = slideInHorizontally(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        initialOffsetX = { (-it * PUSH_REST * forwardSign).roundToInt() },
    ) + fadeIn(
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard),
        initialAlpha = 0.92f,
    ) + scaleIn(initialScale = 0.985f, animationSpec = tween(Motion.medium, easing = Motion.emphasized))

    val popOut: ExitTransition = slideOutHorizontally(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        targetOffsetX = { (it * PUSH_TRAVEL * forwardSign).roundToInt() },
    ) + fadeOut(animationSpec = tween(durationMillis = Motion.medium, easing = Motion.standard)) +
        scaleOut(targetScale = 0.985f, animationSpec = tween(Motion.medium, easing = Motion.emphasized))

    /* The composer rises from the bottom edge and settles back down. */
    val createIn: EnterTransition = slideInVertically(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        initialOffsetY = { it / 6 },
    ) + fadeIn(animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard))

    val createOut: ExitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        targetOffsetY = { it / 6 },
    ) + fadeOut(
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard),
        targetAlpha = 0f,
    )

    /* The rail slides its own width out; the pill drops out of the bottom edge.
     * Both are handed to file-scope wrappers below — inside a Row or a Box the
     * scoped overloads of AnimatedVisibility would shadow the plain one, and a
     * wrapper has no receiver to shadow anything. */
    val railIn: EnterTransition = expandHorizontally(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
    )
    val railOut: ExitTransition = shrinkHorizontally(
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.emphasized),
    )
    val pillIn: EnterTransition = slideInVertically(
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        initialOffsetY = { it },
    ) + fadeIn(animationSpec = tween(durationMillis = Motion.short, easing = Motion.standard))
    val pillOut: ExitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = Motion.short, easing = Motion.emphasized),
        targetOffsetY = { it },
    ) + fadeOut(animationSpec = tween(durationMillis = Motion.instant))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < Dimens.compactWidth

        Row(Modifier.fillMaxSize().clipToBounds()) {
            if (!compact) {
                RailReveal(
                    visible = shellVisible,
                    enter = railIn,
                    exit = railOut,
                ) {
                    PinBomRail(
                        items = tabs,
                        currentRoute = currentRoute,
                        onSelect = { open(it.route) },
                        baseUrl = session.baseUrl,
                        onLogoClick = { open(Destination.Home.route) },
                        style = navStyle,
                    )
                }
            }

            /* One Box owns the shell and the floating pill. The pill has to
             * sit *outside* the Scaffold's content insets: it adds its own
             * navigation-bar padding, and inside those insets the system bar
             * would be counted twice. */
            Box(
                Modifier.weight(1f).fillMaxHeight().tabSwipe(
                    enabled = shellVisible && !reduce,
                    onDrag = { total ->
                        if (settleJob[0] != null) cancelSettle()
                        val idx = Tabs.indexOfFirst { it.route == currentRoute }
                        val logical = if (rtl) total else -total   // >0 → towards next tab
                        val atEdge = (logical > 0f && idx == Tabs.lastIndex) || (logical < 0f && idx == 0)
                        dragPx.floatValue = total * (if (atEdge) 0.12f else 0.38f)
                    },
                    onSettle = { settleDrag() },
                    onSwipe = { physicalStep ->
                        val currentIndex = Tabs.indexOfFirst { it.route == currentRoute }
                        if (currentIndex >= 0) {
                            val logicalStep = if (rtl) -physicalStep else physicalStep
                            val nextIndex = (currentIndex + logicalStep).coerceIn(0, Tabs.lastIndex)
                            if (nextIndex != currentIndex) open(Tabs[nextIndex].route)
                        }
                    },
                ),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = colors.background,
                ) { innerPadding ->
                    /* The NavHost no longer reserves room for the bar: the pill
                     * floats over the content, so a wall keeps its images to the very
                     * bottom of the screen and the tab screens add the matching
                     * [Dimens.navBarSpace] to their own end padding. */
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (reduce || !compact) Modifier else Modifier.nestedScroll(barScroll.connection))
                            .background(colors.background),
                    ) {
                        val renderNavHost: @Composable (SharedTransitionScope?) -> Unit = { activeSharedScope ->
                        NavHost(
                            navController = nav,
                            startDestination = Destination.Home.route,
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .graphicsLayer { translationX = dragPx.floatValue },
                            enterTransition = {
                                when {
                                    reduce -> EnterTransition.None
                                    isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route) ->
                                        tabIn(tabDir(initialState.destination.route, targetState.destination.route))
                                    targetState.destination.route == Destination.Create.route -> createIn
                                    else -> pushIn
                                }
                            },
                            exitTransition = {
                                when {
                                    reduce -> ExitTransition.None
                                    isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route) ->
                                        tabOut(tabDir(initialState.destination.route, targetState.destination.route))
                                    else -> pushBack
                                }
                            },
                            popEnterTransition = {
                                when {
                                    reduce -> EnterTransition.None
                                    isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) ->
                                        tabIn(tabDir(initialState.destination.route, targetState.destination.route))
                                    else -> popBackIn
                                }
                            },
                            popExitTransition = {
                                when {
                                    reduce -> ExitTransition.None
                                    isTabRoute(initialState.destination.route) && isTabRoute(targetState.destination.route) ->
                                        tabOut(tabDir(initialState.destination.route, targetState.destination.route))
                                    initialState.destination.route == Destination.Create.route -> createOut
                                    else -> popOut
                                }
                            },
                        ) {
                            composable(Destination.Home.route) {
                                SharedPinRoute(activeSharedScope, this) { HomeScreen(nav, session) }
                            }
                            composable(Destination.Search.route) { SearchScreen(nav, session) }
                            composable(Destination.Create.route) {
                                CreatePinScreen(
                                    session,
                                    uploads = uploads,
                                    initialImageUri = initialSharePath?.takeIf { File(it).isFile }?.let { Uri.fromFile(File(it)) },
                                    onInitialImageConsumed = onSharePathConsumed,
                                )
                            }
                            composable(Destination.Notifications.route) { NotificationsScreen(nav, session) }
                            composable(Destination.Profile.route) {
                                SharedPinRoute(activeSharedScope, this) { ProfileScreen(nav, session) }
                            }

                            composable(Routes.PROFILE_ARG) { entry ->
                                SharedPinRoute(activeSharedScope, this) {
                                    ProfileScreen(
                                        nav,
                                        session,
                                        entry.arguments?.getString(Routes.ARG_USERNAME).orEmpty(),
                                    )
                                }
                            }
                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    nav = nav,
                                    session = session,
                                    dark = dark,
                                    onDarkChange = onDarkChange,
                                    accentColor = accentColor,
                                    onAccentChange = onAccentChange,
                                    specialEffectsEnabled = specialEffectsEnabled,
                                    onSpecialEffectsChange = onSpecialEffectsChange,
                                    onLogout = onLogout,
                                )
                            }
                            composable(Routes.EDIT_INTERESTS) {
                                InterestsScreen(
                                    editMode = true,
                                    nav = nav,
                                    onDone = { nav.popBackStack() },
                                    onSkip = { nav.popBackStack() },
                                )
                            }
                            composable(Routes.ACTIVE_SESSIONS) {
                                ActiveSessionsScreen(nav = nav, onLogout = onLogout)
                            }
                            composable(Routes.BLOCKED) { BlockedUsersScreen(nav, session) }
                            composable(Routes.PUSH_PREFS) { PushPrefsScreen(nav) }
                            composable(Routes.PEOPLE_ARG) { entry ->
                                PeopleScreen(
                                    nav = nav,
                                    username = entry.arguments?.getString(Routes.ARG_USERNAME).orEmpty(),
                                    kind = entry.arguments?.getString(Routes.ARG_KIND).orEmpty(),
                                    session = session,
                                )
                            }
                            composable(Routes.BOARDS) { BoardsScreen(nav, session) }
                            composable(Routes.BOARD_ARG) { entry ->
                                SharedPinRoute(activeSharedScope, this) {
                                    BoardScreen(
                                        nav,
                                        entry.arguments?.getString(Routes.ARG_ID)?.toLongOrNull() ?: 0L,
                                        session,
                                    )
                                }
                            }
                            composable(Routes.TAG_ARG) { entry ->
                                TagResultsScreen(nav, entry.arguments?.getString(Routes.ARG_TAG).orEmpty(), session)
                            }
                            composable(Routes.PIN_ARG) { entry ->
                                SharedPinRoute(activeSharedScope, this) {
                                    PinDetailScreen(
                                        nav,
                                        entry.arguments?.getString(Routes.ARG_ID)?.toLongOrNull() ?: 0L,
                                        session,
                                    )
                                }
                            }
                            composable(Routes.SIMILAR_ARG) { entry ->
                                SimilarPinsScreen(
                                    nav = nav,
                                    pinId = entry.arguments?.getString(Routes.ARG_ID)?.toLongOrNull() ?: 0L,
                                    session = session,
                                )
                            }
                        }
                        }
                        val routeNeedsSharedTransition = currentRoute in setOf(
                            Destination.Home.route,
                            Destination.Profile.route,
                            Routes.PROFILE_ARG,
                            Routes.BOARD_ARG,
                            Routes.PIN_ARG,
                        )
                        if (PIN_SHARED_TRANSITIONS_ENABLED && specialEffectsEnabled && !reduce && routeNeedsSharedTransition) {
                            SharedTransitionLayout { renderNavHost(this) }
                        } else {
                            renderNavHost(null)
                        }

                        /* The upload card: floating, never in the way, below the
                         * status bar and above every screen for as long as the upload
                         * is unfinished — that is what makes it survive navigation.
                         * The progress stream is collected *inside* the overlay, so a
                         * tick redraws the card instead of recomposing the shell. */
                        UploadOverlay(
                            uploads = uploads,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                    top = innerPadding.calculateTopPadding() + Dimens.sm,
                                    start = Dimens.screen,
                                    end = Dimens.screen,
                                ),
                        )
                    }
                }

                /* The floating pill: over the content, under nothing, and
                 * perfectly still while the user switches tabs. It only travels
                 * when the shell itself leaves (tab → detail). */
                if (compact) {
                    BarReveal(
                        visible = shellVisible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val h = barScroll.heightPx
                                val hidden = if (h <= 0f) 0f else (-barScroll.offsetPx / h).coerceIn(0f, 1f)
                                translationY = hidden * size.height
                            },
                        enter = pillIn,
                        exit = pillOut,
                    ) {
                        PinBomBottomBar(
                            items = tabs,
                            currentRoute = currentRoute,
                            onSelect = { open(it.route) },
                            baseUrl = session.baseUrl,
                            style = navStyle,
                        )
                    }
                }
            }
        }
    }
}

/* ────────────────────────────────────────────────────────────────────────────
 *  The upload card, walled off from the shell.
 *
 *  Upload progress arrives as WorkInfo updates: several per second, per file.
 *  Reading that stream here rather than in [AppNavShell] keeps each tick inside
 *  this one composable — the shell, the NavHost and the bar do not recompose for
 *  a progress bar they never read.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun UploadOverlay(
    uploads: UploadViewModel,
    modifier: Modifier = Modifier,
) {
    val state by uploads.state.collectAsStateWithLifecycle()
    UploadProgressHost(
        state = state,
        onCancel = uploads::cancel,
        onRetry = uploads::retry,
        onDismiss = uploads::dismiss,
        modifier = modifier,
    )
}

/* ────────────────────────────────────────────────────────────────────────────
 *  The two chrome reveals, at file scope on purpose.
 *
 *  `AnimatedVisibility` is declared three times — once plain, once on RowScope
 *  and once on ColumnScope — and inside a Row or a Column the scoped overloads
 *  win. These wrappers take the transitions as arguments instead, so the call
 *  site cannot be ambiguous and the motion lives in one place.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun RailReveal(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = enter, exit = exit) { content() }
}

@Composable
private fun BarReveal(
    visible: Boolean,
    modifier: Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) { content() }
}

/* ────────────────────────────────────────────────────────────────────────────
 *  Tab swipe
 *  Watches on the *Initial* pass and never consumes anything until the gesture
 *  is clearly horizontal (touch slop reached, |dx| > 1.5·|dy|), so vertical
 *  scrolling of the feed is never delayed or stolen. Commit is by distance
 *  *or* fling velocity — a quick flick is enough. The pointer block is keyed
 *  on `enabled` only; the callback is read through rememberUpdatedState, so a
 *  tab change no longer tears the gesture detector down.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun Modifier.tabSwipe(
    enabled: Boolean,
    onDrag: (total: Float) -> Unit,
    onSettle: () -> Unit,
    onSwipe: (physicalStep: Int) -> Unit,
): Modifier {
    val latest by rememberUpdatedState(onSwipe)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestSettle by rememberUpdatedState(onSettle)
    val viewConfig = LocalViewConfiguration.current
    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val commitDistance = size.width * 0.14f
        val commitVelocity = 900f // px/s
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = 0f
            var vertical = 0f
            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                // Only claim the gesture if it is mostly horizontal.
                val dy = kotlin.math.abs(change.position.y - down.position.y)
                if (dy < kotlin.math.abs(change.position.x - down.position.x) / 1.5f) {
                    change.consume()
                    total += over
                } else {
                    vertical = dy
                }
            }
            if (slop == null || vertical > 0f && total == 0f) return@awaitEachGesture
            tracker.addPosition(slop.uptimeMillis, slop.position)
            latestDrag(total)
            horizontalDrag(slop.id) { change ->
                total += change.positionChange().x
                latestDrag(total)
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
            }
            val vx = tracker.calculateVelocity().x
            val committed = kotlin.math.abs(total) >= commitDistance ||
                (kotlin.math.abs(vx) >= commitVelocity && kotlin.math.abs(total) > viewConfig.touchSlop * 2)
            if (committed) latest(if (total < 0f) 1 else -1)
            latestSettle()
        }
    }
}
