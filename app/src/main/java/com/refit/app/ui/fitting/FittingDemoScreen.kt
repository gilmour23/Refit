package com.refit.app.ui.fitting

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.refit.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

private const val DEMO_NOTICE = "데모 · 예시 치수 / 임시 모델"

@Composable
fun FittingDemoScreen(onHome: (() -> Unit)? = null, onOpenHistory: (() -> Unit)? = null, onBack: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var side by rememberSaveable { mutableStateOf<String?>(null) }
    var topUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sideUri by rememberSaveable { mutableStateOf<String?>(null) }
    var usingExample by rememberSaveable { mutableStateOf(false) }
    var topY by rememberSaveable { mutableFloatStateOf(.48f) }
    var sideY by rememberSaveable { mutableFloatStateOf(.48f) }
    var selectedView by rememberSaveable { mutableIntStateOf(0) }
    var yaw by rememberSaveable { mutableFloatStateOf(-30f) }
    var pitch by rememberSaveable { mutableFloatStateOf(15f) }
    var message by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val leave: () -> Unit = { if (step > 0) step-- else onBack() }
    BackHandler(onBack = leave)

    val topPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { topUri = uri.toString(); usingExample = false; topY = .48f }
    }
    val sidePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { sideUri = uri.toString(); usingExample = false; sideY = .48f }
    }
    // Keep document access for activity/process recreation; do not copy or upload photos.
    LaunchedEffect(topUri, sideUri) {
        withContext(Dispatchers.IO) {
            val selected = listOfNotNull(topUri, sideUri).toSet()
            val resolver = context.contentResolver
            selected.forEach { value -> runCatching {
                resolver.takePersistableUriPermission(Uri.parse(value), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } }
            // Do not release unrelated grants from other features.
        }
    }
    fun export(uri: Uri?, kind: String) {
        if (uri == null) return
        writing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val text = if (kind == "stl") DemoCuffMesh.stl()
                    else DemoCuffMesh.json(if (side == "왼쪽 손목") "left" else "right", topY, sideY)
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
            } }
            writing = false
            message = if (result.isSuccess) "예시 ${kind.uppercase()} 파일을 저장했어요." else "저장하지 못했어요. 저장 위치를 다시 선택해 주세요."
        }
    }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { export(it, "json") }
    val stlExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { export(it, "stl") }
    val titles = listOf("보조기 제작", "사진 등록", "손목 위치 확인", "치수 확인", "보조기 미리보기")
    key(step) {
    Page(titles[step], onBack = if (step == 0) null else leave, footer = {
        if (step < 4) PrimaryAction(when(step) {
            0 -> "사진 준비하기"
            1 -> "손목 위치 확인하기"
            2 -> "예시 치수 보기"
            else -> "임시 모델 미리보기"
        }, { message = null; step++ }, when(step) {
            0 -> side != null
            1 -> usingExample || (topUri != null && sideUri != null)
            else -> true
        })
        else PrimaryAction("홈으로 돌아가기", onBack, !writing)
        if (step == 0) BottomTabs("fitting", onHome ?: onBack, onOpenHistory ?: onBack, {})
    }) {
        if (step > 0) Text("제작 단계  ${step + 1} / 5", color = Accent, style = MaterialTheme.typography.bodySmall)
        if (step >= 2) Text(DEMO_NOTICE, color = Accent, style = MaterialTheme.typography.bodyMedium)
        when (step) {
            0 -> {
                Text("어느 쪽 손목인가요?", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("왼쪽 손목", "오른쪽 손목"), side, { side = it })
                Hint("손목 사진을 등록하고 치수와 보조기 모델을 확인하세요.")
                CuffPreview(yaw, pitch, { y, p -> yaw = y; pitch = p }, Modifier.height(160.dp))
                Hint("사진 분석은 아직 연결 전입니다. 선택한 사진과 관계없이 동일한 예시 치수와 손목 커프가 표시됩니다.")
            }
            1 -> {
                Hint("손가락이 위를 향하도록 손과 손목을 펴고, 위·옆 방향의 사진을 준비해 주세요. 크기 기준 카드도 함께 넣어주세요.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoGuide(topUri.takeUnless { usingExample }, false, topY, {}, Modifier.height(170.dp))
                        Text("위 사진", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton({ topPicker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) { Text("사진 선택") }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoGuide(sideUri.takeUnless { usingExample }, true, sideY, {}, Modifier.height(170.dp))
                        Text("옆 사진", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton({ sidePicker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) { Text("사진 선택") }
                    }
                }
                SecondaryAction(if (usingExample) "✓ 예시 그림 사용 중" else "예시 그림으로 진행", {
                    usingExample = true; topY = .48f; sideY = .48f
                })
                Hint("선택한 사진은 서버로 전송하지 않습니다. 사진이 없는 에뮬레이터에서는 예시 그림으로 진행할 수 있어요.")
            }
            2 -> {
                ChoiceRow(listOf("위 사진", "옆 사진"), if (selectedView == 0) "위 사진" else "옆 사진", { selectedView = if (it == "위 사진") 0 else 1 })
                Hint("사진을 눌러 손목 기준선을 옮겨주세요. 현재 기준선은 위치 확인용이며 치수 계산에는 아직 연결되지 않았습니다.")
                PhotoGuide(if (usingExample) null else if (selectedView == 0) topUri else sideUri,
                    selectedView == 1, if (selectedView == 0) topY else sideY,
                    { if (selectedView == 0) topY = it else sideY = it }, Modifier.height(300.dp))
                Text("손목 기준선 위치", style = MaterialTheme.typography.titleMedium)
                Slider(value = if (selectedView == 0) topY else sideY,
                    onValueChange = { if (selectedView == 0) topY = it else sideY = it }, valueRange = .1f.. .9f)
                Hint("위·옆 사진에서 같은 손목 위치를 확인해 주세요. 슬라이더로도 기준선을 이동할 수 있어요.")
            }
            3 -> {
                Hint("아래 숫자는 화면과 제작 흐름을 보여주기 위한 고정 예시값입니다. 사진에서 측정한 결과가 아닙니다.")
                LabelValue("대상", side ?: "손목")
                HorizontalDivider()
                LabelValue("손목 폭", "54 mm")
                LabelValue("손목 두께", "40 mm")
                LabelValue("커프 길이", "100 mm")
                HorizontalDivider()
                LabelValue("모델 벽 두께 · 예시", "3 mm")
                LabelValue("착용 여유 · 예시", "2 mm")
                Hint("확정된 보조기 설계가 없어 손목을 감싸는 열린 커프를 임시로 사용합니다. 모터·관절·전극 고정부는 포함하지 않습니다.")
            }
            4 -> {
                Text("손목 커프 · 임시 형태", style = MaterialTheme.typography.titleMedium)
                CuffPreview(yaw, pitch, { y, p -> yaw = y; pitch = p }, Modifier.height(300.dp))
                Hint("드래그해서 회전해 보세요. 미리보기와 STL은 같은 예시 모델을 사용합니다.")
                TextButton({ yaw = -30f; pitch = 15f }) { Text("처음 각도로") }
                Hint("예시 치수로 만든 모형입니다. 실제 맞춤 보조기나 착용용 출력물로 사용할 수 없습니다. STL 크기 기준은 mm입니다.")
                SecondaryAction("예시 치수 JSON 내보내기", { jsonExport.launch("ReFit_DEMO_dimensions.json") }, !writing)
                SecondaryAction("데모 STL 내보내기", { stlExport.launch("ReFit_DEMO_cuff_mm.stl") }, !writing)
                if (writing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        message?.let { Text(it, color = Accent) }
    }
    }
}

private fun readPhoto(context: android.content.Context, uri: String): Bitmap {
    val resolver = context.contentResolver
    val value = Uri.parse(uri)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(value).use { BitmapFactory.decodeStream(requireNotNull(it), null, options) }
    require(options.outWidth > 0 && options.outHeight > 0)
    options.inSampleSize = 1
    while (max(options.outWidth, options.outHeight) / options.inSampleSize > 1280) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    val bitmap = resolver.openInputStream(value).use { requireNotNull(BitmapFactory.decodeStream(requireNotNull(it), null, options)) }
    val orientation = resolver.openInputStream(value).use { ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
    val matrix = Matrix().apply { when(orientation) {
        2 -> setScale(-1f, 1f)
        3 -> setRotate(180f)
        4 -> setScale(1f, -1f)
        5 -> { setRotate(90f); postScale(-1f, 1f) }
        6 -> setRotate(90f)
        7 -> { setRotate(-90f); postScale(-1f, 1f) }
        8 -> setRotate(-90f)
    } }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun photoRect(width: Float, height: Float, bitmap: Bitmap?): Rect {
    if (bitmap == null) return Rect(0f, 0f, width, height)
    val scale = min(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * scale; val h = bitmap.height * scale
    return Rect((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2)
}

@Composable
private fun PhotoGuide(uri: String?, sideView: Boolean, guideY: Float, onGuide: (Float) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val photo by produceState<Result<Bitmap>?>(null, uri) {
        value = null
        if (uri != null) value = withContext(Dispatchers.IO) { runCatching { readPhoto(context, uri) } }
    }
    val bitmap = photo?.getOrNull()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0F4F3))
            .semantics { contentDescription = if (sideView) "옆 사진 손목 기준선" else "위 사진 손목 기준선" }
            .pointerInput(bitmap) { detectTapGestures { tap ->
                val r = photoRect(size.width.toFloat(), size.height.toFloat(), bitmap)
                if (r.contains(tap)) onGuide(((tap.y-r.top)/r.height).coerceIn(.1f,.9f))
            } }) {
            val r = photoRect(size.width, size.height, bitmap)
            if (bitmap != null) drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(r.left.roundToInt(),r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt(),r.height.roundToInt()))
            else if (uri == null) {
                val w=size.width; val h=size.height
                val body = Path().apply {
                    moveTo(w*.28f,h*.95f); lineTo(w*.37f,h*.48f)
                    lineTo(w*.29f,h*.33f); lineTo(w*.26f,h*.26f)
                    quadraticBezierTo(w*.27f,h*.21f,w*.32f,h*.25f)
                    lineTo(w*.38f,h*.32f); lineTo(w*.37f,h*.13f)
                    quadraticBezierTo(w*.4f,h*.09f,w*.43f,h*.14f)
                    lineTo(w*.44f,h*.28f); lineTo(w*.45f,h*.09f)
                    quadraticBezierTo(w*.48f,h*.05f,w*.51f,h*.1f)
                    lineTo(w*.52f,h*.28f); lineTo(w*.54f,h*.12f)
                    quadraticBezierTo(w*.58f,h*.08f,w*.6f,h*.14f)
                    lineTo(w*.6f,h*.3f); lineTo(w*.62f,h*.19f)
                    quadraticBezierTo(w*.66f,h*.15f,w*.68f,h*.21f)
                    lineTo(w*.66f,h*.4f); lineTo(w*.61f,h*.48f)
                    lineTo(w*.73f,h*.95f); close()
                }
                if (!sideView) drawPath(body, Color(0xFFD7BBA6))
                else {
                    val arm = Path().apply {
                        moveTo(w*.35f,h*.95f); lineTo(w*.43f,h*.49f); lineTo(w*.42f,h*.16f)
                        quadraticBezierTo(w*.46f,h*.08f,w*.5f,h*.17f)
                        lineTo(w*.57f,h*.37f); lineTo(w*.57f,h*.49f); lineTo(w*.68f,h*.95f); close()
                    }; drawPath(arm, Color(0xFFD7BBA6))
                }
                drawRoundRect(Color.White, Offset(w*.77f,h*.62f), androidx.compose.ui.geometry.Size(w*.17f,h*.12f))
                drawRoundRect(Accent.copy(alpha=.4f), Offset(w*.8f,h*.65f), androidx.compose.ui.geometry.Size(w*.08f,h*.02f))
            }
            if (uri == null || bitmap != null) {
                val y = r.top + r.height * guideY
                drawLine(Accent, Offset(r.left+r.width*.12f,y), Offset(r.right-r.width*.12f,y), 3.dp.toPx())
                drawCircle(Accent, 5.dp.toPx(), Offset(r.left+r.width*.5f,y))
            }
        }
        if (uri != null && photo == null) Hint("사진을 여는 중…")
        if (uri != null && photo?.isFailure == true) Text("사진을 열 수 없어요. 뒤로 가서 다른 사진을 선택해 주세요.", color = MaterialTheme.colorScheme.error)
        if (uri == null) Text("촬영 방향 예시 그림", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CuffPreview(yaw: Float, pitch: Float, onRotate: (Float, Float) -> Unit, modifier: Modifier) {
    val mesh = remember { DemoCuffMesh.triangles() }
    val rotate by rememberUpdatedState(onRotate)
    val currentYaw by rememberUpdatedState(yaw)
    val currentPitch by rememberUpdatedState(pitch)
    Canvas(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF0F4F3))
        .semantics { contentDescription = "예시 손목 커프 3D 미리보기, 드래그하여 회전" }
        .pointerInput(Unit) { detectDragGestures { change, delta ->
            change.consume()
            rotate((currentYaw+delta.x*.5f)%360f,(currentPitch+delta.y*.4f).coerceIn(-75f,75f))
        } }) {
        val ry=Math.toRadians(yaw.toDouble()); val rx=Math.toRadians(pitch.toDouble())
        fun turn(p: DemoCuffMesh.Point): DemoCuffMesh.Point {
            val x=p.x*cos(ry)+p.z*sin(ry); val z=-p.x*sin(ry)+p.z*cos(ry)
            return DemoCuffMesh.Point(x,p.y*cos(rx)-z*sin(rx),p.y*sin(rx)+z*cos(rx))
        }
        val scale=min(size.width/110f,size.height/145f)
        fun screen(p: DemoCuffMesh.Point)=Offset(size.width/2+(p.x*scale).toFloat(),size.height/2+(p.y*scale).toFloat())
        drawOval(Color(0x14226D65), Offset(size.width*.23f,size.height*.84f), androidx.compose.ui.geometry.Size(size.width*.54f,size.height*.06f))
        mesh.map { listOf(turn(it.a),turn(it.b),turn(it.c)) }.sortedBy { it.sumOf { p -> p.z } }.forEach { points ->
            val path = Path().apply { val first=screen(points[0]); moveTo(first.x,first.y); points.drop(1).forEach { val p=screen(it); lineTo(p.x,p.y) }; close() }
            val a=points[0]; val b=points[1]; val c=points[2]
            val ux=b.x-a.x; val uy=b.y-a.y; val uz=b.z-a.z
            val vx=c.x-a.x; val vy=c.y-a.y; val vz=c.z-a.z
            val nx=uy*vz-uz*vy; val ny=uz*vx-ux*vz; val nz=ux*vy-uy*vx
            val light=(.65+.3*abs((nx*.3-ny*.3+nz*.9)/sqrt(nx*nx+ny*ny+nz*nz))).toFloat().coerceIn(.45f,1f)
            drawPath(path, Color(.15f*light,.53f*light,.48f*light))
            drawPath(path, Color.White.copy(alpha=.06f), style=Stroke(.5f))
        }
    }
}
