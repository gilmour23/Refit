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
    private val remote: PatientCenterRemoteDataSource = PatientCenterRemoteDataSource()
) : ViewModel() {
    private val _state = MutableStateFlow(PatientCenterUiState())
    val state = _state.asStateFlow()

    fun refresh() = perform { remote.read(owner) }
    fun accept(code: String, consent: Boolean) {
        if (!consent) return
        perform { remote.acceptInvite(owner, code, consent); remote.read(owner) }
    }
    fun revoke(centerId: String) = perform { remote.revoke(owner, centerId); remote.read(owner) }

    private fun perform(operation: suspend () -> PatientCenterSnapshot) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, snapshot = null, message = null) }
        viewModelScope.launch {
            try { _state.value = PatientCenterUiState(snapshot = operation()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _state.value = PatientCenterUiState(message = "센터 연결과 계획을 확인하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.")
            }
        }
    }
}
