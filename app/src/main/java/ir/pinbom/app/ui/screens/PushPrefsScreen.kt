package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.ui.PushPrefsViewModel
import ir.pinbom.app.ui.components.ActionToastHost
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.fx.FxAnimatedIcon
import ir.pinbom.app.ui.components.SectionHeader
import ir.pinbom.app.ui.components.icons.PinBomIcons
import ir.pinbom.app.ui.components.rememberPinBomToastState
import ir.pinbom.app.ui.theme.Dimens
import androidx.compose.ui.unit.dp

/**
 * Server-side push switches (`/notifications/preferences`). The in-app
 * notification-type toggles in Settings only filter what the app shows; these
 * decide what the server is allowed to push at all.
 */
@Composable
fun PushPrefsScreen(
    nav: NavHostController,
    vm: PushPrefsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal by vm.signal.collectAsStateWithLifecycle()
    val toast = rememberPinBomToastState()
    LaunchedEffect(Unit) { vm.load() }
    ExtrasSignalToast(signal, toast)
    val prefs = state.prefs

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ExtrasTopBar(stringResource(R.string.push_title), onBack = { nav.popBackStack() })
            if (state.failed) {
                ErrorState(onRetry = { vm.load() }, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().padding(top = Dimens.md), contentAlignment = Alignment.Center) {
                        /* XML AnimatedVectorDrawable: the bell swings when pushes are switched on. */
                        FxAnimatedIcon(avd = R.drawable.avd_bell_ring, atEnd = prefs.push_enabled, size = 64.dp)
                    }
                    SectionHeader(title = stringResource(R.string.push_title))
                    Column(Modifier.padding(horizontal = Dimens.screen)) {
                        ToggleRow(
                            icon = PinBomIcons.Notifications,
                            title = stringResource(R.string.push_master),
                            subtitle = stringResource(R.string.push_master_hint),
                            checked = prefs.push_enabled,
                            enabled = !state.loading,
                            onCheckedChange = { v -> vm.change { it.copy(push_enabled = v) } },
                        )
                        val sub = prefs.push_enabled && !state.loading
                        ToggleRow(PinBomIcons.Favorite, stringResource(R.string.settings_notify_likes), prefs.likes_enabled, { v -> vm.change { it.copy(likes_enabled = v) } }, sub)
                        ToggleRow(PinBomIcons.ModeComment, stringResource(R.string.settings_notify_comments), prefs.comments_enabled, { v -> vm.change { it.copy(comments_enabled = v) } }, sub)
                        ToggleRow(PinBomIcons.PersonAdd, stringResource(R.string.settings_notify_follows), prefs.follows_enabled, { v -> vm.change { it.copy(follows_enabled = v) } }, sub)
                        ToggleRow(PinBomIcons.Tag, stringResource(R.string.push_mentions), prefs.mentions_enabled, { v -> vm.change { it.copy(mentions_enabled = v) } }, sub)
                        ToggleRow(PinBomIcons.Star, stringResource(R.string.push_broadcasts), prefs.broadcasts_enabled, { v -> vm.change { it.copy(broadcasts_enabled = v) } }, sub)
                    }
                }
            }
        }
        ActionToastHost(state = toast, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
