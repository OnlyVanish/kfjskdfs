package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.data.remote.ActiveSession
import ir.pinbom.app.ui.ActiveSessionsViewModel
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PinBomDialog
import ir.pinbom.app.ui.components.PinBomDialogContent
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.components.SectionHeader
import ir.pinbom.app.ui.theme.Localizer
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme

@Composable
fun ActiveSessionsScreen(
    nav: NavHostController,
    onLogout: () -> Unit,
    vm: ActiveSessionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmOthers by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimens.topBar).padding(horizontal = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = PinBomIcons.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = { nav.popBackStack() },
                containerColor = PinBomTheme.colors.surfaceGrey,
            )
            Spacer(Modifier.width(Dimens.sm))
            Text(
                text = stringResource(R.string.settings_active_sessions),
                style = PinBomText.titleLarge,
                color = PinBomTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            item { SectionHeader(title = stringResource(R.string.settings_active_sessions)) }
            if (state.error != null) item {
                Text(state.error.orEmpty(), modifier = Modifier.padding(horizontal = Dimens.screen), color = MaterialTheme.colorScheme.error)
            }
            if (state.items.isEmpty() && !state.loading) item {
                Text(stringResource(R.string.settings_no_sessions), modifier = Modifier.padding(horizontal = Dimens.screen), color = PinBomTheme.colors.textSecondary)
            }
            items(state.items, key = { it.id }) { item ->
                SessionRow(
                    item = item,
                    enabled = !state.busy,
                    onRevoke = { vm.revoke(item, onLogout) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = Dimens.screen, vertical = Dimens.md)) {
                    PrimaryPillButton(
                        text = stringResource(R.string.settings_revoke_other_sessions),
                        onClick = { confirmOthers = true },
                        enabled = state.items.any { !it.current } && !state.busy,
                        loading = state.busy,
                        fillWidth = true,
                    )
                    Spacer(Modifier.height(Dimens.huge))
                }
            }
        }
    }

    if (confirmOthers) {
        PinBomDialog(onDismissRequest = { confirmOthers = false }) {
            PinBomDialogContent(
                title = stringResource(R.string.settings_revoke_other_sessions),
                body = stringResource(R.string.settings_revoke_other_sessions_confirm),
                confirmLabel = stringResource(R.string.settings_revoke),
                onConfirm = {
                    confirmOthers = false
                    vm.revokeOthers()
                },
                onDismiss = { confirmOthers = false },
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
            )
        }
    }
}

@Composable
private fun SessionRow(item: ActiveSession, enabled: Boolean, onRevoke: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screen, vertical = Dimens.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = PinBomIcons.Devices,
                contentDescription = null,
                tint = PinBomTheme.colors.textSecondary,
            )
            Spacer(Modifier.width(Dimens.sm))
            Text(
                text = item.device_name.ifBlank { stringResource(R.string.settings_unknown_device) },
                style = PinBomText.body,
                color = PinBomTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (item.current) {
                Text(stringResource(R.string.settings_this_device), style = PinBomText.bodySmall, color = PinBomTheme.colors.primary)
            }
        }
        Text(
            text = stringResource(R.string.settings_last_active, Localizer.relativeTime(item.last_seen_at)),
            style = PinBomText.bodySmall,
            color = PinBomTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 28.dp, top = Dimens.xs),
        )
        if (item.ip.isNotBlank()) {
            Text(item.ip, style = PinBomText.bodySmall, color = PinBomTheme.colors.textTertiary, modifier = Modifier.padding(start = 28.dp))
        }
        SecondaryPillButton(
            text = stringResource(if (item.current) R.string.settings_logout_this_device else R.string.settings_revoke),
            onClick = onRevoke,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm),
        )
    }
}
