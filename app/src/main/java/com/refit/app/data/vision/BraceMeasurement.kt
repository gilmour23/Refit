package com.refit.app.data.vision

import kotlinx.serialization.json.*
import java.io.IOException

data class BraceDimension(val id: Int, val name: String, val valueMm: Double)

data class BraceMeasurement(
    val sessionId: String,
    val dimensions: List<BraceDimension>,
    val warnings: List<String>,
    val reportBase64: String?,
    val json: String
) {
    companion object {
        val names = listOf("팔 쪽 40mm 위치 · 폭", "팔 쪽 40mm 위치 · 두께", "손목 폭", "손목 두께",
            "새끼손가락 기준점–손목 세로 거리", "엄지 기준점–손목 세로 거리", "검지–엄지 기준점 세로 거리",
            "엄지 기준점에서 새끼 쪽 테두리", "검지 기준점에서 새끼 쪽 테두리", "엄지 기준점 높이의 두께", "검지 기준점 높이의 두께")

        fun parse(text: String): BraceMeasurement {
            try {
                val obj = Json.parseToJsonElement(text).jsonObject
                if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true)
                    throw IOException(obj["message"]?.jsonPrimitive?.contentOrNull ?: "사진을 분석하지 못했어요. 다시 촬영해 주세요.")
                val dims = obj["dims_mm"]?.jsonObject ?: throw IOException("측정 치수가 누락됐어요.")
                val values = (1..11).map { id ->
                    val value = dims[id.toString()]?.jsonPrimitive?.doubleOrNull
                    if (value == null || !value.isFinite() || value <= 0)
                        throw IOException("${id}번 치수를 측정하지 못했어요. 해당 사진을 다시 촬영해 주세요.")
                    BraceDimension(id, names[id - 1], value)
                }
                val session = obj["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: throw IOException("측정 번호가 누락됐어요.")
                val warnings = obj["warnings"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                return BraceMeasurement(session, values, warnings,
                    obj["report_jpg_base64"]?.jsonPrimitive?.contentOrNull, obj.toString())
            } catch (e: IOException) { throw e }
            catch (_: Exception) { throw IOException("측정 서버의 응답 형식이 올바르지 않아요.") }
        }
    }
}
