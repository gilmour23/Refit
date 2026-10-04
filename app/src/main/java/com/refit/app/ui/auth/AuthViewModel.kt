package com.refit.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.refit.app.auth.AuthRepository
import com.refit.app.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSubmitting: Boolean = false,
    val message: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    // Owned by the activity-retained AuthViewModel, so rotation preserves an
    // exercise, while logout and account changes dispose all account state.
    val accountViewModelStoreOwner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private var activeUserId: String? = null
    private var lastAuthenticated: AuthState.Authenticated? = null
    private var explicitlySignedOut = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.authState.collect { incoming ->
                // Only a new successful credential submission can unlock an
                // explicit logout; delayed SDK restoration must not do so.
                if (!explicitlySignedOut) acceptAuthState(incoming)
            }
        }
    }

    private fun acceptAuthState(incoming: AuthState) {
        // Retain normal same-owner focus/reload behavior outside logout.
        val state = if (incoming == AuthState.Loading) lastAuthenticated ?: incoming else incoming
        val owner = (state as? AuthState.Authenticated)?.userId
        if (state != AuthState.Loading && owner != activeUserId) {
            accountViewModelStoreOwner.viewModelStore.clear()
            activeUserId = owner
        }
        lastAuthenticated = state as? AuthState.Authenticated
        _authState.value = state
    }

    fun signIn(email: String, password: String) {
        submit(email, password, isSignUp = false)
    }

    fun signUp(email: String, password: String) {
        submit(email, password, isSignUp = true)
    }

    fun signOut() {
        if (_uiState.value.isSubmitting) return
        _uiState.value = AuthUiState(isSubmitting = true)
        explicitlySignedOut = true
        accountViewModelStoreOwner.viewModelStore.clear()
        activeUserId = null
        lastAuthenticated = null
        _authState.value = AuthState.Unauthenticated
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                repository.signOut()
                _uiState.value = AuthUiState()
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.value = AuthUiState(
                    message = "계정 접근을 종료했습니다. 서버 세션 해지를 확인하지 못했습니다. 연결을 확인해주세요."
                )
            }
        }
    }

    private fun submit(
        email: String,
        password: String,
        isSignUp: Boolean
    ) {
        if (_uiState.value.isSubmitting) return
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank()) {
            _uiState.value = AuthUiState(message = "이메일을 입력해주세요.")
            return
        }

        if (password.length < 6) {
            _uiState.value = AuthUiState(message = "비밀번호는 6자 이상이어야 합니다.")
            return
        }

        _uiState.value = AuthUiState(isSubmitting = true)
        viewModelScope.launch {

            runCatching {
                if (isSignUp) {
                    repository.signUp(trimmedEmail, password)
                } else {
                    repository.signIn(trimmedEmail, password)
                }
            }.onSuccess {
                explicitlySignedOut = false
                acceptAuthState(repository.authState.first())
                _uiState.value = AuthUiState(
                    message = if (isSignUp) {
                        "회원가입 요청이 완료되었습니다. 이메일 확인이 필요한 설정이라면 메일을 확인해주세요."
                    } else {
                        null
                    }
                )
            }.onFailure { error ->
                currentCoroutineContext().ensureActive()
                _uiState.value = AuthUiState(
                    message = error.message ?: "인증 요청에 실패했습니다."
                )
            }
        }
    }

    override fun onCleared() {
        accountViewModelStoreOwner.viewModelStore.clear()
        super.onCleared()
    }
}
