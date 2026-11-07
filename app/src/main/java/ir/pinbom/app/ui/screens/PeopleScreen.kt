package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.ui.PeopleViewModel
import ir.pinbom.app.ui.components.EmptyState
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.components.icons.PinBomIcons
import ir.pinbom.app.ui.theme.skeleton
import ir.pinbom.app.ui.nav.Routes
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp

/** Followers or following of [username] (`kind` = followers | following). */
@Composable
fun PeopleScreen(
    nav: NavHostController,
    username: String,
    kind: String,
    session: SessionManager,
    vm: PeopleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(username, kind) { vm.load(username, kind) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.items.size - 4) vm.loadMore() }
    }
    val following = kind == PeopleViewModel.KIND_FOLLOWING

    Column(Modifier.fillMaxSize()) {
        ExtrasTopBar(
            title = stringResource(if (following) R.string.profile_stat_following else R.string.profile_stat_followers),
            onBack = { nav.popBackStack() },
        )
        when {
            state.loading -> PeopleSkeleton()
            state.failed -> ErrorState(onRetry = { vm.load(username, kind, force = true) }, modifier = Modifier.fillMaxSize())
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(if (following) R.string.people_empty_following else R.string.people_empty_followers),
                    icon = PinBomIcons.Person,
                    compact = true,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.items, key = { it.id }) { person ->
                    PersonRowItem(
                        person = person,
                        baseUrl = session.baseUrl,
                        onClick = { nav.navigate(Routes.profile(person.username)) },
                        trailing = {
                            Icon(PinBomIcons.ChevronForward, contentDescription = null, tint = PinBomTheme.colors.textTertiary)
                        },
                    )
                }
                item { Spacer(Modifier.height(Dimens.huge)) }
            }
        }
    }
}

@Composable
private fun PeopleSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = Dimens.md)) {
        repeat(8) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Dimens.huge).skeleton(CircleShape))
                Spacer(Modifier.padding(horizontal = Dimens.sm))
                Column {
                    Box(Modifier.size(width = 140.dp, height = 14.dp).skeleton())
                    Spacer(Modifier.height(Dimens.xs))
                    Box(Modifier.size(width = 90.dp, height = 12.dp).skeleton())
                }
            }
        }
    }
}
