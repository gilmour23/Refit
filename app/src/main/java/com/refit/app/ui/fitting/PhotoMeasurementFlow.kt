package com.refit.app.ui.fitting

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.refit.app.BuildConfig
import com.refit.app.data.vision.*
import com.refit.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun PhotoMeasurementFlow(topUri: String, sideUri: String, side: String,
    onRegister: (suspend (String, String) -> Boolean)?, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("refit_vision_connection", 0) }
    var server by rememberSaveable { mutableStateOf(prefs.getString("server", BuildConfig.VISION_SERVER_URL) ?: "") }
    var editServer by rememberSaveable { mutableStateOf(server.isBlank()) }
    var request by remember { mutableIntStateOf(if (server.isBlank()) 0 else 1) }
    var result by remember { mutableStateOf<BraceMeasurement?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val report by produceState<Bitmap?>(null, result?.reportBase64) {
        value = withContext(Dispatchers.IO) { result?.reportBase64?.let { encoded -> runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            require(opts.outWidth > 0 && opts.outHeight > 0)
            opts.inSampleSize = 1
            while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 2400) opts.inSampleSize *= 2
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull() } }
    }
    LaunchedEffect(topUri, sideUri, request) {
        if (request == 0) return@LaunchedEffect
        result = null; accepted = false; message = null; loading = true
        try {
            fun photo(uri: String, name: String): MeasurementPhoto {
                val u = Uri.parse(uri)
                val mime = context.contentResolver.getType(u) ?: "image/jpeg"
                return MeasurementPhoto(name, mime) { requireNotNull(context.contentResolver.openInputStream(u)) }
            }
            result = BraceMeasurementClient(allowHttp = BuildConfig.DEBUG).measure(server,
                photo(topUri, "top.jpg"), photo(sideUri, "side.jpg"))
            ensureActive()
            prefs.edit().putString("server", server.trim()).apply()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "측정하지 못했어요. 서버와 사진을 확인해 주세요." }
        finally { loading = false }
    }
    BackHandler(enabled = !saving, onBack = onBack)
    val measured = result
    Page("사진 자동 측정", onBack = if (saving) null else onBack, footer = {
        if (measured != null) PrimaryAction(if (saving) "측정 결과 저장 중…" else "치수 등록하고 운동으로", {
            scope.launch {
                saving = true
                try {
                    val stored = onRegister?.invoke(side, measured.json) == true
                    if (!stored) message = "측정 결과를 저장하지 못했어요. 다시 시도해 주세요."
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "측정 결과를 저장하지 못했어요. 다시 시도해 주세요." }
                finally { saving = false }
            }
        }, !saving && onRegister != null && (measured.warnings.isEmpty() || accepted))
        else PrimaryAction(if (loading) "카드와 손을 분석하는 중…" else "사진 분석하기", {
            editServer = false; request++
        }, !loading && server.isNotBlank())
    }) {
        Text("${if (side == "left") "왼쪽" else "오른쪽"} 손목 · 사진 2장", style = MaterialTheme.typography.titleMedium)
        Hint("카드와 손을 자동으로 찾아 11개 치수를 계산합니다. 모서리를 직접 선택할 필요가 없어요.")
        if (editServer) {
            OutlinedTextField(server, { server = it; result = null; accepted = false },
                label = { Text("측정 서버 주소") }, placeholder = { Text("https://측정서버주소") },
                singleLine = true, enabled = !loading && !saving, modifier = Modifier.fillMaxWidth())
            Hint("Python 측정 서버를 실행한 주소를 한 번 입력해 주세요. 앱과 같은 PC의 에뮬레이터는 디버그 빌드에서 http://10.0.2.2:8000을 사용할 수 있어요.")
        } else TextButton({ editServer = true; result = null; accepted = false }, enabled = !loading && !saving) { Text("측정 서버 설정") }
        Hint("선택한 원본 사진 2장은 측정 서버로 전송됩니다. 서버는 사진과 결과를 보관합니다.")
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Hint("사진 크기와 서버 상태에 따라 최대 1분 정도 걸릴 수 있어요.") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (measured != null) {
            Text("측정 결과", style = MaterialTheme.typography.titleLarge)
            Hint("사진에서 계산한 추정 치수 · 단위 mm")
            measured.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            if (measured.warnings.isNotEmpty()) Row {
                Checkbox(accepted, { accepted = it })
                Text("주의사항과 측정 위치를 확인했어요.", modifier = Modifier.padding(top = 12.dp))
            }
            report?.let { image ->
                Image(image.asImageBitmap(), "사진 위 측정 위치와 치수 보고서", Modifier.fillMaxWidth().height(380.dp))
                TextButton({ zoom = true }) { Text("보고서 확대하기") }
            } ?: Hint(if (measured.reportBase64 == null) "보고서 이미지가 없어 치수표만 표시합니다." else "보고서 이미지를 열 수 없어 치수표만 표시합니다.")
            measured.dimensions.forEach { dimension ->
                LabelValue("${dimension.id}. ${dimension.name}", "${dimension.valueMm} mm")
                HorizontalDivider()
            }
            Hint("치수 등록은 실제 보조기의 출력·제작 완료를 의미하지 않습니다. 3D 모델 생성은 아직 연결 전입니다.")
            TextButton({ result = null; request++ }, enabled = !saving) { Text("같은 사진으로 다시 측정") }
            TextButton(onBack, enabled = !saving) { Text("사진 다시 선택") }
        }
    }
    if (zoom && report != null) Dialog({ zoom = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var x by remember { mutableFloatStateOf(0f) }; var y by remember { mutableFloatStateOf(0f) }
        Surface(Modifier.fillMaxSize()) {
            Column {
                TextButton({ zoom = false }) { Text("닫기") }
                Hint("두 손가락으로 확대하고 끌어서 이동하세요.")
                Image(report!!.asImageBitmap(), "확대한 측정 보고서", Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTransformGestures { _, pan, change, _ ->
                        scale = (scale * change).coerceIn(1f, 5f); x += pan.x; y += pan.y
                    } }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = x, translationY = y))
            }
        }
    }
}
