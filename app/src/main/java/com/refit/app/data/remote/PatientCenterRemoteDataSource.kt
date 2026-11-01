package com.refit.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

@Serializable data class CenterLinkDto(
    @SerialName("center_id") val centerId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("assigned_therapist_id") val assignedTherapistId: String? = null,
    @SerialName("joined_at") val joinedAt: String,
    @SerialName("revoked_at") val revokedAt: String? = null
)
@Serializable data class PatientProfileNameDto(val id: String, @SerialName("display_name") val displayName: String? = null)
@Serializable data class CenterNameDto(val id: String, val name: String)
@Serializable private data class RevokedCenterDto(
    @SerialName("center_id") val centerId: String,
    @SerialName("revoked_at") val revokedAt: String
)
data class PatientCenterSnapshot(
    val links: List<CenterLinkDto>,
    val centers: List<CenterNameDto>,
    val activePlan: ExercisePlanDto?,
    val displayName: String? = null
)

interface PatientCenterGateway {
    suspend fun read(owner: String, today: LocalDate = LocalDate.now()): PatientCenterSnapshot
    suspend fun acceptInvite(owner: String, code: String, consent: Boolean): CenterLinkDto
    suspend fun revoke(owner: String, centerId: String)
    suspend fun saveDisplayName(owner: String, displayName: String): String? = error("Profile saving unavailable")
}

class PatientCenterRemoteDataSource(
    private val client: SupabaseClient? = SupabaseClientProvider.clientOrNull()
) : PatientCenterGateway {
    private fun ownedClient(owner: String): SupabaseClient {
        val c = requireNotNull(client) { "Cloud is not configured" }
        check((c.auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.id == owner) {
            "Patient must be signed in"
        }
        return c
    }

    override suspend fun read(owner: String, today: LocalDate): PatientCenterSnapshot {
        val c = ownedClient(owner)
        val profile = c.from("profiles").select(Columns.list("id", "display_name")) {
            filter { eq("id", owner) }
        }.decodeList<PatientProfileNameDto>().singleOrNull { it.id == owner }
        val links = c.from("center_patients").select {
            filter { eq("patient_id", owner) }
        }.decodeList<CenterLinkDto>().filter { it.patientId == owner }
        val centerIds = links.filter { it.revokedAt == null }.map { it.centerId }.toSet()
        if (centerIds.isEmpty()) return PatientCenterSnapshot(links, emptyList(), null, profile?.displayName)
        val centers = c.from("centers").select(Columns.list("id", "name")).decodeList<CenterNameDto>()
            .filter { it.id in centerIds }
        val plans = c.from("exercise_plans").select {
            filter { eq("patient_id", owner); eq("exercise_type", "wrist_extension") }
        }.decodeList<ExercisePlanDto>().filter { it.centerId in centerIds }
        ownedClient(owner)
        return PatientCenterSnapshot(links, centers, selectActiveWristPlan(plans, owner, today), profile?.displayName)
    }

    override suspend fun saveDisplayName(owner: String, displayName: String): String? {
        val name = displayName.trim().takeIf { it.isNotEmpty() }
        require(name == null || name.length <= 80) { "Display name is too long" }
        val c = ownedClient(owner)
        val existing = c.from("profiles").select(Columns.list("id")) {
            filter { eq("id", owner) }
        }.decodeList<PatientProfileNameDto>().any { it.id == owner }
        ownedClient(owner)
        if (existing) {
            // PATCH only this column; existing birth year/side and other fields survive.
            c.from("profiles").update(buildJsonObject { put("display_name", name) }) {
                filter { eq("id", owner) }
            }
        } else c.from("profiles").insert(PatientProfileNameDto(owner, name))
        ownedClient(owner)
        return name
    }

    override suspend fun acceptInvite(owner: String, code: String, consent: Boolean): CenterLinkDto {
        require(consent) { "Patient consent is required" }
        val trimmed = code.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 256) { "Invalid invitation code" }
        val link = ownedClient(owner).postgrest.rpc("accept_center_invite", buildJsonObject { put("p_code", trimmed) })
            .decodeAs<CenterLinkDto>()
        check(link.patientId == owner) { "Invitation owner mismatch" }
        ownedClient(owner)
        return link
    }

    override suspend fun revoke(owner: String, centerId: String) {
        ownedClient(owner).postgrest.rpc("revoke_center_link", buildJsonObject { put("p_center_id", centerId) })
            .decodeAs<RevokedCenterDto>().also { check(it.centerId == centerId) }
        ownedClient(owner)
    }
}
