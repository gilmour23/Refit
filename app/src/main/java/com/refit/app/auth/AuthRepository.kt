package com.refit.app.auth

import com.refit.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.SignOutScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val client: SupabaseClient? = SupabaseClientProvider.clientOrNull()
) {

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

    suspend fun signIn(email: String, password: String) {
        val authClient = requireClient().auth
        authClient.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        val authClient = requireClient().auth
        authClient.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        val authClient = requireClient().auth
        try {
            authClient.signOut(SignOutScope.LOCAL)
        } finally {
            // An offline HTTP failure must not leave reusable credentials on
            // a shared device. Also finish local deletion on cancellation.
            withContext(NonCancellable) { authClient.clearSession() }
        }
    }

    private fun requireClient(): SupabaseClient =
        requireNotNull(client) {
            "Supabase is not configured."
        }
}
