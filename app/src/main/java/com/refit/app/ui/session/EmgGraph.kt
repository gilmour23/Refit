package com.refit.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitBorder
import com.refit.app.ui.theme.ReFitPrimary

@Composable
fun EmgGraph(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        repeat(4) { index ->
            val y = size.height * (index + 1) / 5f
            drawLine(
                color = ReFitBorder,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        if (samples.size < 2) return@Canvas

        val minValue = samples.minOrNull() ?: 0f
        val maxValue = samples.maxOrNull() ?: 1f
        val range = (maxValue - minValue).coerceAtLeast(0.01f)

        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
        val path = Path()

        samples.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
            val y = size.height - normalized * size.height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = ReFitPrimary,
            style = Stroke(width = 2.5f)
        )
    }
}
