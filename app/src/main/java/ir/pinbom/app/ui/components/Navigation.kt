package ir.pinbom.app.ui.components

import android.view.HapticFeedbackConstants
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pinbom.app.R
import ir.pinbom.app.ui.components.icons.PinBomNavIcons
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.Dimens
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.NavigationBarColors
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.AbsoluteWhite
import ir.pinbom.app.ui.theme.pressScale
import ir.pinbom.app.ui.theme.reduceMotion
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · navigation bar + rail
 *
 *  One floating charcoal pill on phones (64dp tall, 32dp radius, 16dp side and
 *  12dp bottom margin above the gesture inset) and one floating charcoal column
 *  on tablets. Scrolling content passes *under* the pill — the shell reserves no
 *  room for it — so every scrolling tab screen keeps [Dimens.navBarSpace] free
 *  at its end.
 *
 *  Nothing here moves a route: the items, their order, their labels and the way
 *  a tap is reported are exactly what they were. What is new is the bar's own
 *  design language — five selectable indicator styles behind one [NavBarStyle] —
 *  and the way the active marker travels: as a glide read inside offset/draw
 *  lambdas, never as a recomposition per frame.
 *
 *  Icons are line glyphs (1.75dp stroke, round caps, joins, no fills) from
 *  [PinBomNavIcons]; inactive is a muted grey-lavender, active is the brand accent.
 * ──────────────────────────────────────────────────────────────────────────── */

/** The five indicator styles of the navigation bar. Persisted by [name]. */
enum class NavBarStyle {
    /** A soft radial accent glow behind the active icon. */
    Glow,

    /** The active icon sits on a lighter rounded tile that slides between tabs. */
    Tile,

    /** Legacy stored value; renders as a centered icon-only style. */
    Label,

    /** Legacy preference migrated to Tile to avoid the old protruding edge. */
    Notch,

    /** A short accent underline sliding under the active icon. */
    Underline;

    companion object {
        /** What a fresh install shows. */
        val Default: NavBarStyle = Tile

        /** Reads a stored style; anything unknown falls back to [Default]. */
        fun ofTag(tag: String?): NavBarStyle = entries.firstOrNull { it.name == tag } ?: Default
    }
}

/** The name Settings shows for a style. */
fun NavBarStyle.labelRes(): Int = when (this) {
    NavBarStyle.Glow -> R.string.nav_style_glow
    NavBarStyle.Tile -> R.string.nav_style_tile
    NavBarStyle.Label -> R.string.nav_style_label
    NavBarStyle.Notch -> R.string.nav_style_notch
    NavBarStyle.Underline -> R.string.nav_style_underline
}

/** One destination. [label] is used for accessibility, never drawn as text. */
data class PinBomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val showBadge: Boolean = false,
    val isProfile: Boolean = false,
    val avatarName: String? = null,
    val avatarUrl: String? = null,
    /** Optional unread count; zero keeps the original small dot badge. */
    val badgeCount: Int = 0,
)

/* ── Geometry the whole file agrees on ───────────────────────────────────── */

private val BarSideMargin = 16.dp
private val BarBottomMargin = 12.dp
private val BarElevation = 16.dp
private val IconSize = 24.dp
private val TileSize = 40.dp
private val TileCorner = 16.dp
private val GlowSize = 40.dp
private val GlowAlpha = 0.35f
private val UnderlineWidth = 24.dp
private val UnderlineHeight = 3.dp
private val UnderlineBottom = 11.dp
private val BadgeDotSize = 6.dp
private val BadgeRing = 2.dp

/**
 * The bar palette for the theme around it, crossfaded.
 *
 * Every field is animated with the app's own [Motion.colorShort] tween, so
 * switching light ↔ dark blends the pill, the icons and the marker instead of
 * cutting to a new colour in a single frame. A theme flip happens on the
 * Settings screen, i.e. while the bar is on screen — which is exactly when a
 * hard swap is visible.
 */
@Composable
private fun animatedBarColors(): NavigationBarColors {
    val target = PinBomTheme.navigationBar
    val spec = if (reduceMotion() || !LocalSpecialEffectsEnabled.current) snap() else Motion.colorShort
    val fill by animateColorAsState(target.fill, spec, label = "navFill")
    val idle by animateColorAsState(target.idle, spec, label = "navIdle")
    val indicator by animateColorAsState(target.indicator, spec, label = "navMarker")
    val shadow by animateColorAsState(target.shadow, spec, label = "navShadow")
    val hairline by animateColorAsState(target.hairline, spec, label = "navHairline")
    val accent by animateColorAsState(target.accent, spec, label = "navAccent")
    return NavigationBarColors(
        fill = fill,
        idle = idle,
        indicator = indicator,
        shadow = shadow,
        hairline = hairline,
        accent = accent,
    )
}

/** Width of the light pill's outline. */
private val BarHairline = 1.dp

/* ── The bar ─────────────────────────────────────────────────────────────── */

@Composable
fun PinBomBottomBar(
    items: List<PinBomNavItem>,
    currentRoute: String?,
    onSelect: (PinBomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    style: NavBarStyle = NavBarStyle.Default,
) {
    val bar = animatedBarColors()
    val reduce = reduceMotion()
    val effects = LocalSpecialEffectsEnabled.current
    val motionDisabled = reduce || !effects
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    val activeIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val count = items.size.coerceAtLeast(1)

    /* The one value every travelling marker shares. It is read inside offset and
     * draw lambdas only, so a gliding marker never recomposes the bar. */
    val glide = remember { Animatable(activeIndex.toFloat()) }
    LaunchedEffect(activeIndex, items.size) {
        if (motionDisabled) {
            glide.snapTo(activeIndex.toFloat())
        } else {
            glide.animateTo(activeIndex.toFloat(), Motion.layoutSpring)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = BarSideMargin,
                end = BarSideMargin,
                bottom = BarBottomMargin,
            ),
    ) {
        val slotPx = with(density) { (maxWidth / count).toPx() }
        val barHeightPx = with(density) { Dimens.bottomBar.toPx() }

        /* Which slot a marker sits in: RTL mirrors the order, so item 0 owns the
         * right-hand slot and a marker always travels with the reading order. */
        fun markerCenter(marker: Float): Float {
            val physical = if (rtl) (count - 1) - marker else marker
            return slotPx * (physical + 0.5f)
        }

        // Keep the navigation surface fully contained. The legacy concave notch
        // caused a visible protruding lip on some screen densities.
        val barShape: Shape = PinBomShape.pill

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBar)
                .shadow(
                    elevation = BarElevation,
                    shape = barShape,
                    clip = false,
                    ambientColor = bar.shadow,
                    spotColor = bar.shadow,
                ),
        ) {
            // A clipped glass-tint backplate stays slightly more translucent on API 31+.
            // Compose blur affects its own layer, not siblings behind it, so no fake backdrop blur is applied.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(barShape)
                    .background(bar.fill.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && effects && !reduce) 0.88f else 0.96f)),
            )
            Box(Modifier.matchParentSize().border(BarHairline, bar.hairline, barShape))
            /* The markers live in one LTR layer: their x is already a physical
             * pixel offset, so nothing here is mirrored a second time. */
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize()) {
                    when (style) {
                        NavBarStyle.Tile -> Box(
                            modifier = Modifier
                                .absoluteOffset {
                                    IntOffset(
                                        x = (markerCenter(glide.value) - with(density) { TileSize.toPx() } / 2f)
                                            .roundToInt(),
                                        y = 0,
                                    )
                                }
                                .size(TileSize)
                                .clip(RoundedCornerShape(TileCorner))
                                .background(bar.indicator),
                        )

                        NavBarStyle.Underline -> Box(
                            modifier = Modifier
                                .absoluteOffset {
                                    IntOffset(
                                        x = (markerCenter(glide.value) -
                                            with(density) { UnderlineWidth.toPx() } / 2f).roundToInt(),
                                        y = (barHeightPx -
                                            with(density) { UnderlineBottom.toPx() }).roundToInt(),
                                    )
                                }
                                .size(width = UnderlineWidth, height = UnderlineHeight)
                                .clip(PinBomShape.pill)
                                .background(bar.accent),
                        )

                        // The active icon tint is enough; the old red dot inside
                        // the notch read as an unread badge and is intentionally gone.
                        NavBarStyle.Notch -> Box(
                            modifier = Modifier
                                .absoluteOffset {
                                    IntOffset(
                                        x = (markerCenter(glide.value) - with(density) { TileSize.toPx() } / 2f).roundToInt(),
                                        y = ((barHeightPx - with(density) { TileSize.toPx() }) / 2f).roundToInt(),
                                    )
                                }
                                .size(TileSize)
                                .graphicsLayer {
                                    val segment = glide.value - floor(glide.value)
                                    val stretch = 1f + 0.22f * sin(segment * PI.toFloat())
                                    scaleX = stretch
                                    scaleY = 0.94f
                                }
                                .clip(PinBomShape.pill)
                                .background(bar.indicator.copy(alpha = 0.74f)),
                        )

                        /* Both are drawn per slot instead of travelling. */
                        NavBarStyle.Glow, NavBarStyle.Label -> Unit
                    }
                }
            }

            /* The five slots, in the layout direction's own order. */
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    BottomBarItem(
                        item = item,
                        selected = item.route == currentRoute,
                        style = style,
                        bar = bar,
                        onClick = { onSelect(item) },
                        baseUrl = baseUrl,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: PinBomNavItem,
    selected: Boolean,
    style: NavBarStyle,
    bar: NavigationBarColors,
    onClick: () -> Unit,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    val reduce = reduceMotion()
    val motionDisabled = reduce || !LocalSpecialEffectsEnabled.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var lastActiveTapAt by remember(item.route) { mutableLongStateOf(0L) }

    /* A selection pulse, not a state: 1.00 → 1.12 → 1.00, read in the layer. */
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(selected, motionDisabled) {
        if (selected && !motionDisabled) {
            /* One soft spring pop: squeeze in, settle with a small overshoot.
             * A single physics animation is smoother than two chained tweens
             * (no velocity break in the middle) and costs the same. */
            pulse.snapTo(0.78f)
            pulse.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
            )
        } else {
            pulse.snapTo(1f)
        }
    }


    Box(
        modifier = modifier
            .pressScale(source = interaction, pressedScale = 0.92f)
            .navClickable(
                interaction = interaction,
                selected = selected,
                contentDescription = item.label,
                onClick = {
                    if (!selected) {
                        lastActiveTapAt = 0L
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                        onClick()
                    } else {
                        // A single tap leaves the active wall untouched; a second tap
                        // within 320ms dispatches the existing smooth scroll-to-top event.
                        val now = SystemClock.uptimeMillis()
                        val previous = lastActiveTapAt
                        lastActiveTapAt = if (previous > 0L && now - previous in 1L..320L) {
                            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                            onClick()
                            0L
                        } else {
                            now
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        NavIcon(
            item = item,
            selected = selected,
            glow = style == NavBarStyle.Glow,
            accent = bar.accent,
            idle = bar.idle,
            ring = bar.fill,
            scale = { pulse.value },
            baseUrl = baseUrl,
            modifier = Modifier,
        )
    }
}

/**
 * The icon itself: the user's avatar for the profile slot, a line glyph anywhere
 * else, plus the optional glow and the optional unread badge. [scale] is a lambda
 * so the selection pulse is applied in the graphics layer, not in composition.
 */
@Composable
private fun NavIcon(
    item: PinBomNavItem,
    selected: Boolean,
    glow: Boolean,
    accent: Color,
    idle: Color,
    ring: Color,
    scale: () -> Float,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    val motionDisabled = reduceMotion() || !LocalSpecialEffectsEnabled.current

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale()
            scaleY = scale()
        },
        contentAlignment = Alignment.Center,
    ) {
        if (glow) {
            NavGlow(visible = selected, color = accent)
        }

        when {
            item.isProfile && !item.avatarName.isNullOrBlank() -> Avatar(
                name = item.avatarName,
                url = item.avatarUrl,
                baseUrl = baseUrl,
                size = IconSize,
                ring = if (selected) accent else null,
                ringWidth = BadgeRing,
            )

            /* Line glyph and solid glyph are stacked and cross-faded, so the icon
             * "fills in" on selection. The fade is read inside graphicsLayer
             * lambdas: no recomposition per frame, just a layer alpha change. */
            item.selectedIcon === item.icon -> Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) accent else idle,
                modifier = Modifier.size(IconSize),
            )

            else -> {
                val fill = animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = if (motionDisabled) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
                    label = "navIconFill",
                )
                Box(Modifier.size(IconSize)) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = idle,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - fill.value },
                    )
                    Icon(
                        imageVector = item.selectedIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fill.value },
                    )
                }
            }
        }

        val reduceBadgeMotion = reduceMotion() || !LocalSpecialEffectsEnabled.current
        var previousBadgeCount by remember(item.route) { androidx.compose.runtime.mutableIntStateOf(item.badgeCount) }
        var previousBadgeVisible by remember(item.route) { androidx.compose.runtime.mutableStateOf(item.showBadge) }
        val badgeWiggle = remember(item.route) { Animatable(0f) }
        LaunchedEffect(item.badgeCount, item.showBadge) {
            val isNew = item.badgeCount > previousBadgeCount || (item.showBadge && !previousBadgeVisible)
            if (isNew && !reduceBadgeMotion) {
                badgeWiggle.snapTo(0f)
                badgeWiggle.animateTo(1f, tween(420, easing = Motion.emphasized))
            } else {
                badgeWiggle.snapTo(0f)
            }
            previousBadgeCount = item.badgeCount
            previousBadgeVisible = item.showBadge
        }
        if (item.showBadge || item.badgeCount > 0) {
            if (item.badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            rotationZ = if (reduceBadgeMotion) 0f else sin(badgeWiggle.value * PI.toFloat() * 6f) * (1f - badgeWiggle.value) * 8f
                        }
                        .widthIn(min = 19.dp)
                        .clip(PinBomShape.pill)
                        .background(ring)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.clip(PinBomShape.pill).background(accent).padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.badgeCount > 99) {
                            Text(text = "99+", color = AbsoluteWhite, style = PinBomText.caption, maxLines = 1)
                        } else {
                            AnimatedCounter(
                                value = item.badgeCount,
                                color = AbsoluteWhite,
                                style = PinBomText.caption,
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            rotationZ = if (reduceBadgeMotion) 0f else sin(badgeWiggle.value * PI.toFloat() * 6f) * (1f - badgeWiggle.value) * 8f
                        }
                        .size(BadgeDotSize + BadgeRing * 2)
                        .clip(CircleShape)
                        .background(ring)
                        .padding(BadgeRing),
                ) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(accent))
                }
            }
        }
    }
}

/** A soft radial halo: fades in and out, never travels. */
@Composable
private fun NavGlow(visible: Boolean, color: Color) {
    val motionDisabled = reduceMotion() || !LocalSpecialEffectsEnabled.current
    val alpha by animateFloatAsState(
        targetValue = if (visible && !motionDisabled) GlowAlpha else 0f,
        animationSpec = if (motionDisabled) snap() else Motion.mediumSpec(),
        label = "navGlow",
    )
    val radius = with(LocalDensity.current) { (GlowSize / 2).toPx() }

    Box(
        modifier = Modifier
            .size(GlowSize)
            .graphicsLayer { this.alpha = alpha }
            .background(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color.copy(alpha = 0.9f),
                        0.45f to color.copy(alpha = 0.35f),
                        1f to color.copy(alpha = 0f),
                    ),
                    radius = radius,
                ),
            ),
    )
}

/* ── Desktop / large-screen rail ─────────────────────────────────────────── */

private val RailSlot = Dimens.row

/** Vertical distance between two rail slots. */
private val RailPitch = RailSlot + Dimens.xs

/**
 * The same pill, stood on its side: a floating charcoal column with the mark on
 * top and a tile (or a glow, or a short vertical rule) marking the active
 * destination. Labels would not fit a 64dp column, so those keep their content
 * description instead of a visible line.
 */
@Composable
fun PinBomRail(
    items: List<PinBomNavItem>,
    currentRoute: String?,
    onSelect: (PinBomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    onLogoClick: (() -> Unit)? = null,
    style: NavBarStyle = NavBarStyle.Tile,
) {
    val bar = animatedBarColors()
    val reduce = reduceMotion()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val activeIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val glide = remember { Animatable(activeIndex.toFloat()) }
    LaunchedEffect(activeIndex, items.size) {
        if (reduce) {
            glide.snapTo(activeIndex.toFloat())
        } else {
            glide.animateTo(activeIndex.toFloat(), Motion.layoutSpring)
        }
    }

    /* The styles that make sense standing up; the rest read as the tile. */
    val railStyle = when (style) {
        NavBarStyle.Glow -> NavBarStyle.Glow
        NavBarStyle.Underline -> NavBarStyle.Underline
        else -> NavBarStyle.Tile
    }

    val pitchPx = with(density) { RailPitch.toPx() }
    val slotPx = with(density) { RailSlot.toPx() }
    val tilePx = with(density) { TileSize.toPx() }
    val railWidthPx = with(density) { 64.dp.toPx() }

    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(vertical = Dimens.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PinBomMark(
            size = 36.dp,
            modifier = if (onLogoClick != null) {
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .navClickable(
                        interaction = remember { MutableInteractionSource() },
                        selected = false,
                        contentDescription = stringResource(R.string.cd_pinbom_logo),
                        onClick = onLogoClick,
                    )
            } else {
                Modifier
            },
        )

        Spacer(Modifier.height(Dimens.lg))

        Box(
            modifier = Modifier
                .width(64.dp)
                .clip(PinBomShape.pill)
                .shadow(
                    elevation = BarElevation,
                    shape = PinBomShape.pill,
                    clip = false,
                    ambientColor = bar.shadow,
                    spotColor = bar.shadow,
                )
                .background(bar.fill, PinBomShape.pill)
                .border(BarHairline, bar.hairline, PinBomShape.pill)
                .padding(vertical = Dimens.sm),
        ) {
            /* The travelling marker first, so it stays behind the icons. It is
             * centred in the column, which is why the mirrored case is simply
             * the same distance to the other side. */
            if (railStyle == NavBarStyle.Tile) {
                val sidePx = (railWidthPx - tilePx) / 2f
                Box(
                    modifier = Modifier
                        .absoluteOffset {
                            IntOffset(
                                x = (if (rtl) -sidePx else sidePx).roundToInt(),
                                y = (glide.value * pitchPx + (slotPx - tilePx) / 2f).roundToInt(),
                            )
                        }
                        .size(TileSize)
                        .clip(RoundedCornerShape(TileCorner))
                        .background(bar.indicator),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                items.forEach { item ->
                    RailItem(
                        item = item,
                        selected = item.route == currentRoute,
                        glow = railStyle == NavBarStyle.Glow,
                        showRule = railStyle == NavBarStyle.Underline,
                        bar = bar,
                        onClick = { onSelect(item) },
                        baseUrl = baseUrl,
                    )
                }
            }
        }
    }
}

/** One rail slot: icon (or avatar), the optional glow, the optional rule. */
@Composable
private fun RailItem(
    item: PinBomNavItem,
    selected: Boolean,
    glow: Boolean,
    showRule: Boolean,
    bar: NavigationBarColors,
    onClick: () -> Unit,
    baseUrl: String,
) {
    val reduce = reduceMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }

    val pulse = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduce) {
            /* One soft spring pop: squeeze in, settle with a small overshoot.
             * A single physics animation is smoother than two chained tweens
             * (no velocity break in the middle) and costs the same. */
            pulse.snapTo(0.78f)
            pulse.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
            )
        } else {
            pulse.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = RailSlot)
            .clip(PinBomShape.pill)
            .pressScale(source = interaction, pressedScale = 0.92f)
            .navClickable(
                interaction = interaction,
                selected = selected,
                contentDescription = item.label,
                onClick = {
                    if (!selected) {
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    }
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        NavIcon(
            item = item,
            selected = selected,
            glow = glow,
            accent = bar.accent,
            idle = bar.idle,
            ring = bar.fill,
            scale = { pulse.value },
            baseUrl = baseUrl,
        )

        if (showRule && selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.xs)
                    .size(width = UnderlineHeight, height = UnderlineWidth)
                    .clip(PinBomShape.pill)
                    .background(bar.accent),
            )
        }
    }
}

/* ── Interaction ─────────────────────────────────────────────────────────── */

/**
 * A tab tap: no ripple, the selection in semantics, and the destination's own
 * label as the click label — which is what a screen reader and a UI test both
 * read. Press feedback (0.92) is applied by the caller's `pressScale`.
 */
@Composable
private fun Modifier.navClickable(
    interaction: MutableInteractionSource,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
): Modifier = this
    .semantics {
        this.selected = selected
    }
    .clickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Tab,
        onClickLabel = contentDescription,
        onClick = onClick,
    )

/* ── Previews ─────────────────────────────────────────────────────────────── */

@Composable
private fun navPreviewItems(): List<PinBomNavItem> = listOf(
    PinBomNavItem(
        route = "home",
        label = "خانه",
        icon = PinBomNavIcons.Home,
    ),
    PinBomNavItem(
        route = "search",
        label = "جستجو",
        icon = PinBomNavIcons.Search,
    ),
    PinBomNavItem(
        route = "create",
        label = "ساخت",
        icon = PinBomNavIcons.Create,
    ),
    PinBomNavItem(
        route = "notifications",
        label = "اعلان‌ها",
        icon = PinBomNavIcons.Notifications,
    ),
    PinBomNavItem(
        route = "profile",
        label = "پروفایل",
        icon = PinBomNavIcons.Profile,
        isProfile = true,
        avatarName = "نگار محمدی",
    ),
)

@Composable
private fun PreviewBar(
    style: NavBarStyle,
    dark: Boolean = false,
    currentRoute: String = "home",
) {
    PinBomTheme(darkTheme = dark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(PinBomTheme.colors.background),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PinBomBottomBar(
                items = navPreviewItems(),
                currentRoute = currentRoute,
                onSelect = {},
                style = style,
            )
        }
    }
}

@Preview(name = "Bar · standard pill · light", backgroundColor = 0xFFFFFFFF, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewBottomBarLight() = PreviewBar(style = NavBarStyle.Notch)

@Preview(name = "Bar · legacy marker · dark", backgroundColor = 0xFF121212, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewBottomBarDark() =
    PreviewBar(style = NavBarStyle.Notch, dark = true, currentRoute = "notifications")

@Preview(name = "Bar · glow", backgroundColor = 0xFF121212, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewStyleGlow() = PreviewBar(style = NavBarStyle.Glow, dark = true, currentRoute = "search")

@Preview(name = "Bar · tile", backgroundColor = 0xFFFFFFFF, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewStyleTile() = PreviewBar(style = NavBarStyle.Tile, currentRoute = "create")

@Preview(name = "Bar · label", backgroundColor = 0xFFFFFFFF, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewStyleLabel() = PreviewBar(style = NavBarStyle.Label, currentRoute = "profile")

@Preview(name = "Bar · underline", backgroundColor = 0xFF121212, widthDp = 400, heightDp = 160)
@Composable
private fun PreviewStyleUnderline() =
    PreviewBar(style = NavBarStyle.Underline, dark = true, currentRoute = "home")

@Preview(name = "Rail · light", backgroundColor = 0xFFFFFFFF, widthDp = 150, heightDp = 480)
@Composable
private fun PreviewRailLight() {
    PinBomTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PinBomTheme.colors.background),
        ) {
            PinBomRail(items = navPreviewItems(), currentRoute = "profile", onSelect = {})
        }
    }
}

@Preview(name = "Rail · dark · glow", backgroundColor = 0xFF121212, widthDp = 150, heightDp = 480)
@Composable
private fun PreviewRailDark() {
    PinBomTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PinBomTheme.colors.background),
        ) {
            PinBomRail(
                items = navPreviewItems(),
                currentRoute = "search",
                onSelect = {},
                style = NavBarStyle.Glow,
            )
        }
    }
}
