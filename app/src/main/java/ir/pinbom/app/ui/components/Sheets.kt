package ir.pinbom.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.Dimens

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · sheets and dialogs
 *  32dp radius, a drag handle, no tonal elevation and pill buttons — the two
 *  surfaces allowed to float above content. Everything modal in the app comes
 *  from here so the radius can never drift.
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Standard bottom sheet. [content] receives a [ColumnScope] so callers can just
 * stack rows; the sheet is already padded and the content is scroll-safe as long
 * as the caller keeps it bounded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinBomBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        start = Dimens.xl,
        end = Dimens.xl,
        top = Dimens.xs,
        bottom = Dimens.xxl,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PinBomTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = PinBomSheetMaxWidth,
        shape = PinBomShape.sheet,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = if (showDragHandle) {
            { PinBomDragHandle() }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Neutral grip: 36×4 in grey, no Material tokens involved. */
@Composable
fun PinBomDragHandle(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 36.dp,
    height: androidx.compose.ui.unit.Dp = 4.dp,
) {
    val colors = PinBomTheme.colors
    Box(
        modifier = modifier
            .padding(vertical = Dimens.md)
            .width(width)
            .height(height)
            .background(colors.surfaceGreyPressed, PinBomShape.pill),
    )
}

/** Sheet title line: bold title, optional secondary line, trailing action. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** True when [title] is user content (a pin title): it aligns by its own script. */
    contentDirection: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PinBomTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (contentDirection) {
                ContentText(
                    text = title,
                    style = PinBomText.title,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(text = title, style = PinBomText.title, color = colors.textPrimary)
            }
            if (subtitle != null) {
                Text(text = subtitle, style = PinBomText.caption, color = colors.textSecondary)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Dialog built from scratch: 32dp radius, hairline-free, one or two pill
 * buttons at the end. No tonal elevation, so it reads as the same surface as a
 * sheet rather than a raised Material card.
 */
@Composable
fun PinBomDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.90f,
        animationSpec = if (reduce) snap() else androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "pinBomDialogScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduce) snap() else tween(Motion.short),
        label = "pinBomDialogAlpha",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Column(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .widthIn(min = 280.dp, max = 400.dp)
                .background(colors.surface, PinBomShape.dialog)
                .padding(Dimens.xxl),
            content = content,
        )
    }
}

/** Title + body + a row of pill buttons (primary first, then secondary). */
@Composable
fun PinBomDialogContent(
    title: String,
    body: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    val colors = PinBomTheme.colors

    Text(text = title, style = PinBomText.title, color = colors.textPrimary)
    if (body != null) {
        Spacer(Modifier.height(Dimens.sm))
        Text(text = body, style = PinBomText.bodySmall, color = colors.textSecondary)
    }
    Spacer(Modifier.height(Dimens.xl))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dismissLabel != null) {
            SecondaryPillButton(
                text = dismissLabel,
                onClick = onDismiss,
                size = PillButtonSize.Small,
            )
        }
        PrimaryPillButton(
            text = confirmLabel,
            onClick = onConfirm,
            size = PillButtonSize.Small,
            containerColor = if (destructive) MaterialTheme.colorScheme.error else colors.primary,
        )
    }
}

private val PinBomSheetMaxWidth = 640.dp

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Preview(name = "Dialog · light", backgroundColor = 0xFFEFEFEF, widthDp = 400, heightDp = 420)
@Composable
private fun PreviewDialogLight() {
    PinBomTheme(darkTheme = false) {
        PinBomDialog(onDismissRequest = {}) {
            PinBomDialogContent(
                title = "حذف حساب؟",
                body = "تمام محتوای حساب شما حذف می‌شود و این عمل قابل بازگشت نیست.",
                confirmLabel = "حذف حساب",
                onConfirm = {},
                onDismiss = {},
                dismissLabel = "انصراف",
            )
        }
    }
}

@Preview(name = "Dialog · dark", backgroundColor = 0xFF121212, widthDp = 400, heightDp = 420)
@Composable
private fun PreviewDialogDark() {
    PinBomTheme(darkTheme = true) {
        PinBomDialog(onDismissRequest = {}) {
            PinBomDialogContent(
                title = "خروج از حساب؟",
                body = "برای استفاده دوباره باید وارد شوید.",
                confirmLabel = "خروج",
                onConfirm = {},
                onDismiss = {},
                dismissLabel = "انصراف",
            )
        }
    }
}

@Preview(name = "Sheet content · light", backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun PreviewSheetContentLight() {
    PinBomTheme(darkTheme = false) {
        Column(Modifier.padding(Dimens.xl)) {
            PinBomDragHandle()
            SheetHeader(title = "ذخیره در بورد", subtitle = "یک بورد انتخاب کنید")
            BoardRow(title = "دکوراسیون خانه", pinCountLabel = "۱۲ پین", seed = 2, onClick = {})
            BoardRow(title = "دستور پخت", pinCountLabel = "۴۸ پین", seed = 6, onClick = {}, selected = true)
            BoardCreateRow(title = "ساخت بورد جدید", onClick = {})
        }
    }
}
