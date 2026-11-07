package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.ui.BlockedUsersViewModel
import ir.pinbom.app.ui.components.ActionToastHost
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.icons.PinBomIcons
import ir.pinbom.app.ui.components.rememberPinBomToastState
import ir.pinbom.app.ui.nav.Routes

/** Accounts the user blocked, with a one-tap unblock. */
@Composable
fun BlockedUsersScreen(
    nav: NavHostController,
    session: SessionManager,
    vm: BlockedUsersViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal by vm.signal.collectAsStateWithLifecycle()
    val toast = rememberPinBomToastState()
    LaunchedEffect(Unit) { vm.load() }
    ExtrasSignalToast(signal, toast)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ExtrasTopBar(stringResource(R.string.blocked_title), onBack = { nav.popBackStack() })
            when {
                state.failed -> ErrorState(onRetry = { vm.load() }, modifier = Modifier.fillMaxSize())
                state.items.isEmpty() && !state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.blocked_empty),
                        description = stringResource(R.string.blocked_empty_hint),
                        icon = PinBomIcons.Person,
                        compact = true,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { person ->
                        PersonRowItem(
                            person = person,
                            baseUrl = session.baseUrl,
                            onClick = { nav.navigate(Routes.profile(person.username)) },
                            trailing = {
                                SecondaryPillButton(
                                    text = stringResource(R.string.blocked_unblock),
                                    onClick = { vm.unblock(person) },
                                    enabled = state.busyId == 0L,
                                    loading = state.busyId == person.id,
                                )
                            },
                        )
                    }
                }
            }
        }
        ActionToastHost(state = toast, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
