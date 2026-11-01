package com.refit.app.data.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MeasurementPhoto(val filename: String, val mimeType: String, val open: () -> InputStream)

class BraceMeasurementClient(private val allowHttp: Boolean = false) {
    suspend fun measure(serverUrl: String, top: MeasurementPhoto, side: MeasurementPhoto): BraceMeasurement = withContext(Dispatchers.IO) {
        val base = URI(serverUrl.trim().trimEnd('/'))
        require(base.host != null && base.rawUserInfo == null && base.query == null && base.fragment == null &&
            (base.scheme == "https" || allowHttp && base.scheme == "http")) {
            "측정 서버 주소를 확인해 주세요. HTTPS 주소가 필요해요."
        }
        val url = URI(base.toString() + "/measure_brace").toURL()
        val boundary = "ReFit-${UUID.randomUUID()}"
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.buffered().use { out ->
                fun line(value: String) { out.write((value + "\r\n").toByteArray(Charsets.UTF_8)) }
                fun field(name: String, value: String) {
                    line("--$boundary"); line("Content-Disposition: form-data; name=\"$name\"")
                    line(""); line(value)
                }
                for ((name, photo) in listOf("top_image" to top, "side_image" to side)) {
                    require(photo.mimeType in listOf("image/jpeg", "image/png")) { "JPG 또는 PNG 원본 사진을 선택해 주세요." }
                    line("--$boundary")
                    // Controlled filename: never include provider/user supplied paths in headers.
                    line("Content-Disposition: form-data; name=\"$name\"; filename=\"$name.${if (photo.mimeType == "image/png") "png" else "jpg"}\"")
                    line("Content-Type: ${photo.mimeType}"); line("")
                    photo.open().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > 20 * 1024 * 1024) throw IOException("사진 한 장은 20MB 이하여야 해요.")
                            out.write(buffer, 0, n)
                        }
                        if (total == 0) throw IOException("사진 파일이 비어 있어요.")
                    }
                    line("")
                }
                field("session_id", "B${UUID.randomUUID().toString().replace("-", "")}")
                field("return_report", "true")
                line("--$boundary--")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("측정 서버에 연결하지 못했어요 (HTTP $status). 서버 주소와 실행 상태를 확인해 주세요.")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE) throw IOException("측정 결과가 너무 커서 열 수 없어요.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > MAX_RESPONSE) throw IOException("측정 결과가 너무 커서 열 수 없어요.")
            ensureActive()
            BraceMeasurement.parse(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    companion object { private const val MAX_RESPONSE = 12 * 1024 * 1024 }
}
