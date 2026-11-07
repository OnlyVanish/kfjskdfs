package ir.pinbom.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import ir.pinbom.app.R
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.reduceMotion

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · search bar
 *  48dp grey pill with a search glyph, an animated clear button and an optional
 *  “انصراف” that slides in on focus. Built on BasicTextField so the height is
 *  exact — Material’s TextField cannot go below 56dp without clipping.
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
fun PinBomSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.cd_search_field),
    onSearch: () -> Unit = {},
    onClear: () -> Unit = {},
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    requester: FocusRequester? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion()
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val localFocus = remember { FocusRequester() }
    val activeRequester = requester ?: localFocus
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { activeRequester.requestFocus() }
    }

    val showCancel = cancelText != null && onCancel != null && (focused || value.isNotEmpty())

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.touch)
                .clip(PinBomShape.pill)
                .background(colors.surfaceGrey)
                .padding(horizontal = Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PinBomIcons.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Dimens.sm))

            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = PinBomText.body,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(activeRequester)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = PinBomText.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            onSearch()
                        },
                    ),
                    interactionSource = interaction,
                )
            }

            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = if (reduce) EnterTransition.None else fadeIn(tween(Motion.short)) + scaleIn(initialScale = 0.7f),
                exit = if (reduce) ExitTransition.None else fadeOut(tween(Motion.instant)) + scaleOut(targetScale = 0.7f),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .pinbomClick(
                            source = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, color = colors.textSecondary),
                            role = Role.Button,
                            pressedScale = 0.90f,
                            onClick = {
                                onValueChange("")
                                onClear()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PinBomIcons.Close,
                        contentDescription = stringResource(R.string.cd_clear_query),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(Dimens.xs))
                trailingContent()
            }
        }

        AnimatedVisibility(
            visible = showCancel,
            enter = if (reduce) EnterTransition.None else fadeIn(tween(Motion.short)) + scaleIn(initialScale = 0.9f),
            exit = if (reduce) ExitTransition.None else fadeOut(tween(Motion.instant)) + scaleOut(targetScale = 0.9f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Dimens.md))
                Text(
                    text = cancelText.orEmpty(),
                    style = PinBomText.button,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .clip(PinBomShape.pill)
                        .pinbomClick(
                            source = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, color = colors.textPrimary),
                            role = Role.Button,
                            pressedScale = 0.97f,
                            onClick = {
                                keyboard?.hide()
                                focused = false
                                onCancel?.invoke()
                            },
                        )
                        .padding(vertical = Dimens.xs, horizontal = Dimens.xxs),
                )
            }
        }
    }
}

/** Static, non-interactive variant used in the empty “discover” state. */
@Composable
fun PinBomSearchBarPlaceholder(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PinBomTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.touch)
            .clip(PinBomShape.pill)
            .background(colors.surfaceGrey)
            .pinbomClick(
                source = remember { MutableInteractionSource() },
                indication = ripple(color = colors.textSecondary),
                role = Role.Button,
                pressedScale = 0.985f,
                onClick = onClick,
            )
            .padding(horizontal = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PinBomIcons.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Dimens.sm))
        Text(
            text = text,
            style = PinBomText.body,
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Composable
private fun SearchGallery(dark: Boolean) {
    PinBomTheme(darkTheme = dark) {
        Column(
            Modifier
                .background(PinBomTheme.colors.background)
                .padding(Dimens.screen),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            PinBomSearchBar(value = "", onValueChange = {})
            PinBomSearchBar(
                value = "گیاهان آپارتمانی",
                onValueChange = {},
                cancelText = "انصراف",
                onCancel = {},
            )
            PinBomSearchBarPlaceholder(text = "جستجو در پینبوم", onClick = {})
        }
    }
}

@Preview(name = "SearchBar · light", backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun PreviewSearchLight() = SearchGallery(dark = false)

@Preview(name = "SearchBar · dark", backgroundColor = 0xFF121212, widthDp = 400)
@Composable
private fun PreviewSearchDark() = SearchGallery(dark = true)
