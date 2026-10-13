package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import fr.gildasledrogoff.fwknop.presentation.ui.theme.DimensTheme
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme


@Composable
fun ShieldLockIllustration(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    accent: Color = MaterialTheme.colorScheme.tertiary,
    boxSize: Dp = DimensTheme.dimens.heroIcon * 1.5f,
) {
    val transition = rememberInfiniteTransition(label = "shield")
    val glimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "glimmer",
    )
    Canvas(modifier.size(boxSize)) {
        val stroke = boxSize.toPx() * 0.025f
        drawShield(tint, stroke)
        
        val pulse = (1f - kotlin.math.abs(glimmer * 2f - 1f)).let { it * it }
        drawPadlock(tint.copy(alpha = 0.65f + 0.35f * pulse), stroke)
        drawSparkle(accent.copy(alpha = pulse), Offset(size.width * 0.66f, size.height * 0.36f), pulse)
    }
}

private const val GLIMMER_MS = 2600

private fun DrawScope.drawShield(color: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    val shield = Path().apply {
        moveTo(w * 0.5f, h * 0.06f)
        lineTo(w * 0.84f, h * 0.18f)
        lineTo(w * 0.84f, h * 0.46f)
        cubicTo(w * 0.84f, h * 0.70f, w * 0.68f, h * 0.86f, w * 0.5f, h * 0.94f)
        cubicTo(w * 0.32f, h * 0.86f, w * 0.16f, h * 0.70f, w * 0.16f, h * 0.46f)
        lineTo(w * 0.16f, h * 0.18f)
        close()
    }
    drawPath(shield, color.copy(alpha = 0.10f))
    drawPath(shield, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawPadlock(color: Color, stroke: Float) {
    val w = size.width
    val h = size.height
    val bodyTop = h * 0.48f
    val bodySize = Size(w * 0.32f, h * 0.24f)
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.39f, h * 0.34f),
        size = Size(w * 0.22f, h * 0.26f),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.34f, bodyTop),
        size = bodySize,
        cornerRadius = CornerRadius(stroke * 1.5f),
        style = Stroke(stroke),
    )
    drawCircle(color, radius = stroke * 1.2f, center = Offset(w * 0.5f, bodyTop + bodySize.height * 0.42f))
    drawLine(
        color,
        Offset(w * 0.5f, bodyTop + bodySize.height * 0.42f),
        Offset(w * 0.5f, bodyTop + bodySize.height * 0.72f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSparkle(color: Color, center: Offset, scale: Float) {
    val r = size.width * 0.07f * (0.4f + 0.6f * scale)
    val k = r * 0.22f
    val sparkle = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(sparkle, color)
}

@AdaptivePreviews
@Composable
private fun ShieldLockIllustrationPreview() {
    FwknopTheme { ShieldLockIllustration() }
}
