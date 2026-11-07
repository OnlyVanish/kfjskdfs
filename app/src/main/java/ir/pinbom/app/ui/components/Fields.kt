package ir.pinbom.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.Localizer
import kotlinx.coroutines.launch

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · text fields
 *  Two shapes, one look: a quiet grey fill with no outline and no floating
 *  label (the label lives *above* the field, Pinterest-style), plus a dashed
 *  underline variant for the create/settings forms.
 * ──────────────────────────────────────────────────────────────────────────── */

/** How the characters *inside* a field are laid out. */
enum class FieldDirection {
    /** Follows the UI layout direction — the default for form labels and values. */
    Layout,

    /** Aligns by the text's own language — user content (titles, bios, comments). */
    Content,

    /**
     * Latin technical input (email, username, password, server URL): the
     * characters stay LTR so nothing reorders, while the text hugs the same edge
     * as the placeholder (right edge in the Persian UI, left edge in English).
     */
    Latin,
}

@Composable
private fun TextStyle.byFieldDirection(direction: FieldDirection): TextStyle = when (direction) {
    FieldDirection.Layout -> this
    FieldDirection.Content -> byContentDirection()
    FieldDirection.Latin -> copy(
        textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
        textDirection = TextDirection.Ltr,
    )
}

@Composable
fun PinBomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    error: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = if (singleLine) PinBomShape.pill else PinBomShape.field,
    textStyle: TextStyle = PinBomText.body,
    fieldDirection: FieldDirection = FieldDirection.Layout,
) {
    val colors = PinBomTheme.colors
    val hasError = error != null
    val reduce = reduceMotion()
    val effectsEnabled = LocalSpecialEffectsEnabled.current
    var focused by remember { mutableStateOf(false) }
    val focusProgress = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduce || !effectsEnabled) snap() else Motion.shortSpec<Float>(),
        label = "fieldFocus",
    )
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusScope = rememberCoroutineScope()

    LaunchedEffect(hasError, reduce, effectsEnabled) {
        if (hasError && !reduce && effectsEnabled) {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 240
                    0f at 0
                    -3.5f at 40
                    3.5f at 80
                    -2.5f at 120
                    2f at 160
                    0f at 240
                },
            )
        } else {
            shake.snapTo(0f)
        }
    }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focus ->
                focused = focus.isFocused
                if (focus.isFocused) {
                    focusScope.launch { bringIntoView.bringIntoView() }
                }
            }
            .graphicsLayer { translationX = with(density) { shake.value.dp.toPx() } }
            .drawWithContent {
                drawContent()
                if (effectsEnabled && !reduce) {
                    val alpha = focusProgress.value * 0.18f
                    if (alpha > 0.001f) {
                        val corner = if (singleLine) size.height / 2f else with(density) { 16.dp.toPx() }
                        drawRoundRect(
                            color = colors.primary.copy(alpha = alpha * 0.42f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(corner),
                            style = Stroke(width = with(density) { 4.dp.toPx() }, cap = StrokeCap.Round),
                        )
                        drawRoundRect(
                            color = colors.primary.copy(alpha = alpha * 2.6f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(corner),
                            style = Stroke(width = with(density) { 1.35.dp.toPx() }, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            .then(
                if (hasError) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, shape)
                } else {
                    Modifier
                },
            ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.byFieldDirection(fieldDirection).copy(color = colors.textPrimary),
        label = if (singleLine) placeholder?.let {
            {
                Text(
                    text = it,
                    style = PinBomText.caption,
                    color = if (hasError) MaterialTheme.colorScheme.error else colors.textSecondary,
                    maxLines = 1,
                )
            }
        } else null,
        placeholder = if (!singleLine) placeholder?.let {
            {
                Text(
                    text = it,
                    style = PinBomText.body,
                    color = colors.textTertiary,
                    maxLines = 2,
                )
            }
        } else null,
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = trailingIcon,
        supportingText = (error ?: supportingText)?.let {
            {
                Text(
                    text = it,
                    style = PinBomText.caption,
                    color = if (hasError) MaterialTheme.colorScheme.error else colors.textTertiary,
                )
            }
        },
        isError = hasError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = shape,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceGrey,
            unfocusedContainerColor = colors.surfaceGrey,
            disabledContainerColor = colors.surfaceGrey,
            errorContainerColor = colors.surfaceGrey,
            focusedIndicatorColor = colors.background.copy(alpha = 0f),
            unfocusedIndicatorColor = colors.background.copy(alpha = 0f),
            disabledIndicatorColor = colors.background.copy(alpha = 0f),
            errorIndicatorColor = colors.background.copy(alpha = 0f),
            cursorColor = colors.primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            disabledTextColor = colors.textTertiary,
            focusedPlaceholderColor = colors.textTertiary,
            unfocusedPlaceholderColor = colors.textTertiary,
            disabledPlaceholderColor = colors.textTertiary,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textTertiary,
            focusedTrailingIconColor = colors.textSecondary,
            unfocusedTrailingIconColor = colors.textTertiary,
            focusedSupportingTextColor = colors.textTertiary,
            unfocusedSupportingTextColor = colors.textTertiary,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/**
 * Underlined form field: a small label (optionally with a character counter),
 * then a borderless input that only draws its underline when focused.
 */
@Composable
fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    counter: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    error: String? = null,
    supportingText: String? = null,
    textStyle: TextStyle = PinBomText.body,
) {
    val colors = PinBomTheme.colors
    val hasError = error != null
    val reduce = reduceMotion()
    val effectsEnabled = LocalSpecialEffectsEnabled.current
    var focused by remember { mutableStateOf(false) }
    val focusProgress = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduce || !effectsEnabled) snap() else Motion.shortSpec<Float>(),
        label = "underlineFieldFocus",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.error
            focused -> colors.primary
            else -> colors.textSecondary
        },
        animationSpec = if (reduce || !effectsEnabled) snap() else Motion.colorShort,
        label = "underlineFieldLabelColor",
    )
    val underlineColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.error
            focused -> colors.primary
            else -> colors.divider
        },
        animationSpec = if (reduce || !effectsEnabled) snap() else Motion.colorShort,
        label = "underlineFieldLineColor",
    )
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(error, reduce, effectsEnabled) {
        if (error != null && !reduce && effectsEnabled) {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 240
                    0f at 0
                    -3.5f at 40
                    3.5f at 80
                    -2.5f at 120
                    2f at 160
                    0f at 240
                },
            )
        } else {
            shake.snapTo(0f)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.xs, end = Dimens.xs, bottom = Dimens.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = PinBomText.pinTitle,
                color = labelColor,
            )
            if (counter != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${Localizer.number(value.length)}/${Localizer.number(counter)}",
                    style = PinBomText.caption,
                    color = if (value.length > counter) MaterialTheme.colorScheme.error else colors.textTertiary,
                )
            }
        }

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .graphicsLayer { translationX = with(density) { shake.value.dp.toPx() } }
                .drawWithContent {
                    drawContent()
                    if (effectsEnabled && !reduce && focusProgress.value > 0f) {
                        val y = size.height - with(density) { 1.dp.toPx() }
                        drawLine(
                            color = colors.primary.copy(alpha = focusProgress.value * 0.11f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = with(density) { 5.dp.toPx() },
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = colors.primary.copy(alpha = focusProgress.value * 0.70f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = with(density) { 1.5.dp.toPx() },
                            cap = StrokeCap.Round,
                        )
                    }
                },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(color = colors.textPrimary),
            placeholder = placeholder?.let {
                { Text(text = it, style = PinBomText.body, color = colors.textTertiary) }
            },
            leadingIcon = leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            supportingText = (error ?: supportingText)?.let {
                {
                    Text(
                        text = it,
                        style = PinBomText.caption,
                        color = if (hasError) MaterialTheme.colorScheme.error else colors.textTertiary,
                        textAlign = TextAlign.Start,
                    )
                }
            },
            isError = hasError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = PinBomShape.pill,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.background.copy(alpha = 0f),
                unfocusedContainerColor = colors.background.copy(alpha = 0f),
                disabledContainerColor = colors.background.copy(alpha = 0f),
                errorContainerColor = colors.background.copy(alpha = 0f),
                focusedIndicatorColor = underlineColor,
                unfocusedIndicatorColor = underlineColor,
                disabledIndicatorColor = colors.divider,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
                cursorColor = colors.primary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedPlaceholderColor = colors.textTertiary,
                unfocusedPlaceholderColor = colors.textTertiary,
                focusedLeadingIconColor = colors.textSecondary,
                unfocusedLeadingIconColor = colors.textTertiary,
                focusedSupportingTextColor = colors.textTertiary,
                unfocusedSupportingTextColor = colors.textTertiary,
                errorSupportingTextColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Composable
private fun FieldsGallery(dark: Boolean) {
    PinBomTheme(darkTheme = dark) {
        Column(
            Modifier
                .background(PinBomTheme.colors.background)
                .padding(Dimens.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            PinBomTextField(
                value = "",
                onValueChange = {},
                placeholder = "نام کاربری یا ایمیل",
                leadingIcon = PinBomIcons.Person,
                modifier = Modifier.fillMaxWidth(),
            )
            PinBomTextField(
                value = "نگار محمدی",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
            )
            PinBomTextField(
                value = "رمز اشتباه",
                onValueChange = {},
                error = "رمز عبور باید حداقل ۸ کاراکتر باشد",
                modifier = Modifier.fillMaxWidth(),
            )
            PinBomTextField(
                value = "توضیح چندخطی درباره این پین که چند خط را پر می‌کند.",
                onValueChange = {},
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                placeholder = "توضیح",
                modifier = Modifier.fillMaxWidth(),
            )
            UnderlineField(
                label = "عنوان",
                value = "ایده چیدمان",
                onValueChange = {},
                counter = 40,
            )
            UnderlineField(
                label = "توضیح",
                value = "",
                onValueChange = {},
                placeholder = "چند جمله درباره این پین بنویسید",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
            )
        }
    }
}

@Preview(name = "Fields · light", backgroundColor = 0xFFFFFFFF, widthDp = 400)
@Composable
private fun PreviewFieldsLight() = FieldsGallery(dark = false)

@Preview(name = "Fields · dark", backgroundColor = 0xFF121212, widthDp = 400)
@Composable
private fun PreviewFieldsDark() = FieldsGallery(dark = true)
