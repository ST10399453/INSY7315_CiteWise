package com.example.citewise_mobile.data

import android.content.Context
import com.example.citewise_mobile.api.ResourceDto
import com.example.citewise_mobile.api.ResourcesApi
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repository wrapper for Resources API with NetResult safety.
 */
class ResourcesRepository(
    private val api: ResourcesApi,
    private val appContext: Context
) {

    suspend fun createResourceMultipart(
        file: File,
        mime: String,
        name: String,
        faculty: String,
        category: String?,
        description: String?,
        auth: String?
    ): NetResult<ResourceDto> = safeCall {
        val mediaType = (mime.ifBlank { "application/octet-stream" }).toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody(mediaType)
        )
        val mimePart: RequestBody? = mime.ifBlank { null }?.toPlainPart()
        val namePart = name.toPlainPart()
        val facultyPart = faculty.toPlainPart()
        val categoryPart = category?.takeIf { it.isNotBlank() }?.toPlainPart()
        val descPart = description?.takeIf { it.isNotBlank() }?.toPlainPart()

        api.createResourceMultipart(
            file = filePart,
            mime = mimePart,
            name = namePart,
            faculty = facultyPart,
            category = categoryPart,
            description = descPart,
            auth = auth
        )
    }
    //Unused endpoint
//
//    suspend fun listResources(
//        faculty: String? = null,
//        visibility: String? = null,
//        q: String? = null,
//        sort: String? = null,
//        dir: String? = null,
//        auth: String? = null
//    ): NetResult<List<ResourceDto>> = safeCall {
//        api.listResources(faculty, visibility, q, sort, dir, auth)
//    }
//
//    suspend fun getResource(id: String, auth: String? = null): NetResult<ResourceDto> = safeCall {
//        api.getResource(id, auth)
//    }
//
//    suspend fun deleteResource(id: String, auth: String, strict: Boolean = false): NetResult<Unit> =
//        safeCall { api.deleteResource(id, auth, strict) }

    /**
     * Download a resource file to disk using /resources/:id/file
     * and return the File wrapped in NetResult.
     */
    suspend fun downloadResourceToDisk(
        id: String,
        preferredName: String
    ): NetResult<File> = withContext(Dispatchers.IO) {
        try {
            val resp = api.streamResourceFile(id, disposition = "attachment")
            if (!resp.isSuccessful) {
                return@withContext NetResult.Err(
                    message = resp.errorBody()?.string()
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: "HTTP ${resp.code()}",
                    code = resp.code()
                )
            }

            val body = resp.body()
                ?: return@withContext NetResult.Err("Empty body from server", resp.code())

            val safeName = preferredName
                .ifBlank { "resource_$id.bin" }
                .replace(Regex("""[\\/:*?"<>|]"""), "_")

            val outFile = File(appContext.filesDir, safeName)

            body.use { b ->
                FileOutputStream(outFile).use { out ->
                    b.byteStream().copyTo(out)
                }
            }

            NetResult.Ok(outFile)
        } catch (t: Throwable) {
            NetResult.Err(t.message ?: "Download error", null)
        }
    }

    // ---------- helpers ----------

    private fun String.toPlainPart(): RequestBody =
        this.toRequestBody("text/plain".toMediaTypeOrNull())

    private inline fun <reified T> safeCall(block: () -> retrofit2.Response<T>): NetResult<T> =
        try {
            val resp = block()
            if (resp.isSuccessful) {
                NetResult.Ok(resp.body() as T)
            } else {
                NetResult.Err(
                    message = resp.errorBody()?.string()
                        ?.takeUnless { it.isNullOrBlank() }
                        ?: "HTTP ${resp.code()}",
                    code = resp.code()
                )
            }
        } catch (t: Throwable) {
            NetResult.Err(t.message ?: "Network error", null)
        }
}

/*
 * REFERENCES
 *
 * Ahamad, Musthaq. 2018. “Using Intents and Extras to Pass Data between Activities — Android Beginner’s Guide”.
 * https://medium.com/@haxzie/using-intents-and-extras-to-pass-data-between-activities-android-beginners-guide-565239407ba0
 * [accessed 28 August 2025].
 *
 * Ananth.k. 2023. “Kotlin — SerializedName Annotation”.
 * https://medium.com/@ananthkvn2016/kotlin-serializedname-annotation-2ad375f83371
 * [accessed 19 September 2025].
 *
 * Android Developer. 2024. “Grant Partial Access to Photos and Videos”.
 * https://developer.android.com/about/versions/14/changes/partial-photo-video-access
 * [accessed 10 September 2025].
 *
 * Android Developer. 2025. “Request Location Permissions | Sensors and Location”.
 * https://developer.android.com/develop/sensors-and-location/location/permissions
 * [accessed 16 August 2025].
 *
 * Android Developers. 2025. “Create Dynamic Lists with RecyclerView”.
 * https://developer.android.com/develop/ui/views/layout/recyclerview
 * [accessed 18 September 2025].
 *
 * Android Knowledge. 2023a. “Bottom Navigation Bar in Android Studio Using Java | Explanation”.
 * https://www.youtube.com/watch?v=0x5kmLY16qE
 * [accessed 20 September 2023].
 *
 * Android Knowledge. 2023b. “CRUD Using Firebase Realtime Database in Android Studio Using Kotlin | Create, Read, Update, Delete”.
 * https://www.youtube.com/watch?v=oGyQMBKPuNY
 * [accessed 21 September 2025].
 *
 * Anil Kr Mourya. 2024. “How to Convert Base64 String to Bitmap and Bitmap to Base64 String”.
 * https://mrappbuilder.medium.com/how-to-convert-base64-string-to-bitmap-and-bitmap-to-base64-string-7a30947b0494
 * [accessed 30 September 2025].
 *
 * Firebase. 2019a. “Cloud Firestore | Firebase”.
 * https://firebase.google.com/docs/firestore
 * [accessed 23 September 2025].
 *
 * Firebase. 2019b. “Firebase Authentication | Firebase”.
 * https://firebase.google.com/docs/auth
 * [accessed 24 September 2025].
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2017. “How to Use Glide Image Loader Library in Android Apps?”
 * https://www.geeksforgeeks.org/android/image-loading-caching-library-android-set-2/
 * [accessed 30 September 2025].
 *
 * GeeksforGeeks. 2020. “SimpleAdapter in Android with Example”.
 * https://www.geeksforgeeks.org/android/simpleadapter-in-android-with-example/
 * [accessed 19 August 2025].
 *
 * GeeksforGeeks. 2021. “State ProgressBar in Android”.
 * https://www.geeksforgeeks.org/android/state-progressbar-in-android/
 * [accessed 22 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 * Nainal. 2019. “Add Multiple SHA for Same OAuth for Google SignIn Android”.
 * https://stackoverflow.com/questions/55142027/add-multiple-sha-for-same-oauth-for-google-signin-android
 * [accessed 11 August 2025].
 */

