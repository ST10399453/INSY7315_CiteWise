// app/src/main/java/com/example/citewise_mobile/data/DocumentsRepository.kt
package com.example.citewise_mobile.data

import android.content.Context
import com.example.citewise_mobile.api.DocumentsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

class DocumentsRepository(
    private val api: DocumentsApi,
    private val ctx: Context
) {
    private val rawHttp by lazy { OkHttpClient() }

    suspend fun downloadToDisk(documentId: String, preferredName: String?): NetResult<File> =
        withContext(Dispatchers.IO) {
            // 1) Try direct streaming via proxy (/documents/{id}/file)
            val streamTry: Response<ResponseBody> = runCatching {
                api.streamFile(documentId = documentId, provider = null, disposition = "attachment")
            }.getOrElse { t ->
                return@withContext NetResult.Err(t.message ?: "Stream failed")
            }

            if (streamTry.isSuccessful) {
                return@withContext saveBodyToFile(streamTry.body()!!, documentId, preferredName)
            }

            // If not found, 404 on Render means route missing — fall back to signed URL
            if (streamTry.code() != 404) {
                val msg = streamTry.errorBody()?.string().orEmpty().ifBlank { "HTTP ${streamTry.code()}" }
                return@withContext NetResult.Err(msg, streamTry.code())
            }

            // 2) Signed URL fallback (/documents/{id}/download)
            val signed = runCatching { api.signedUrl(documentId = documentId, provider = null, disposition = "attachment") }
                .getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

            if (!signed.isSuccessful || signed.body() == null) {
                val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
                return@withContext NetResult.Err(msg, signed.code())
            }

            val url = signed.body()!!.url
            val req = Request.Builder().url(url).get().build()
            val resp = runCatching { rawHttp.newCall(req).execute() }.getOrElse { t ->
                return@withContext NetResult.Err(t.message ?: "Download failed")
            }

            if (!resp.isSuccessful) {
                return@withContext NetResult.Err("HTTP ${resp.code}", resp.code)
            }

            val body = resp.body ?: return@withContext NetResult.Err("Empty body from signed URL")
            return@withContext saveBodyToFile(body, documentId, preferredName)
        }

    private fun saveBodyToFile(body: ResponseBody, documentId: String, preferredName: String?): NetResult<File> {
        return try {
            val fileName = (preferredName?.takeIf { it.isNotBlank() } ?: "$documentId.bin")
            val dir = File(ctx.filesDir, "docs").apply { if (!exists()) mkdirs() }
            val target = File(dir, fileName)
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            NetResult.Ok(target)
        } catch (t: Throwable) {
            NetResult.Err(t.message ?: "Save failed")
        }
    }
}
