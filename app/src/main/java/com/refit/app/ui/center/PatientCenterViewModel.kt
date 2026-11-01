package com.refit.app.ui.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.refit.app.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientCenterUiState(
    val loading: Boolean = false,
    val snapshot: PatientCenterSnapshot? = null,
    val message: String? = null
)

class PatientCenterViewModel(
    private val owner: String,
    private val remote: PatientCenterGateway = PatientCenterRemoteDataSource()
) : ViewModel() {
    private val _state = MutableStateFlow(PatientCenterUiState())
    val state = _state.asStateFlow()

    private val operations = kotlinx.coroutines.sync.Mutex()

    fun refresh() {
        if (!_state.value.loading) perform { remote.read(owner) }
    }
    fun accept(code: String, consent: Boolean) {
        if (!consent) return
        perform { remote.acceptInvite(owner, code, consent); remote.read(owner) }
    }
    fun revoke(centerId: String) = perform(
        failure = "센터 연결 해제를 확인하지 못했어요. 기록 공유가 계속될 수 있으니 연결 상태를 다시 확인해 주세요."
    ) { remote.revoke(owner, centerId); remote.read(owner) }

    fun verifyPlan(planId: String, onVerified: (ExercisePlanDto) -> Unit) = perform(onSuccess = { snapshot ->
        if (snapshot.activePlan?.id == planId) onVerified(snapshot.activePlan)
        else _state.update { it.copy(message = "오늘의 계획이나 센터 연결이 변경되었어요. 현재 계획을 다시 확인해 주세요.") }
    }) { remote.read(owner) }

    private fun perform(
        failure: String = "센터 연결과 계획을 확인하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.",
        onSuccess: ((PatientCenterSnapshot) -> Unit)? = null,
        operation: suspend () -> PatientCenterSnapshot
    ) {
        viewModelScope.launch {
            operations.lock()
            try {
                _state.update { it.copy(loading = true, message = null) }
                val snapshot = operation()
                _state.value = PatientCenterUiState(snapshot = snapshot)
                onSuccess?.invoke(snapshot)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = PatientCenterUiState(message = failure) }
            finally { operations.unlock() }
        }
    }
}
