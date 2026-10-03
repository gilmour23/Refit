package com.refit.app.data.remote

import com.refit.app.data.local.RehabSessionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant

class RehabSessionRemoteDataSource(
    private val client: SupabaseClient? = SupabaseClientProvider.clientOrNull()
) {

    val isConfigured: Boolean
        get() = client != null

    suspend fun upsert(session: RehabSessionEntity): String {
        val supabase = requireNotNull(client) {
            "Supabase is not configured."
        }

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
            .upsert(
                value = dto,
                onConflict = "user_id,client_local_id"
            ) {
                select(Columns.list("id"))
            }
            .decodeSingle<RemoteSessionId>()
            .id
    }
}
