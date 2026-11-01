package com.refit.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EmgGraph(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (samples.size < 2) return@Canvas

        val finite = samples.filter { it.isFinite() }
        if (finite.size < 2) return@Canvas
        val low = minOf(finite.minOrNull() ?: 0f, 0f)
        val high = maxOf(finite.maxOrNull() ?: 1f, 1f)
        val range = (high - low).coerceAtLeast(1f)

        val stepX = size.width / (finite.size - 1).coerceAtLeast(1)
        val path = Path()

        finite.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - low) / range).coerceIn(0f, 1f)
            val y = size.height - normalized * size.height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = 3f)
        )
    }
}


