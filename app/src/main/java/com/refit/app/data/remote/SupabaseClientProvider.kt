package com.refit.app.data.remote

import com.refit.app.BuildConfig
import android.content.Context
import com.refit.app.auth.EncryptedSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    private val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            ) {
                install(Auth) {
                    sessionManager = EncryptedSessionManager(requireNotNull(appContext), BuildConfig.SUPABASE_URL)
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest)
            }
        }
    }

    fun clientOrNull(): SupabaseClient? = client
}
