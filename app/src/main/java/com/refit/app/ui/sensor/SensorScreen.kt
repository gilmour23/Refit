package com.refit.app.ui.sensor

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.refit.app.data.source.EmgConnectionState
import com.refit.app.ui.components.*
import com.refit.app.ui.session.SessionViewModel
import com.refit.app.ui.session.EmgGraph

@Composable
fun SensorScreen(viewModel: SessionViewModel, owner: String, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(owner) { context.getSharedPreferences("guide-$owner", Application.MODE_PRIVATE) }
    var service by rememberSaveable { mutableStateOf(prefs.getString("sensor-service", "") ?: "") }
    var characteristic by rememberSaveable { mutableStateOf(prefs.getString("sensor-characteristic", "") ?: "") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) { permissionMessage = null; viewModel.scanSensors() }
        else permissionMessage = "검색하려면 블루투스 권한을 허용해 주세요. Android 11 이하에서는 위치 권한도 필요해요."
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    BackHandler(onBack = onBack)
    DisposableEffect(viewModel) { onDispose { viewModel.stopSensorScan() } }
    Page("센서 연결", onBack) {
        LabelValue("신호 모드", if (state.dataMode == "demo") "데모 · 모의 신호" else "실제 센서 · 원시값")
        val connection = state.sensorState
        Text(when (connection) {
            is EmgConnectionState.Connected -> "${connection.deviceName ?: "선택한 센서"} · 연결됨"
            is EmgConnectionState.Connecting -> "센서에 연결하는 중…"
            EmgConnectionState.Scanning -> "연결할 센서를 찾는 중…"
            is EmgConnectionState.Error -> "연결 오류 · ${connection.message}"
            else -> "연결된 센서가 없어요"
        }, style = MaterialTheme.typography.titleMedium)
        state.sensorAddress?.let { LabelValue("센서 주소", it) }
        state.sensorRssi?.let { LabelValue("신호 세기", "$it dBm") }
        permissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.sensorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ permissions.launch(if (Build.VERSION.SDK_INT >= 31)
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }, enabled = !state.isConnecting) {
                Text(if (state.sensorScanning) "다시 검색" else "주변 센서 검색")
            }
            TextButton({ runCatching { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { permissionMessage = "기기 설정에서 블루투스를 켜 주세요." } }) { Text("블루투스 켜기") }
        }
        if (state.sensorScanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(viewModel::stopSensorScan) { Text("검색 중지") } }
        if (state.sensorDevices.isEmpty()) Hint("검색된 BLE 기기가 여기에 표시됩니다. 센서 전원을 켜고 가까이 두세요.")
        state.sensorDevices.forEach { device ->
            OutlinedButton({ selected = device.address }, modifier = Modifier.fillMaxWidth(), enabled = !state.isConnecting) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${if (selected == device.address) "✓ " else ""}${device.name ?: "이름 없는 BLE 기기"}")
                    Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        Text("센서 통신 설정", style = MaterialTheme.typography.titleMedium)
        Hint("센서팀에서 정한 서비스 UUID와 수신 특성 UUID를 입력하세요. 기기 이름만으로 EMG 센서 여부를 판단하지 않습니다.")
        OutlinedTextField(service, { service = it; confirmed = false }, label = { Text("서비스 UUID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !state.isConnecting)
        OutlinedTextField(characteristic, { characteristic = it; confirmed = false }, label = { Text("수신 특성 UUID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !state.isConnecting)
        Row {
            Checkbox(confirmed, { confirmed = it }, enabled = !state.isConnecting)
            Text("펌웨어가 부호 있는 16비트 리틀 엔디언(INT16_LE) 알림을 전송하는지 확인했어요.", Modifier.padding(top = 12.dp))
        }
        Hint("형식이 다르면 파서를 수정해야 합니다. 현재 값은 보정 전 원시값이며 µV나 실제 운동 동작으로 판정하지 않습니다.")
        PrimaryAction(if (state.isConnecting) "연결 확인 중…" else "선택한 센서 연결", {
            selected?.let { viewModel.connectSensor(it, service, characteristic, confirmed) }
        }, selected != null && service.isNotBlank() && characteristic.isNotBlank() && confirmed && !state.isConnecting)
        if (state.connected || state.isConnecting) SecondaryAction("센서 연결 해제", viewModel::disconnectSensor)
        if (state.dataMode == "sensor") {
            LabelValue("수신 중인 원시값", state.sensorRawValue?.toString() ?: "신호 대기 중")
            if (state.sensorSamples.isNotEmpty()) EmgGraph(state.sensorSamples)
            else Hint("연결 뒤 센서가 데이터를 전송하면 그래프가 표시됩니다.")
        }
        HorizontalDivider()
        TextButton(viewModel::useDemoSensor, enabled = !state.isConnecting) { Text("센서 없이 데모 모드 사용") }
        Hint("실제 검색 목록에 가상 기기를 추가하지 않습니다. 데모 기록은 실제 센서 기록과 구분됩니다.")
    }
}
