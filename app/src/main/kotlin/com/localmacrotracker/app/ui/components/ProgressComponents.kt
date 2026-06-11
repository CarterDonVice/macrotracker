package com.localmacrotracker.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmacrotracker.app.ui.theme.DarkSurface
import com.localmacrotracker.app.ui.theme.DarkSurfaceVariant
import com.localmacrotracker.app.ui.theme.ErrorRed
import com.localmacrotracker.app.ui.theme.TextPrimary
import com.localmacrotracker.app.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Card with press-scale feedback (0.97 on press, spring release).
 * Use for any tappable card so the whole app shares the same touch response.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = DarkSurface,
    shadowElevation: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = shape,
        color = color,
        shadowElevation = shadowElevation,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) { content() }
}

/**
 * Animated calorie ring. With a goal: shows kcal remaining (or over, in red).
 * Without a goal: shows kcal consumed today.
 */
@Composable
fun CalorieRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    isEstimated: Boolean = false,
    ringSize: Dp = 120.dp,
    strokeWidth: Dp = 11.dp,
    progressColor: Color
) {
    val hasGoal = goal > 0
    val over = hasGoal && consumed > goal
    val target = if (hasGoal) (consumed.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "calorieRing"
    )
    val sweepColor = if (over) ErrorRed else progressColor

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = DarkSurfaceVariant,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (animated > 0f) {
                drawArc(
                    color = sweepColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val prefix = if (isEstimated) "~" else ""
            if (hasGoal) {
                Text(
                    text = "$prefix${abs(goal - consumed)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (over) ErrorRed else TextPrimary
                )
                Text(
                    text = if (over) "kcal over" else "kcal left",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) ErrorRed else TextSecondary
                )
            } else {
                Text(
                    text = "$prefix$consumed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "kcal today",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Labeled macro progress bar with animated fill.
 * Without a goal it just shows the current value, no fill.
 */
@Composable
fun MacroGoalBar(
    label: String,
    current: Int,
    goal: Int,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "g"
) {
    val hasGoal = goal > 0
    val over = hasGoal && current > goal
    val target = if (hasGoal) (current.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "macroBar_$label"
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = if (hasGoal) "$current / $goal$unit" else "$current$unit",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (over) ErrorRed else TextSecondary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
        ) {
            if (animated > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (over) ErrorRed else color)
                )
            }
        }
    }
}
