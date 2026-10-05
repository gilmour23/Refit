package com.refit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Instruction diagram, never driven by EMG or presented as measured movement. */
@Composable
fun WristIllustration() {
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics {
        contentDescription = "동작 예시: 팔을 받친 상태에서 손목을 위로 펴려는 시도"
    }) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFFDAE5E2), Offset(w * .08f, h * .63f), Size(w * .55f, h * .18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f))
        drawLine(Color(0xFFABB7BE), Offset(w * .04f, h * .83f), Offset(w * .97f, h * .83f), 3f)
        val arm = Path().apply {
            moveTo(w * .08f, h * .35f); lineTo(w * .57f, h * .35f)
            cubicTo(w * .64f, h * .35f, w * .70f, h * .15f, w * .86f, h * .12f)
            cubicTo(w * .93f, h * .10f, w * .96f, h * .19f, w * .89f, h * .23f)
            lineTo(w * .69f, h * .49f)
            cubicTo(w * .64f, h * .56f, w * .60f, h * .58f, w * .56f, h * .58f)
            lineTo(w * .08f, h * .58f); close()
        }
        drawPath(arm, Color(0xFFF5E5DB)); drawPath(arm, Ink.copy(alpha = .65f), style = Stroke(3f))
        drawLine(Accent, Offset(w * .77f, h * .53f), Offset(w * .77f, h * .35f), 4f)
        drawLine(Accent, Offset(w * .77f, h * .35f), Offset(w * .74f, h * .41f), 4f)
        drawLine(Accent, Offset(w * .77f, h * .35f), Offset(w * .80f, h * .41f), 4f)
    }
}
