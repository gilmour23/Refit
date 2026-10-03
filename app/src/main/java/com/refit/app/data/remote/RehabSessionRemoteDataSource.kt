package com.refit.app.data.remote

import com.refit.app.data.local.RehabSessionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant

class RehabSessionRemoteDataSource(
    private val client: SupabaseClient? = SupabaseClientProvider.clientOrNull()
) {

    val isConfigured: Boolean
        get() = client != null

    val syncOwner: Flow<String?> = client?.auth?.sessionStatus
        ?.map { (it as? SessionStatus.Authenticated)?.session?.user?.id }
        ?.distinctUntilChanged() ?: flowOf(null)

    fun canSync(userId: String): Boolean =
        (client?.auth?.sessionStatus?.value as? SessionStatus.Authenticated)
            ?.session?.user?.id == userId

    suspend fun upsert(session: RehabSessionEntity): String {
        val supabase = requireNotNull(client) {
            "Supabase is not configured."
        }

        // Check immediately before building the request. RLS is the final
        // authority if the SDK's active token changes while the request runs.
        check(canSync(session.userId)) { "The record owner is not signed in." }

        val dto = RehabSessionUpsertDto(
            userId = session.userId,
            clientLocalId = session.clientLocalId,
            createdAt = Instant.ofEpochMilli(session.createdAt).toString(),
            exerciseType = session.exerciseName,
            durationSeconds = session.durationSeconds,
            sampleCount = session.sampleCount,
            rms = session.rms,
            mav = session.mav,
            peakActivation = session.peakActivation,
            updatedAt = Instant.ofEpochMilli(session.updatedAt).toString()
        )

        return supabase
            .from("rehab_sessions")
            .upsert(value = dto) {
                onConflict = "user_id,client_local_id"
                select(Columns.list("id"))
            }
            .decodeSingle<RemoteSessionId>()
            .id.also { UUID.fromString(it) }
    }
}

