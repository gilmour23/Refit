package com.refit.app.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.*

/** Direction cue only. It never renders measured movement or an electrode placement. */
@Composable
fun WristIllustration(side: String?, attempt: Boolean = false, modifier: Modifier = Modifier.height(132.dp)) {
    Canvas(modifier.fillMaxWidth().semantics {
        contentDescription = if (attempt) "팔을 받친 채 손등 쪽으로 손목을 펴려는 방향" else "팔을 받침 위에 편하게 놓은 준비 자세"
    }) {
        val w = size.width; val h = size.height
        withTransform({ if (side == "right") scale(-1f, 1f, Offset(w / 2, h / 2)) }) {
            drawRoundRect(Color(0xFFD8E3E0), Offset(w*.06f,h*.68f), androidx.compose.ui.geometry.Size(w*.51f,h*.15f), CornerRadius(10f))
            val arm = Path().apply {
                moveTo(w*.06f,h*.36f); lineTo(w*.53f,h*.36f)
                cubicTo(w*.60f,h*.35f,w*.63f,h*.30f,w*.69f,h*.30f)
                lineTo(w*.89f,h*.33f)
                cubicTo(w*.95f,h*.34f,w*.96f,h*.41f,w*.90f,h*.43f)
                lineTo(w*.73f,h*.44f)
                cubicTo(w*.74f,h*.46f,w*.77f,h*.49f,w*.79f,h*.51f)
                cubicTo(w*.82f,h*.55f,w*.79f,h*.60f,w*.75f,h*.58f)
                lineTo(w*.64f,h*.51f)
                cubicTo(w*.60f,h*.55f,w*.58f,h*.60f,w*.53f,h*.60f)
                lineTo(w*.06f,h*.60f); close()
            }
            drawPath(arm, Color(0xFFF1DCCB)); drawPath(arm, Ink.copy(alpha=.6f), style=Stroke(2.5f))
            drawLine(Ink.copy(alpha=.3f), Offset(w*.55f,h*.38f),Offset(w*.55f,h*.57f),2f)
            drawLine(Muted.copy(alpha=.3f),Offset(w*.03f,h*.85f),Offset(w*.96f,h*.85f),2f)
            if (attempt) {
                val direction = Path().apply {
                    moveTo(w*.70f,h*.23f)
                    quadraticTo(w*.77f,h*.18f,w*.79f,h*.06f)
                }
                drawPath(direction, Accent, style=Stroke(4f))
                drawLine(Accent,Offset(w*.79f,h*.06f),Offset(w*.75f,h*.10f),4f)
                drawLine(Accent,Offset(w*.79f,h*.06f),Offset(w*.82f,h*.13f),4f)
            }
        }
    }
}

