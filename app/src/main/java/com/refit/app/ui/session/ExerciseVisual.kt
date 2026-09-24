package com.refit.app.ui.session

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refit.app.ui.theme.ReFitPrimary
import com.refit.app.ui.theme.ReFitPrimarySoft
import com.refit.app.ui.theme.ReFitSubtext

@Composable
fun WristExtensionVisual(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val height = if (compact) 210.dp else 250.dp

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
            val outline = ReFitPrimary.copy(alpha = 0.85f)

            drawRoundRect(
                color = limb,
                topLeft = Offset(w * 0.13f, h * 0.57f),
                size = Size(w * 0.47f, h * 0.13f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.065f)
            )

            drawCircle(
                color = outline,
                radius = h * 0.035f,
                center = Offset(w * 0.60f, h * 0.635f)
            )

            rotate(
                degrees = -34f,
                pivot = Offset(w * 0.60f, h * 0.635f)
            ) {
                drawRoundRect(
                    color = limb,
                    topLeft = Offset(w * 0.58f, h * 0.57f),
                    size = Size(w * 0.25f, h * 0.13f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.065f)
                )
            }

            val arrow = Path().apply {
                moveTo(w * 0.69f, h * 0.45f)
                quadraticBezierTo(
                    w * 0.76f, h * 0.32f,
                    w * 0.70f, h * 0.22f
                )
            }
            drawPath(
                path = arrow,
                color = ReFitPrimary,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f)
            )
            drawLine(
                color = ReFitPrimary,
                start = Offset(w * 0.70f, h * 0.22f),
                end = Offset(w * 0.66f, h * 0.27f),
                strokeWidth = 7f
            )
            drawLine(
                color = ReFitPrimary,
                start = Offset(w * 0.70f, h * 0.22f),
                end = Offset(w * 0.75f, h * 0.26f),
                strokeWidth = 7f
            )
        }

        Text(
            text = "손목을 위로 천천히",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 16.dp)
        )

        Text(
            text = "2초 유지 후 천천히 내려주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = ReFitSubtext,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 16.dp)
        )
    }
}
