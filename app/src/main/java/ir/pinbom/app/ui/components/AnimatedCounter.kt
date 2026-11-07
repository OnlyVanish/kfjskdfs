package ir.pinbom.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import ir.pinbom.app.ui.theme.Localizer
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.reduceMotion

/** Reusable rolling numeric label: old value travels up, new value enters from below. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = PinBomText.caption,
    color: Color = PinBomTheme.colors.textPrimary,
    maxLines: Int = 1,
    formatter: @Composable (Int) -> String = { Localizer.number(it) },
) {
    val reduce = reduceMotion() || !LocalSpecialEffectsEnabled.current
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(animationSpec = tween(Motion.short)) { height -> height } + fadeIn(tween(Motion.short))) togetherWith
                    (slideOutVertically(animationSpec = tween(Motion.short)) { height -> -height } + fadeOut(tween(Motion.instant)))
            }
        },
        label = "pinBomAnimatedCounter",
    ) { number ->
        Text(
            text = formatter(number),
            style = style,
            color = color,
            maxLines = maxLines,
        )
    }
}
