package com.refit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

val Ink = Color(0xFF202930)
val Muted = Color(0xFF68747D)
val Accent = Color(0xFF55439B)
val Calm = Color(0xFF236E62)
val CalmSurface = Color(0xFFEAF5F1)
val PageBackground = Color(0xFFF5F6F8)

@Composable
fun ReFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Accent, secondary = Calm,
            background = PageBackground, surface = Color.White,
            onSurface = Ink, onBackground = Ink, outline = Color(0xFFDCE1E5)),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 18.sp, lineHeight = 28.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
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
            Column(Modifier.fillMaxWidth().background(PageBackground).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = footer)
        }) { inset ->
        val body = Modifier.fillMaxSize().padding(inset).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 20.dp)
        Column(body, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)) {
            if (!compact) Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(16.dp)) { Text(text) }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(14.dp)) { Text(text) }
}

@Composable
fun Hint(text: String) { Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) }

@Composable
fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DemoNotice() {
    Surface(color = Color(0xFFEEEAF8), shape = RoundedCornerShape(10.dp)) {
        Text("데모 · 모의 신호", color = Accent, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: String?, onChoose: (String) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            Column(Modifier.weight(1f)) {
                if (selected == option) PrimaryAction("✓ $option", { onChoose(option) }, enabled)
                else SecondaryAction(option, { onChoose(option) }, enabled)
            }
        }
    }
}

@Composable
fun BottomTabs(selected: String, onHome: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onHome, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("홈", color = if (selected == "home") Accent else Muted, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onHistory, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("운동 기록", color = if (selected == "history") Accent else Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun formatTime(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
fun formatDate(timestamp: Long, pattern: String = "M월 d일 · HH:mm"): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, java.util.Locale.KOREAN))
