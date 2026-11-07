package ir.pinbom.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/* PinBom radius tokens: soft, predictable and constrained independently of tile size. */
val PinBomShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Light cubic superellipse approximation with a bounded radius (no size-proportional over-rounding). */
class PinBomSquircleShape(
    private val minRadius: Dp = 12.dp,
    private val maxRadius: Dp = 24.dp,
    private val fraction: Float = 0.20f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline = with(density) {
        val lower = minRadius.toPx().coerceAtLeast(0f)
        val upper = maxRadius.toPx().coerceAtLeast(lower)
        val halfSmallestSide = minOf(size.width, size.height) / 2f
        val radius = (size.minDimension * fraction.coerceIn(0f, 0.5f))
            .coerceIn(lower, upper)
            .coerceAtMost(halfSmallestSide)
        val k = 0.43f
        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            cubicTo(size.width - radius * k, 0f, size.width, radius * k, size.width, radius)
            lineTo(size.width, size.height - radius)
            cubicTo(size.width, size.height - radius * k, size.width - radius * k, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            cubicTo(radius * k, size.height, 0f, size.height - radius * k, 0f, size.height - radius)
            lineTo(0f, radius)
            cubicTo(0f, radius * k, radius * k, 0f, radius, 0f)
            close()
        }
        Outline.Generic(path)
    }
}

object PinBomShape {
    val pin = PinBomSquircleShape(12.dp, 24.dp, 0.19f)
    val boardCover = PinBomSquircleShape(14.dp, 32.dp, 0.22f)
    val searchTile = PinBomSquircleShape(12.dp, 28.dp, 0.24f)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val dialog = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(percent = 50)
    val field = RoundedCornerShape(16.dp)
    val avatar = CircleShape
    val thumb = PinBomSquircleShape(6.dp, 12.dp, 0.18f)
    val hero = PinBomSquircleShape(16.dp, 36.dp, 0.23f)
}
