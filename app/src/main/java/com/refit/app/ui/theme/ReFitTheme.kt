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

val ReFitBackground = Color(0xFFF7F8FA)
val ReFitSurface = Color(0xFFFFFFFF)
val ReFitText = Color(0xFF151A22)
val ReFitSubtext = Color(0xFF667085)
val ReFitBorder = Color(0xFFE1E5EA)
val ReFitPrimary = Color(0xFF1769E0)
val ReFitPrimarySoft = Color(0xFFEEF4FF)
val ReFitSuccess = Color(0xFF1F8A5B)
val ReFitSuccessSoft = Color(0xFFEDF8F2)
val ReFitDanger = Color(0xFFC43D3D)

val ReFitCardShape = RoundedCornerShape(12.dp)
val ReFitButtonShape = RoundedCornerShape(10.dp)

private val ReFitColors = lightColorScheme(
    primary = ReFitPrimary,
    onPrimary = Color.White,
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
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
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
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 17.sp
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
