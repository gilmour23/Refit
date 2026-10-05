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
            drawRoundRect(Color(0xFFDDEAE5), Offset(w*.06f,h*.68f), androidx.compose.ui.geometry.Size(w*.51f,h*.2f), CornerRadius(12f))
            val arm = Path().apply {
                moveTo(w*.06f,h*.31f); lineTo(w*.55f,h*.31f)
                cubicTo(w*.66f,h*.26f,w*.77f,h*.27f,w*.88f,h*.32f)
                cubicTo(w*.95f,h*.35f,w*.95f,h*.45f,w*.88f,h*.48f)
                lineTo(w*.72f,h*.48f)
                cubicTo(w*.69f,h*.57f,w*.65f,h*.61f,w*.62f,h*.54f)
                lineTo(w*.55f,h*.62f); lineTo(w*.06f,h*.62f); close()
            }
            drawPath(arm, Color(0xFFF5E7DC)); drawPath(arm, Ink.copy(alpha=.55f), style=Stroke(2.5f))
            drawLine(Ink.copy(alpha=.4f), Offset(w*.56f,h*.32f),Offset(w*.56f,h*.6f),2f)
            drawLine(Muted.copy(alpha=.4f),Offset(w*.03f,h*.9f),Offset(w*.96f,h*.9f),2f)
            if (attempt) {
                val x=w*.79f
                drawLine(Accent,Offset(x,h*.27f),Offset(x,h*.04f),5f)
                drawLine(Accent,Offset(x,h*.04f),Offset(x-w*.035f,h*.12f),5f)
                drawLine(Accent,Offset(x,h*.04f),Offset(x+w*.035f,h*.12f),5f)
            }
        }
    }
}
