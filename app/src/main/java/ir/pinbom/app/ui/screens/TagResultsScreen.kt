package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.SessionManager
import ir.pinbom.app.ui.TagResultsViewModel
import ir.pinbom.app.ui.components.PinCard
import ir.pinbom.app.ui.components.icons.PinBomIcons
import ir.pinbom.app.ui.components.ErrorState
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.nav.Routes

@Composable
fun TagResultsScreen(
    nav: NavHostController,
    tag: String,
    session: SessionManager,
    vm: TagResultsViewModel = hiltViewModel(),
) {
    LaunchedEffect(tag) { vm.load(tag) }
    val items by vm.items.collectAsStateWithLifecycle()
    val cursor by vm.cursor.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            IconButton(onClick = { nav.popBackStack() }) { androidx.compose.material3.Icon(PinBomIcons.ArrowBack, contentDescription = null) }
            Text("#${tag.removePrefix("#")}", style = PinBomText.titleLarge, color = PinBomTheme.colors.textPrimary)
        }
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            failed && items.isEmpty() -> ErrorState(onRetry = { vm.load(tag) }, modifier = Modifier.fillMaxSize())
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Dimens.grid, end = Dimens.grid, bottom = Dimens.huge + Dimens.navBarSpace),
                horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                verticalItemSpacing = Dimens.gridGap,
            ) {
                items(items = items, key = { it.id }, contentType = { "pin" }) { pin ->
                    PinCard(pin = pin, baseUrl = session.baseUrl, onClick = { nav.navigate(Routes.pin(pin.id)) })
                }
                if (cursor.isNotBlank()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "tag-load-more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { vm.loadMore() }, enabled = !loadingMore) { Text(stringResource(R.string.action_load_more)) }
                        }
                    }
                }
                if (!loading && items.isEmpty() && !failed) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "tag-empty") {
                        Box(Modifier.fillMaxWidth().padding(Dimens.lg), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.search_no_results_title), color = PinBomTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
