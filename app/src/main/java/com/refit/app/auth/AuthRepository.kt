package com.refit.app.auth

import com.refit.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
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

                    is SessionStatus.RefreshFailure ->
                        AuthState.Error(
                            "로그인 세션을 갱신하지 못했습니다. 네트워크 연결을 확인해주세요."
                        )
                }
            }
            ?: flowOf(
                AuthState.Error(
                    "Supabase가 설정되지 않았습니다. local.properties에 " +
                        "SUPABASE_URL과 SUPABASE_PUBLISHABLE_KEY를 설정해주세요."
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
        requireClient().auth.signOut()
    }

    private fun requireClient(): SupabaseClient =
        requireNotNull(client) {
            "Supabase is not configured."
        }
}
