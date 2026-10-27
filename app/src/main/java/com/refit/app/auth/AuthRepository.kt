package com.refit.app.auth

import com.refit.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.SignOutScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val client: SupabaseClient? = SupabaseClientProvider.clientOrNull()
) {
    companion object {
        const val MIN_NEW_PASSWORD_LENGTH = 12
        // Activities can create separate repositories over the app's shared
        // client. An earlier root's logout must finish before a new login.
        private val authOperation = Mutex()
    }

    val authState: Flow<AuthState> =
        client?.auth?.sessionStatus
            ?.map { status ->
                when (status) {
                    SessionStatus.Initializing -> AuthState.Loading

                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user == null) {
                            AuthState.Error("로그인 세션에 사용자 정보가 없습니다.")
                        } else {
                            AuthState.Authenticated(
                                userId = user.id,
                                email = user.email
                            )
                        }
                    }

                    is SessionStatus.NotAuthenticated ->
                        AuthState.Unauthenticated

                    is SessionStatus.RefreshFailure -> {
                        // RefreshFailure is retryable; revoked sessions are reported
                        // by the SDK as NotAuthenticated and never use this fallback.
                        val user = client.auth.sessionManager.loadSessionOrNull()?.user
                        if (user == null) {
                            AuthState.Error("로그인 세션을 갱신하지 못했습니다. 다시 로그인해주세요.")
                        } else {
                            AuthState.Authenticated(user.id, user.email, sessionRefreshPending = true)
                        }
                    }
                }
            }
            ?: flowOf(
                AuthState.Error(
                    "계정 서비스를 사용할 수 없습니다. 관리자에게 문의해주세요."
                )
            )

    suspend fun signIn(email: String, password: String) = authOperation.withLock {
        val authClient = requireClient().auth
        authClient.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) = authOperation.withLock {
        require(password.length >= MIN_NEW_PASSWORD_LENGTH) { "새 비밀번호는 12자 이상으로 설정해주세요." }
        val authClient = requireClient().auth
        authClient.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = authOperation.withLock {
        val authClient = requireClient().auth
        val accessToken = authClient.currentAccessTokenOrNull()
            ?: authClient.sessionManager.loadSessionOrNull()?.accessToken
        // Delete reusable credentials before making any HTTP request. Keep
        // only the outgoing JWT in memory for the SDK's explicit-token logout.
        withContext(NonCancellable) {
            authClient.stopAutoRefreshForCurrentSession()
            authClient.clearSession()
        }
        try {
            if (accessToken != null) {
                withTimeout(10_000) {
                    authClient.admin.signOut(accessToken, SignOutScope.LOCAL)
                }
            }
        } finally {
            // Finish cleanup even on cancellation or a late SDK emission.
            // The mutex prevents this cleanup from deleting a new login.
            withContext(NonCancellable) { authClient.clearSession() }
        }
    }

    private fun requireClient(): SupabaseClient =
        requireNotNull(client) {
            "Supabase is not configured."
        }
}
