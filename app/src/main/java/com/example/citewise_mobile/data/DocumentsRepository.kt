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

    /** Public signed URL (for DownloadManager). */
    suspend fun getSignedUrl(documentId: String): NetResult<String> =
        withContext(Dispatchers.IO) {
            val signed = runCatching {
                api.signedUrl(documentId = documentId, provider = null, disposition = "attachment")
            }.getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

            if (!signed.isSuccessful || signed.body() == null) {
                val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
                return@withContext NetResult.Err(msg, signed.code())
            }
            NetResult.Ok(signed.body()!!.url)
        }

    /** Private save for in-app viewing. */
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

            // If not found, try signed URL then raw GET
            if (streamTry.code() != 404) {
                val msg = streamTry.errorBody()?.string().orEmpty().ifBlank { "HTTP ${streamTry.code()}" }
                return@withContext NetResult.Err(msg, streamTry.code())
            }

            val signed = runCatching {
                api.signedUrl(documentId = documentId, provider = null, disposition = "attachment")
            }.getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

            if (!signed.isSuccessful || signed.body() == null) {
                val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
                return@withContext NetResult.Err(msg, signed.code())
            }

            val url = signed.body()!!.url
            val req = Request.Builder().url(url).get().build()
            val resp = runCatching { rawHttp.newCall(req).execute() }.getOrElse { t ->
                return@withContext NetResult.Err(t.message ?: "Download failed")
            }

            if (!resp.isSuccessful) return@withContext NetResult.Err("HTTP ${resp.code}", resp.code)

            val body = resp.body ?: return@withContext NetResult.Err("Empty body from signed URL")
            return@withContext saveBodyToFile(body, documentId, preferredName)
        }

    private fun saveBodyToFile(
        body: ResponseBody,
        documentId: String,
        preferredName: String?
    ): NetResult<File> = try {
        val fileName = (preferredName?.takeIf { it.isNotBlank() } ?: "$documentId.bin")

        // app-specific external Downloads (visible to your app, not public Downloads app)
        val base = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: ctx.filesDir
        val dir = File(base, "CiteWise").apply { if (!exists()) mkdirs() }

        val target = File(dir, fileName)
        body.byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        NetResult.Ok(target)
    } catch (t: Throwable) {
        NetResult.Err(t.message ?: "Save failed")
    }
}
