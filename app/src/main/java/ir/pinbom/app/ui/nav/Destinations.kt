package ir.pinbom.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.pinbom.app.R
import ir.pinbom.app.ui.components.PinBomNavItem
import ir.pinbom.app.ui.components.icons.PinBomNavIcons

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · the route table, in one place
 *  These strings are the contract with every deep link, shortcut and
 *  `nav.navigate(...)` call in the app. They existed before the redesign and
 *  they are not allowed to change — only the shell around them did.
 * ──────────────────────────────────────────────────────────────────────────── */

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CREATE = "create"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"

    const val SETTINGS = "settings"
    const val BOARDS = "boards"
    const val EDIT_INTERESTS = "settings/interests"
    const val ACTIVE_SESSIONS = "settings/sessions"
    const val BLOCKED = "settings/blocked"
    const val PUSH_PREFS = "settings/push"

    /** `people/{username}/{kind}` — kind is `followers` or `following`. */
    const val PEOPLE_ARG = "people/{username}/{kind}"

    /** `profile/{username}` */
    const val PROFILE_ARG = "profile/{username}"

    /** `board/{id}` */
    const val BOARD_ARG = "board/{id}"

    /** `pin/{id}` */
    const val PIN_ARG = "pin/{id}"
    const val SIMILAR_ARG = "similar/{id}"
    const val TAG_ARG = "tag/{tag}"

    /** Argument names, used by both the graph and the deep links. */
    const val ARG_USERNAME = "username"
    const val ARG_ID = "id"
    const val ARG_TAG = "tag"
    const val ARG_KIND = "kind"

    fun profile(username: String) = "$PROFILE/$username"
    fun people(username: String, kind: String) = "people/${Uri.encode(username)}/$kind"
    fun board(id: Long) = "board/$id"
    fun pin(id: Long) = "pin/$id"
    fun similar(id: Long = 0L) = "similar/$id"
    fun tag(tag: String) = "tag/${Uri.encode(tag.trimStart('#'))}"
}

/** The five shell destinations, in visual order. */
sealed class Destination(val route: String, val labelRes: Int) {
    data object Home : Destination(Routes.HOME, R.string.nav_home)
    data object Search : Destination(Routes.SEARCH, R.string.nav_search)
    data object Create : Destination(Routes.CREATE, R.string.nav_create)
    data object Notifications : Destination(Routes.NOTIFICATIONS, R.string.nav_notifications)
    data object Profile : Destination(Routes.PROFILE, R.string.nav_profile)
}

val Tabs: List<Destination> = listOf(
    Destination.Home,
    Destination.Search,
    Destination.Create,
    Destination.Notifications,
    Destination.Profile,
)

/** Routes that keep the shell (bottom bar / rail) visible. */
private val TabRouteSet: Set<String> = Tabs.map { it.route }.toSet()

/** Every other route is a pushed detail and hides the shell. */
fun isTabRoute(route: String?): Boolean = route != null && route in TabRouteSet

/**
 * The shell items. The profile destination is drawn as the user’s avatar —
 * [profileName] feeds the initials (and its deterministic colour), while
 * [profileUrl] supplies the uploaded avatar when one is available.
 */
@Composable
fun pinbomTabs(profileName: String?, profileUrl: String? = null): List<PinBomNavItem> = listOf(
    PinBomNavItem(
        route = Destination.Home.route,
        label = stringResource(R.string.nav_home),
        icon = PinBomNavIcons.Home,
        selectedIcon = PinBomNavIcons.HomeFilled,
    ),
    PinBomNavItem(
        route = Destination.Search.route,
        label = stringResource(R.string.nav_search),
        icon = PinBomNavIcons.Search,
        selectedIcon = PinBomNavIcons.SearchBold,
    ),
    PinBomNavItem(
        route = Destination.Create.route,
        label = stringResource(R.string.nav_create),
        icon = PinBomNavIcons.Create,
        selectedIcon = PinBomNavIcons.CreateFilled,
    ),
    PinBomNavItem(
        route = Destination.Notifications.route,
        label = stringResource(R.string.nav_notifications),
        icon = PinBomNavIcons.Notifications,
        selectedIcon = PinBomNavIcons.NotificationsFilled,
    ),
    PinBomNavItem(
        route = Destination.Profile.route,
        label = stringResource(R.string.nav_profile),
        icon = PinBomNavIcons.Profile,
        selectedIcon = PinBomNavIcons.ProfileFilled,
        isProfile = true,
        avatarName = profileName,
        avatarUrl = profileUrl,
    ),
)
