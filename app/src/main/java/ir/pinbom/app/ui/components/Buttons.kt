package ir.pinbom.app.ui.components

import androidx.compose.runtime.getValue
import ir.pinbom.app.ui.theme.AbsoluteWhite

import androidx.compose.animation.Crossfade
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.BrandRedSoft
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.pressScale

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · buttons
 *  Three pill shapes and one circular icon button. Everything the app taps
 *  goes through these, so pressed states, loading states and disabled colours
 *  can never drift apart between screens.
 * ──────────────────────────────────────────────────────────────────────────── */

enum class PillButtonSize(val height: Dp, val horizontalPadding: Dp) {
    Regular(Dimens.button, 18.dp),
    Small(Dimens.buttonSmall, 14.dp),
}

/** Brand red pill: “ذخیره”, “انتشار”, “دنبال کردن”, form submits. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    size: PillButtonSize = PillButtonSize.Regular,
    fillWidth: Boolean = false,
    containerColor: Color = PinBomTheme.colors.primary,
    contentColor: Color = labelOn(containerColor),
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val resolvedContainer by animateColorAsState(
        targetValue = if (enabled && !loading) containerColor else colors.surfaceGrey,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else Motion.colorShort,
        label = "primaryButtonContainer",
    )
    val resolvedContent by animateColorAsState(
        targetValue = if (enabled && !loading) contentColor else colors.textTertiary,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else Motion.colorShort,
        label = "primaryButtonContent",
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(size.height)
            .animateContentSize(animationSpec = if (reduce) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.82f, stiffness = 420f))
            .drawBehind {
                if (pressed && enabled && !loading && !reduce) {
                    val bleed = 5.dp.toPx()
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(colors.primary.copy(alpha = 0.12f), BrandRedSoft.copy(alpha = 0.22f), colors.primary.copy(alpha = 0.12f)),
                        ),
                        topLeft = Offset(-bleed, -bleed),
                        size = androidx.compose.ui.geometry.Size(this.size.width + bleed * 2f, this.size.height + bleed * 2f),
                        cornerRadius = CornerRadius(this.size.height / 2f + bleed),
                    )
                }
            }
            .pressScale(interaction, pressedScale = 0.96f),
        enabled = enabled && !loading,
        shape = PinBomShape.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent,
            disabledContainerColor = resolvedContainer,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding),
        interactionSource = interaction,
    ) {
        Crossfade(
            targetState = loading,
            animationSpec = if (reduce) androidx.compose.animation.core.snap() else tween(Motion.short),
            label = "primaryLoading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.textSecondary,
                )
            } else {
                ButtonLabel(text = text, icon = leadingIcon, color = resolvedContent)
            }
        }
    }
}

/** Grey pill: “ویرایش پروفایل”, “دنبال می‌کنید”. */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    size: PillButtonSize = PillButtonSize.Regular,
    fillWidth: Boolean = false,
    selected: Boolean = false,
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val targetContainer = if (selected) colors.chipSelectedBg else colors.surfaceGrey
    val targetContent = if (selected) colors.chipSelectedText else colors.textPrimary
    val container by animateColorAsState(
        targetValue = if (enabled && !loading) targetContainer else colors.surfaceGrey,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else Motion.colorShort,
        label = "secondaryButtonContainer",
    )
    val content by animateColorAsState(
        targetValue = if (enabled && !loading) targetContent else colors.textTertiary,
        animationSpec = if (reduce) androidx.compose.animation.core.snap() else Motion.colorShort,
        label = "secondaryButtonContent",
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(size.height)
            .animateContentSize(animationSpec = if (reduce) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.82f, stiffness = 420f))
            .pressScale(interaction, pressedScale = 0.96f),
        enabled = enabled && !loading,
        shape = PinBomShape.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding),
        interactionSource = interaction,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Crossfade(
            targetState = loading,
            animationSpec = if (reduce) androidx.compose.animation.core.snap() else tween(Motion.short),
            label = "secondaryLoading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.textSecondary,
                )
            } else {
                ButtonLabel(text = text, icon = leadingIcon, color = content)
            }
        }
    }
}

/** Text-only pill: quiet actions, “بیشتر”, “انصراف”, “تلاش مجدد”. */
@Composable
fun TextPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    destructive: Boolean = false,
    size: PillButtonSize = PillButtonSize.Small,
) {
    val colors = PinBomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val content = when {
        !enabled -> colors.textTertiary
        destructive -> MaterialTheme.colorScheme.error
        else -> colors.textPrimary
    }

    Row(
        modifier = modifier
            .height(size.height)
            .clip(PinBomShape.pill)
            .pinbomClick(
                onClick = onClick,
                source = interaction,
                indication = ripple(color = content),
                enabled = enabled,
                pressedScale = 0.96f,
                role = Role.Button,
            )
            .padding(horizontal = size.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ButtonLabel(text = text, icon = leadingIcon, color = content)
    }
}

/**
 * Translucent / grey circular icon button. Used for the floating back and more
 * buttons over images, the search field, notification rows and the profile bar.
 */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Dimens.iconButton,
    containerColor: Color? = null,
    contentColor: Color = PinBomTheme.colors.textPrimary,
    shape: Shape = CircleShape,
    iconSize: Dp = 18.dp,
    badge: Boolean = false,
    badgeColor: Color = PinBomTheme.colors.primary,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = PinBomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val resolvedContainer = containerColor ?: colors.surfaceGrey

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(resolvedContainer)
            .pinbomClick(
                onClick = onClick,
                source = interaction,
                indication = ripple(color = contentColor),
                enabled = enabled,
                pressedScale = 0.92f,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) contentColor else colors.textTertiary,
            modifier = Modifier.size(iconSize),
        )
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = size * 0.16f, end = size * 0.16f)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
        }
    }
}

/**
 * The label colour that belongs with a given container.
 *
 * The pill has always been white-on-brand-red, and that pair is unchanged. What
 * this fixes is the caller who passes a *light* container (`surface`, or the
 * light theme's `surfaceGrey`): the label used to stay white and disappear.
 * Rules of thumb, both measured: white on `primary` clears 4.7:1 in the light
 * theme and 3.5:1 in the dark one (button-sized semibold text), while
 * `inverseSurface` on a light container clears 13:1 or better.
 */
@Composable
private fun labelOn(container: Color): Color =
    if (container.luminance() > 0.45f) PinBomTheme.colors.inverseSurface else AbsoluteWhite

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Dimens.sm))
        }
        Text(
            text = text,
            style = PinBomText.button,
            color = color,
            maxLines = 1,
        )
    }
}

/** Small square-ish button used inside sheets and dialogs. */
@Composable
fun PinBomTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    shape: Shape = PinBomShape.pill,
    contentColor: Color = PinBomTheme.colors.textPrimary,
) {
    val colors = PinBomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(Dimens.buttonSmall)
            .clip(shape)
            .background(colors.surfaceGrey)
            .pinbomClick(
                onClick = onClick,
                source = interaction,
                indication = ripple(color = contentColor),
                pressedScale = 0.96f,
                role = Role.Button,
            )
            .padding(horizontal = Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonLabel(text = text, icon = leadingIcon, color = contentColor)
    }
}

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Composable
private fun ButtonGallery(dark: Boolean) {
    PinBomTheme(darkTheme = dark) {
        Column(
            Modifier
                .background(PinBomTheme.colors.background)
                .padding(Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                PrimaryPillButton(text = "ذخیره", onClick = {})
                PrimaryPillButton(text = "انتشار", onClick = {}, loading = true)
                PrimaryPillButton(text = "غیرفعال", onClick = {}, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                SecondaryPillButton(text = "دنبال می‌کنید", onClick = {}, selected = true)
                SecondaryPillButton(text = "کوتاه", onClick = {}, size = PillButtonSize.Small)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                TextPillButton(text = "انصراف", onClick = {})
                TextPillButton(text = "تلاش مجدد", onClick = {})
                TextPillButton(text = "حذف", onClick = {}, destructive = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                IconCircleButton(
                    icon = PinBomIcons.BookmarkBorder,
                    contentDescription = "ذخیره",
                    onClick = {},
                )
                IconCircleButton(
                    icon = PinBomIcons.Settings,
                    contentDescription = "تنظیمات",
                    onClick = {},
                    enabled = false,
                )
                PinBomTonalButton(text = "خاص", onClick = {})
            }
            PrimaryPillButton(text = "تمام‌عرض", onClick = {}, fillWidth = true)
        }
    }
}

@Preview(name = "Buttons · light", backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun PreviewButtonsLight() = ButtonGallery(dark = false)

@Preview(name = "Buttons · dark", backgroundColor = 0xFF121212, widthDp = 400)
@Composable
private fun PreviewButtonsDark() = ButtonGallery(dark = true)
