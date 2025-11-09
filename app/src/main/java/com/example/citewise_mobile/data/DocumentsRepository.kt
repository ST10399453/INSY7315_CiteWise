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

    /** Public/authorized signed URL for DownloadManager. */
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
        }.getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

        if (!signed.isSuccessful || signed.body() == null) {
            val msg = signed.errorBody()?.string().orEmpty().ifBlank { "HTTP ${signed.code()}" }
            return@withContext NetResult.Err(msg, signed.code())
        }
        NetResult.Ok(signed.body()!!.url)
    }

    /** Private save for in-app viewing. */
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
            return@withContext NetResult.Err(t.message ?: "Stream failed")
        }

        if (streamTry.isSuccessful && streamTry.body() != null) {
            return@withContext saveBodyToFile(streamTry.body()!!, documentId, preferredName)
        }

        // If not found, try signed URL then raw GET
        if (streamTry.code() != 404) {
            val msg = streamTry.errorBody()?.string().orEmpty().ifBlank { "HTTP ${streamTry.code()}" }
            return@withContext NetResult.Err(msg, streamTry.code())
        }

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

    /** Admin only. */
    suspend fun deleteResource(
        resourceId: String,
        auth: String,
        strict: Boolean = false
    ): NetResult<Unit> {
        val resp = runCatching { api.deleteResource(resourceId, auth = auth, strict = strict) }
            .getOrElse { t -> return NetResult.Err(t.message ?: "Delete failed") }

        if (!resp.isSuccessful) {
            val msg = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP ${resp.code()}" }
            return NetResult.Err(msg, resp.code())
        }
        return NetResult.Ok(Unit)
    }

    /** Signed URL for a resource (server may require auth). */
    suspend fun getResourceSignedUrl(
        id: String,
        disposition: String = "inline",
        auth: String? = null
    ): NetResult<String> = withContext(Dispatchers.IO) {
        val resp = runCatching { api.resourceSignedUrl(id, disposition, null, auth) }
            .getOrElse { t -> return@withContext NetResult.Err(t.message ?: "Signed URL failed") }

        if (!resp.isSuccessful || resp.body() == null) {
            val msg = resp.errorBody()?.string().orEmpty().ifBlank { "HTTP ${resp.code()}" }
            return@withContext NetResult.Err(msg, resp.code())
        }
        NetResult.Ok(resp.body()!!.url)
    }

    private fun saveBodyToFile(
        body: ResponseBody,
        documentId: String,
        preferredName: String?
    ): NetResult<File> = try {
        val safeName = preferredName
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
            ?: "$documentId.bin"

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
