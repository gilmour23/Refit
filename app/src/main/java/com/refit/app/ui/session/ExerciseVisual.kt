package com.refit.app.ui.session

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext

private enum class WristMotionPhase {
    LIFT,
    HOLD,
    LOWER,
    REST
}

@Composable
fun AnimatedWristExerciseCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "wrist_rehab_motion")

    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wrist_cycle"
    )

    val phase = when {
        cycle < 0.20f -> WristMotionPhase.LIFT
        cycle < 0.60f -> WristMotionPhase.HOLD
        cycle < 0.90f -> WristMotionPhase.LOWER
        else -> WristMotionPhase.REST
    }

    val wristAngle = when (phase) {
        WristMotionPhase.LIFT -> {
            val local = (cycle / 0.20f).coerceIn(0f, 1f)
            -35f * FastOutSlowInEasing.transform(local)
        }

        WristMotionPhase.HOLD -> -35f

        WristMotionPhase.LOWER -> {
            val local = ((cycle - 0.60f) / 0.30f).coerceIn(0f, 1f)
            -35f * (1f - FastOutSlowInEasing.transform(local))
        }

        WristMotionPhase.REST -> 0f
    }

    val height = if (compact) 220.dp else 260.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = ReFitPrimarySoft,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val w = size.width
            val h = size.height

            val limb = Color(0xFFB8CAE8)
            val joint = ReFitPrimary.copy(alpha = 0.88f)
            val pivot = Offset(w * 0.60f, h * 0.63f)

            drawRoundRect(
                color = limb,
                topLeft = Offset(w * 0.12f, h * 0.57f),
                size = Size(w * 0.48f, h * 0.13f),
                cornerRadius = CornerRadius(h * 0.065f)
            )

            drawCircle(
                color = joint,
                radius = h * 0.032f,
                center = pivot
            )

            rotate(
                degrees = wristAngle,
                pivot = pivot
            ) {
                drawRoundRect(
                    color = limb,
                    topLeft = Offset(w * 0.58f, h * 0.57f),
                    size = Size(w * 0.25f, h * 0.13f),
                    cornerRadius = CornerRadius(h * 0.065f)
                )
            }

            val arrow = Path().apply {
                moveTo(w * 0.70f, h * 0.45f)
                quadraticBezierTo(
                    w * 0.78f, h * 0.33f,
                    w * 0.71f, h * 0.22f
                )
            }

            drawPath(
                path = arrow,
                color = ReFitPrimary.copy(alpha = 0.75f),
                style = Stroke(width = 7f)
            )

            drawLine(
                color = ReFitPrimary.copy(alpha = 0.75f),
                start = Offset(w * 0.71f, h * 0.22f),
                end = Offset(w * 0.67f, h * 0.28f),
                strokeWidth = 7f
            )

            drawLine(
                color = ReFitPrimary.copy(alpha = 0.75f),
                start = Offset(w * 0.71f, h * 0.22f),
                end = Offset(w * 0.76f, h * 0.27f),
                strokeWidth = 7f
            )
        }

        Text(
            text = "손목 폄 운동",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 16.dp)
        )

        Text(
            text = phaseText(phase),
            style = MaterialTheme.typography.bodyLarge,
            color = ReFitSubtext,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 16.dp)
        )
    }
}

private fun phaseText(phase: WristMotionPhase): String {
    return when (phase) {
        WristMotionPhase.LIFT -> "손목을 위로 천천히 올리세요"
        WristMotionPhase.HOLD -> "2초간 유지하세요"
        WristMotionPhase.LOWER -> "천천히 내려오세요"
        WristMotionPhase.REST -> "잠시 쉬고 다시 시작하세요"
    }
}
