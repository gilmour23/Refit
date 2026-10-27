package com.refit.app.ui.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.refit.app.R

/** Static instruction asset from the approved design; never measured movement. */
@Composable
fun WristIllustration(side: String?, attempt: Boolean = false, modifier: Modifier = Modifier.height(184.dp)) {
    Image(painterResource(if (attempt) R.drawable.wrist_attempt else R.drawable.wrist_rest),
        contentDescription = if (attempt) "동작 예시: 손목을 위로 펴려는 방향" else "동작 예시: 팔을 받침 위에 편하게 놓은 자세",
        modifier = modifier.fillMaxWidth().scale(if (side == "right") -1f else 1f, 1f),
        contentScale = ContentScale.FillBounds)
}
