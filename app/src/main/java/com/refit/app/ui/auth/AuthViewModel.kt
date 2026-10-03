package com.refit.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.refit.app.auth.AuthRepository
import com.refit.app.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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

    val authState: StateFlow<AuthState> = repository.authState
        .map { incoming ->
            // Android Auth emits Loading when the app loses focus. Retain the
            // already-known owner until the SDK confirms login or logout.
            val state = if (incoming == AuthState.Loading) lastAuthenticated ?: incoming else incoming
            val owner = (state as? AuthState.Authenticated)?.userId
            if (state != AuthState.Loading && owner != activeUserId) {
                accountViewModelStoreOwner.viewModelStore.clear()
                activeUserId = owner
            }
            lastAuthenticated = state as? AuthState.Authenticated
            state
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading
        )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signIn(email: String, password: String) {
        submit(email, password, isSignUp = false)
    }

    fun signUp(email: String, password: String) {
        submit(email, password, isSignUp = true)
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repository.signOut() }
                .onFailure { error ->
                    _uiState.value = AuthUiState(
                        message = error.message ?: "로그아웃에 실패했습니다."
                    )
                }
        }
    }

    private fun submit(
        email: String,
        password: String,
        isSignUp: Boolean
    ) {
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank()) {
            _uiState.value = AuthUiState(message = "이메일을 입력해주세요.")
            return
        }

        if (password.length < 6) {
            _uiState.value = AuthUiState(message = "비밀번호는 6자 이상이어야 합니다.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isSubmitting = true)

            runCatching {
                if (isSignUp) {
                    repository.signUp(trimmedEmail, password)
                } else {
                    repository.signIn(trimmedEmail, password)
                }
            }.onSuccess {
                _uiState.value = AuthUiState(
                    message = if (isSignUp) {
                        "회원가입 요청이 완료되었습니다. 이메일 확인이 필요한 설정이라면 메일을 확인해주세요."
                    } else {
                        null
                    }
                )
            }.onFailure { error ->
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
