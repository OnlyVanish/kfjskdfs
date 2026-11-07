package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import ir.pinbom.app.R
import ir.pinbom.app.data.extras.PersonRow
import ir.pinbom.app.data.extras.ReportReason
import ir.pinbom.app.ui.ExtrasEvent
import ir.pinbom.app.ui.ExtrasSignal
import ir.pinbom.app.ui.components.Avatar
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PinBomBottomSheet
import ir.pinbom.app.ui.components.PinBomChip
import ir.pinbom.app.ui.components.PinBomSwitch
import ir.pinbom.app.ui.components.PinBomTextField
import ir.pinbom.app.ui.components.PinBomToastState
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.icons.PinBomIcons
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.pinbomClick

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · shared pieces of the screens that expose backend-only features
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
internal fun ExtrasTopBar(title: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(Dimens.topBar).padding(horizontal = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(
            icon = PinBomIcons.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            containerColor = PinBomTheme.colors.surfaceGrey,
        )
        Spacer(Modifier.width(Dimens.sm))
        Text(
            text = title,
            style = PinBomText.titleLarge,
            color = PinBomTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Turns a view model's one-shot [signal] into a localized toast. */
@Composable
internal fun ExtrasSignalToast(signal: ExtrasSignal?, toast: PinBomToastState) {
    val messages = mapOf(
        ExtrasEvent.Blocked to stringResource(R.string.toast_blocked),
        ExtrasEvent.Unblocked to stringResource(R.string.toast_unblocked),
        ExtrasEvent.UserReported to stringResource(R.string.toast_user_reported),
        ExtrasEvent.CommentEdited to stringResource(R.string.toast_comment_edited),
        ExtrasEvent.CommentDeleted to stringResource(R.string.toast_comment_deleted),
        ExtrasEvent.CommentReported to stringResource(R.string.toast_comment_reported),
        ExtrasEvent.PinRemoved to stringResource(R.string.toast_pin_removed),
        ExtrasEvent.PinMoved to stringResource(R.string.toast_pin_moved),
        ExtrasEvent.PrefsSaved to stringResource(R.string.toast_prefs_saved),
        ExtrasEvent.Failed to stringResource(R.string.toast_failed),
    )
    LaunchedEffect(signal?.nonce) {
        val s = signal ?: return@LaunchedEffect
        toast.show(messages.getValue(s.event))
    }
}

/** A person in a list: avatar, names, optional trailing action. */
@Composable
internal fun PersonRowItem(
    person: PersonRow,
    baseUrl: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PinBomTheme.colors
    val name = person.display_name.ifBlank { person.username }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pinbomClick(onClick = onClick, pressedScale = 0.985f)
            .padding(horizontal = Dimens.screen, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = name, url = person.avatar_url.ifBlank { null }, baseUrl = baseUrl, size = Dimens.huge)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = PinBomText.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${person.username}", style = PinBomText.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (person.bio.isNotBlank()) {
                Text(person.bio, style = PinBomText.bodySmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Dimens.sm))
            trailing()
        }
    }
}

/** Label + switch row used by the push-preferences screen. */
@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    val colors = PinBomTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = PinBomText.body, color = if (enabled) colors.textPrimary else colors.textTertiary)
            if (subtitle != null) Text(subtitle, style = PinBomText.bodySmall, color = colors.textSecondary)
        }
        PinBomSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** One tappable line inside an action sheet. */
@Composable
internal fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = PinBomTheme.colors
    val tint = if (destructive) colors.primary else colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pinbomClick(onClick = onClick, pressedScale = 0.985f)
            .padding(vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(Dimens.md))
        Text(label, style = PinBomText.body, color = tint)
    }
}

/** Reason picker + optional note, shared by user, comment and pin reports. */
@Composable
internal fun ReportSheet(
    title: String,
    onDismiss: () -> Unit,
    busy: Boolean,
    onSubmit: (ReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf(ReportReason.Spam) }
    var note by remember { mutableStateOf("") }
    val labels = mapOf(
        ReportReason.Spam to stringResource(R.string.report_reason_spam),
        ReportReason.Nudity to stringResource(R.string.report_reason_nudity),
        ReportReason.Violence to stringResource(R.string.report_reason_violence),
        ReportReason.Copyright to stringResource(R.string.report_reason_copyright),
        ReportReason.Other to stringResource(R.string.report_reason_other),
    )
    PinBomBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = PinBomText.title, color = PinBomTheme.colors.textPrimary)
        Spacer(Modifier.height(Dimens.md))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            items(ReportReason.entries.toList()) { item ->
                PinBomChip(text = labels.getValue(item), selected = item == reason, onClick = { reason = item })
            }
        }
        Spacer(Modifier.height(Dimens.md))
        PinBomTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            placeholder = stringResource(R.string.report_note_hint),
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.lg))
        PrimaryPillButton(
            text = stringResource(R.string.report_send),
            onClick = { onSubmit(reason, note) },
            loading = busy,
            fillWidth = true,
        )
    }
}
