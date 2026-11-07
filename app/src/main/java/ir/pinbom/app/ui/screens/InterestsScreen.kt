package ir.pinbom.app.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateContentSize
import ir.pinbom.app.ui.components.icons.PinBomIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import ir.pinbom.app.R
import ir.pinbom.app.ui.InterestsViewModel
import ir.pinbom.app.ui.components.IconCircleButton
import ir.pinbom.app.ui.components.PinBomChip
import ir.pinbom.app.ui.components.CardEntranceSeen
import ir.pinbom.app.ui.components.cardEntrance
import ir.pinbom.app.ui.components.fx.MeshBackground
import ir.pinbom.app.ui.components.fx.PinBomParticles
import ir.pinbom.app.ui.components.fx.ParticleBurstKind
import ir.pinbom.app.ui.components.PrimaryPillButton
import ir.pinbom.app.ui.components.SecondaryPillButton
import ir.pinbom.app.ui.theme.AppLanguage
import ir.pinbom.app.ui.theme.LocalAppLanguage
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens

/** Selection screen used once after registration and again from Settings in edit mode. */
@Composable
fun InterestsScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit,
    editMode: Boolean = false,
    nav: NavHostController? = null,
    vm: InterestsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val language = LocalAppLanguage.current
    val reduce = reduceMotion()
    val effects = LocalSpecialEffectsEnabled.current
    val canContinue = state.selected.size in 3..10 && !state.loading && !state.saving
    val continueScale = remember { Animatable(1f) }
    var previousCanContinue by remember { mutableStateOf(canContinue) }
    LaunchedEffect(editMode) { vm.load(editMode) }
    LaunchedEffect(canContinue, reduce, effects) {
        if (canContinue && !previousCanContinue && effects && !reduce) {
            continueScale.snapTo(0.92f)
            continueScale.animateTo(1f, Motion.popSpring)
        } else if (!effects || reduce || !canContinue) {
            continueScale.snapTo(1f)
        }
        previousCanContinue = canContinue
    }

    Box(Modifier.fillMaxSize()) {
      MeshBackground(Modifier.matchParentSize())
      Column(Modifier.fillMaxSize().padding(horizontal = Dimens.screen)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimens.topBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editMode) {
                IconCircleButton(
                    icon = PinBomIcons.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = { nav?.popBackStack() ?: onSkip() },
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            Text(
                text = stringResource(if (editMode) R.string.interests_edit_title else R.string.interests_onboarding_title),
                style = PinBomText.titleLarge,
                color = PinBomTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.interests_description),
            style = PinBomText.body,
            color = PinBomTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimens.sm),
        )
        Text(
            text = stringResource(R.string.interests_selected_count, state.selected.size),
            style = PinBomText.body,
            color = PinBomTheme.colors.textPrimary,
            modifier = (if (!reduce && effects) Modifier.animateContentSize(tween(Motion.medium)) else Modifier).padding(bottom = Dimens.md),
        )
        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinBomTheme.colors.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 124.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, interest ->
                    val selected = interest.id in state.selected
                    var sparkleTrigger by remember(interest.id) { mutableIntStateOf(0) }
                    val firstEntrance = remember(interest.id) {
                        index < 8 && CardEntranceSeen.markFirst(interest.id xor Long.MIN_VALUE)
                    }
                    val selectionScale = animateFloatAsState(
                        targetValue = if (selected) 1.035f else 1f,
                        animationSpec = if (reduce || !effects) snap() else Motion.popSpring,
                        label = "interestSelectionScale",
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .cardEntrance(
                                enabled = firstEntrance,
                                delayMillis = index.coerceAtMost(7) * 38,
                            )
                            .graphicsLayer {
                                scaleX = selectionScale.value
                                scaleY = selectionScale.value
                            },
                    ) {
                        PinBomChip(
                            text = if (language == AppLanguage.Persian) interest.name_fa else interest.name_en,
                            selected = selected,
                            enabled = selected || state.selected.size < 10,
                            trailingIcon = { InterestDrawCheckmark(selected = selected) },
                            onClick = {
                                if (!selected) sparkleTrigger += 1
                                vm.toggle(interest.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (sparkleTrigger > 0) {
                            PinBomParticles(
                                trigger = sparkleTrigger,
                                modifier = Modifier.matchParentSize(),
                                kind = ParticleBurstKind.Sparkle,
                                maxParticles = 8,
                            )
                        }
                    }
                }
            }
        }
        state.error?.let { message ->
            Text(
                text = message,
                style = PinBomText.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = Dimens.sm),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.md, bottom = Dimens.navBarSpace),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryPillButton(
                text = stringResource(if (editMode) R.string.action_cancel else R.string.interests_skip),
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                enabled = !state.saving,
            )
            PrimaryPillButton(
                text = stringResource(R.string.action_continue),
                onClick = { vm.save(onDone) },
                modifier = Modifier.weight(1f).graphicsLayer {
                    scaleX = continueScale.value
                    scaleY = continueScale.value
                },
                enabled = canContinue,
                loading = state.saving,
            )
        }
    }
    }
}

@Composable
private fun InterestDrawCheckmark(selected: Boolean) {
    val reduce = reduceMotion()
    val effects = LocalSpecialEffectsEnabled.current
    val checkColor = PinBomTheme.colors.chipSelectedText
    val progress = remember { Animatable(0f) }
    LaunchedEffect(selected, reduce, effects) {
        if (reduce || !effects) {
            progress.snapTo(if (selected) 1f else 0f)
        } else if (selected) {
            progress.snapTo(0f)
            progress.animateTo(1f, Motion.popSpring)
        } else {
            progress.animateTo(0f, tween(Motion.instant))
        }
    }
    Canvas(
        Modifier.size(14.dp).graphicsLayer { alpha = if (selected) 1f else 0f },
    ) {
        val p = progress.value.coerceIn(0f, 1f)
        val start = Offset(size.width * 0.12f, size.height * 0.52f)
        val middle = Offset(size.width * 0.40f, size.height * 0.78f)
        val end = Offset(size.width * 0.90f, size.height * 0.22f)
        if (p > 0f) {
            if (p < 0.52f) {
                val t = p / 0.52f
                drawLine(checkColor, start, Offset(start.x + (middle.x - start.x) * t, start.y + (middle.y - start.y) * t), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawLine(checkColor, start, middle, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val t = ((p - 0.52f) / 0.48f).coerceIn(0f, 1f)
                drawLine(checkColor, middle, Offset(middle.x + (end.x - middle.x) * t, middle.y + (end.y - middle.y) * t), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

