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

    /** Get a public/authorized signed URL for a document download. */
    suspend fun getSignedUrl(
        documentId: String,
        auth: String? = null
    ): NetResult<String> = withContext(Dispatchers.IO) {
        val signed = runCatching {
            api.signedUrl(
                documentId = documentId,
                provider = null,
                disposition = "attachment",
                auth = auth
            )
        }.getOrElse { t ->
            return@withContext NetResult.Err(t.message ?: "Signed URL failed")
        }

        if (!signed.isSuccessful || signed.body() == null) {
            val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
            return@withContext NetResult.Err(msg, signed.code())
        }
        NetResult.Ok(signed.body()!!.url)
    }

    /**
     * Download a document (try direct streaming first, then always fall back to signed URL).
     */
    suspend fun downloadToDisk(
        documentId: String,
        preferredName: String?,
        auth: String? = null
    ): NetResult<File> = withContext(Dispatchers.IO) {
        // 1) Try direct streaming via proxy (/documents/{id}/file)
        val streamTry: Response<ResponseBody> = runCatching {
            api.streamFile(
                documentId = documentId,
                provider = null,
                disposition = "attachment",
                auth = auth
            )
        }.getOrElse { t ->
            // Streaming failed at HTTP/IO level – we'll fall back to signed URL
            return@withContext NetResult.Err(t.message ?: "Stream failed")
        }

        if (streamTry.isSuccessful && streamTry.body() != null) {
            return@withContext saveBodyToFile(streamTry.body()!!, documentId, preferredName)
        }

        // 2) Get signed URL
        val signed = runCatching {
            api.signedUrl(
                documentId = documentId,
                provider = null,
                disposition = "attachment",
                auth = auth
            )
        }.getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

        if (!signed.isSuccessful || signed.body() == null) {
            val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
            return@withContext NetResult.Err(msg, signed.code())
        }

        val url = signed.body()!!.url
        val req = Request.Builder().url(url).get().build()
        val resp = runCatching { rawHttp.newCall(req).execute() }
            .getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Download failed") }

        resp.use { r ->
            if (!r.isSuccessful) return@withContext NetResult.Err("HTTP ${r.code}", r.code)
            val body = r.body ?: return@withContext NetResult.Err("Empty body from signed URL")
            return@withContext saveBodyToFile(body, documentId, preferredName)
        }
    }

    /** Download a file using a fully qualified signed URL (direct download). */
    suspend fun downloadFromUrlToDisk(
        url: String,
        preferredName: String
    ): NetResult<File> = withContext(Dispatchers.IO) {
        val safeName = preferredName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "download.bin" }

        val req = Request.Builder().url(url).get().build()
        val resp = runCatching { rawHttp.newCall(req).execute() }
            .getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Direct download failed") }

        resp.use { r ->
            if (!r.isSuccessful) return@withContext NetResult.Err("HTTP ${r.code}", r.code)
            val body = r.body ?: return@withContext NetResult.Err("Empty body")

            return@withContext saveBodyToFile(body, safeName, safeName)
        }
    }


    // -------- Fallback via /download + raw GET --------

    private suspend fun trySignedUrlDownload(
        documentId: String,
        preferredName: String?,
        auth: String?,
        hintError: String?
    ): NetResult<File> = withContext(Dispatchers.IO) {
        val signed = runCatching {
            api.signedUrl(
                documentId = documentId,
                provider = null,
                disposition = "attachment",
                auth = auth
            )
        }.getOrElse { t ->
            // Both stream + signed URL failed
            val combined = buildString {
                append("Signed URL failed")
                if (!hintError.isNullOrBlank()) {
                    append(" (stream error: ").append(hintError).append(")")
                }
                append(": ").append(t.message ?: "")
            }
            return@withContext NetResult.Err(combined.ifBlank { "Signed URL failed" })
        }

        if (!signed.isSuccessful || signed.body() == null) {
            val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
            val combined = if (!hintError.isNullOrBlank()) {
                "$msg (stream error: $hintError)"
            } else msg
            return@withContext NetResult.Err(combined, signed.code())
        }

        val url = signed.body()!!.url
        val req = Request.Builder().url(url).get().build()
        val resp = runCatching { rawHttp.newCall(req).execute() }
            .getOrElse { t ->
                val combined = if (!hintError.isNullOrBlank()) {
                    "Download failed (stream error: $hintError): ${t.message}"
                } else {
                    "Download failed: ${t.message}"
                }
                return@withContext NetResult.Err(combined)
            }

        resp.use { r ->
            if (!r.isSuccessful) {
                val combined = if (!hintError.isNullOrBlank()) {
                    "HTTP ${r.code} (stream error: $hintError)"
                } else {
                    "HTTP ${r.code}"
                }
                return@withContext NetResult.Err(combined, r.code)
            }
            val body = r.body ?: return@withContext NetResult.Err("Empty body from signed URL")
            return@withContext saveBodyToFile(body, documentId, preferredName)
        }
    }

    // -------- Internal helpers --------

    /** Save ResponseBody to disk. Works for both docId-based and URL-based downloads. */
    private fun saveBodyToFile(
        body: ResponseBody,
        fileName: String,
        preferredName: String?
    ): NetResult<File> {
        return try {
            val safeName = preferredName
                ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
                ?.ifBlank { fileName }
                ?: fileName

            val base = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: ctx.filesDir
            val dir = File(base, "CiteWise").apply { if (!exists()) mkdirs() }

            val target = File(dir, safeName)
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            NetResult.Ok(target)
        } catch (t: Throwable) {
            NetResult.Err(t.message ?: "Save failed")
        }
    }

}
