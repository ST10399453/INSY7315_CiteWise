package com.example.citewise_mobile.data

import com.example.citewise_mobile.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

sealed class NetResult<out T> {
    data class Ok<T>(val data: T): NetResult<T>()
    data class Err(val message: String, val code: Int? = null): NetResult<Nothing>()
}

class ServiceReviewsRepository(
    private val api: ServiceReviewsApi
) {
    suspend fun createRequestMultipart(
        file: File,
        mime: String,
        documentName: String,
        customName: String?,
        serviceType: String,
        description: String,
        priority: String,
        deadlineIso: String?
    ): NetResult<ServiceRequestDto> = safe {
        val mediaType = mime.toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = documentName,
            body = file.asRequestBody(mediaType)
        )
        api.createRequestMultipart(
            file = filePart,
            documentName = documentName.toRb(),
            customName = customName?.toRb(),
            serviceType = serviceType.toRb(),
            description = description.toRb(),
            priority = priority.toRb(),
            deadline = deadlineIso?.toRb()
        )
    }

    suspend fun listMyRequests(
        status: String? = null,
        userId: String?
    ): NetResult<List<ServiceRequestDto>> = safe {
        if (userId.isNullOrBlank()) return@safe Response.success(emptyList())
        api.listRequests(status = status, userId = userId, consultantId = null)
    }

    suspend fun listGeneralRequests(
        consultantId: String? = null,
        status: String? = null,
        userId: String? = null
    ): NetResult<List<ServiceRequestDto>> = safe {
        api.listRequests(consultantId = consultantId, status = status, userId = userId)
    }
//Unused functions related to API
//    suspend fun listPendingAssignments(): NetResult<List<ServiceRequestDto>> = safe {
//        api.listPendingAssignments()
//    }
//
//    suspend fun listUnassignedConsultants(): NetResult<List<ConsultantDto>> =
//        when (val resp = safe { api.listUnassignedConsultants() }) {
//            is NetResult.Ok -> NetResult.Ok(resp.data.items)
//            is NetResult.Err -> resp
//        }

    /** Upload annotated file (feedback). Optionally bump status (e.g. "review_submitted"). */
    suspend fun uploadAnnotatedFile(
        requestId: String,
        file: File,
        mime: String = "application/pdf",
        newStatus: String? = null
    ): NetResult<ServiceRequestDto> = safe {
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody(mime.toMediaTypeOrNull())
        )
        val statusRb: RequestBody? = newStatus?.toRb()
        api.uploadAnnotated(
            id = requestId,
            file = filePart,
            status = statusRb
        )
    }

    // ---- helpers ----
    private fun String.toRb(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())

    private suspend fun <T> safe(block: suspend () -> Response<T>): NetResult<T> =
        withContext(Dispatchers.IO) {
            try {
                val resp = block()
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body != null) NetResult.Ok(body)
                    else NetResult.Err("Empty response body", resp.code())
                } else {
                    val err = resp.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    NetResult.Err(err ?: "HTTP ${resp.code()}", resp.code())
                }
            } catch (e: Exception) {
                NetResult.Err(e.message ?: "Network error")
            }
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

