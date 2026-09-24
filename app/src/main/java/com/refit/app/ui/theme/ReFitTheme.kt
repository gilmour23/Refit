package com.refit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ReFitBackground = Color(0xFFF4F6F3)
val ReFitSurface = Color(0xFFFFFFFF)
val ReFitText = Color(0xFF17211D)
val ReFitSubtext = Color(0xFF6B746F)
val ReFitBorder = Color(0xFFE1E6E2)

val ReFitPrimary = Color(0xFF2E675A)
val ReFitPrimaryDark = Color(0xFF204C43)
val ReFitPrimarySoft = Color(0xFFE7F0EC)

val ReFitSuccess = Color(0xFF2E675A)
val ReFitSuccessSoft = Color(0xFFE7F0EC)
val ReFitDanger = Color(0xFFB34B49)

val ReFitCardShape = RoundedCornerShape(18.dp)
val ReFitButtonShape = RoundedCornerShape(14.dp)

private val ReFitColors = lightColorScheme(
    primary = ReFitPrimary,
    onPrimary = Color.White,
    primaryContainer = ReFitPrimarySoft,
    onPrimaryContainer = ReFitPrimaryDark,
    background = ReFitBackground,
    onBackground = ReFitText,
    surface = ReFitSurface,
    onSurface = ReFitText,
    outline = ReFitBorder,
    error = ReFitDanger
)

private val ReFitTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun ReFitTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ReFitColors,
        typography = ReFitTypography,
        content = content
    )
}
