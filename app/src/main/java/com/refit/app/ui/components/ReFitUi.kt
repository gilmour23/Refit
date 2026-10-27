package com.refit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.refit.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val AppFont = FontFamily(
    Font(R.font.noto_sans_kr_400, FontWeight.Normal),
    Font(R.font.noto_sans_kr_600, FontWeight.SemiBold),
    Font(R.font.noto_sans_kr_700, FontWeight.Bold)
)

val Ink = Color(0xFF16324F)
val Muted = Color(0xFF586A79)
val Accent = Color(0xFF226D65)
val AccentSurface = Color(0xFFE7F0F6)
val Calm = Color(0xFF226D65)
val CalmSurface = Color(0xFFEAF3EF)
val PageBackground = Color(0xFFFFFFFF)

@Composable
fun ReFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Accent, secondary = Calm,
            background = PageBackground, surface = Color.White,
            onSurface = Ink, onBackground = Ink, outline = Color(0xFFDCE1E5)),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 18.sp, lineHeight = 26.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        ), content = content
    )
}

@Composable
fun Page(title: String, onBack: (() -> Unit)? = null, topAction: (@Composable () -> Unit)? = null,
         compact: Boolean = false, scrollable: Boolean = true,
         footer: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = PageBackground, contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ 뒤로", fontSize = 17.sp) }
                else Text("Re:Fit", color = Accent, fontWeight = FontWeight.Bold, fontSize = 23.sp, modifier = Modifier.padding(12.dp))
                if (compact) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!compact) Spacer(Modifier.weight(1f))
                topAction?.invoke()
            }
        }, bottomBar = {
            Column(Modifier.fillMaxWidth().background(PageBackground).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = footer)
        }) { inset ->
        val body = Modifier.fillMaxSize().padding(inset).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 20.dp)
        Column(body, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
            if (!compact) Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(14.dp)) { Text(text, fontSize = 20.sp) }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(14.dp)) { Text(text, fontSize = 20.sp) }
}

@Composable
fun Hint(text: String) { Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) }

@Composable
fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun DemoNotice() {
    Surface(color = AccentSurface, shape = RoundedCornerShape(6.dp)) {
        Text("데모 · 모의 신호", color = Accent, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: String?, onChoose: (String) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            Column(Modifier.weight(1f)) {
                if (selected == option) Button({ onChoose(option) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) { Text("✓ $option", style = MaterialTheme.typography.bodyMedium) }
                else OutlinedButton({ onChoose(option) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) { Text(option, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun BottomTabs(selected: String, onHome: () -> Unit, onHistory: () -> Unit) {
    NavigationBar(containerColor = PageBackground, windowInsets = WindowInsets(0, 0, 0, 0), tonalElevation = 0.dp) {
        NavigationBarItem(selected == "home", onHome,
            icon = { NavigationGlyph(false, if (selected == "home") Accent else Muted) }, label = { Text("홈", style = MaterialTheme.typography.bodySmall) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = AccentSurface))
        NavigationBarItem(selected == "history", onHistory,
            icon = { NavigationGlyph(true, if (selected == "history") Accent else Muted) }, label = { Text("운동 기록", style = MaterialTheme.typography.bodySmall) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = AccentSurface))
    }
}

@Composable
private fun NavigationGlyph(history: Boolean, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        if (history) {
            drawRoundRect(color, Offset(5*s, 3*s), Size(14*s, 18*s), androidx.compose.ui.geometry.CornerRadius(2*s), style = Stroke(1.8f*s))
            for (y in listOf(8, 12, 16)) drawLine(color, Offset(8*s,y*s), Offset(16*s,y*s), 1.8f*s)
        } else {
            val path = Path().apply {
                moveTo(3*s,11*s); lineTo(12*s,3*s); lineTo(21*s,11*s)
                moveTo(6*s,9*s); lineTo(6*s,21*s); lineTo(10*s,21*s); lineTo(10*s,15*s)
                lineTo(14*s,15*s); lineTo(14*s,21*s); lineTo(18*s,21*s); lineTo(18*s,9*s)
            }
            drawPath(path, color, style = Stroke(1.8f*s))
        }
    }
}

@Composable
fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

fun formatTime(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
fun formatDate(timestamp: Long, pattern: String = "M월 d일 · HH:mm"): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, java.util.Locale.KOREAN))

