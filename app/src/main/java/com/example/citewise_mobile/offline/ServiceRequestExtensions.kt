package com.example.citewise_mobile.offline

import com.example.citewise_mobile.api.ServicePriority
import com.example.citewise_mobile.api.ServiceRequestDto
import com.example.citewise_mobile.api.ServiceType
import com.example.citewise_mobile.api.toFlex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun epochToIsoUTC(epochMillis: Long?): String? =
    epochMillis?.let { isoFmt.format(Date(it)) }

/** Map server → local. We keep server truth for status/documentId/etc. */
fun ServiceRequestDto.toEntityPreservingLocalFallback(localFallback: ServiceRequestEntity?): ServiceRequestEntity {
    val docName     = localFallback?.documentName ?: (documentId ?: "document")
    val svcType     = serviceType?.name ?: (localFallback?.serviceType ?: "OTHER")
    val quotationId = quotationId ?: (localFallback?.quotationId ?: "")
    val desc        = description ?: (localFallback?.description ?: "")
    val prio        = priority?.name ?: (localFallback?.priority ?: "MEDIUM")
    val deadlineIso = epochToIsoUTC(deadline?.epochMillis)
    val custom      = this.customName ?: localFallback?.customName ?: ""

    return (localFallback ?: ServiceRequestEntity(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,
        consultantId = consultantId ?: localFallback?.consultantId,
        documentName = docName,
        serviceType  = svcType,
        quotationId  = quotationId,
        quotationWords   = this.quotationWords ?: localFallback?.quotationWords,
        quotationAmount  = this.quotationAmount ?: localFallback?.quotationAmount,
        quotationCurrency= this.quotationCurrency ?: localFallback?.quotationCurrency,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        filePath     = localFallback?.filePath,
        status       = status ?: "submitted",
        feedback     = feedback ?: localFallback?.feedback,
        feedbackFileName = feedbackFileName ?: localFallback?.feedbackFileName,
        feedbackFileUrl  = feedbackFileUrl  ?: localFallback?.feedbackFileUrl,
        syncState    = SyncState.SYNCED
    )).copy(
        remoteId     = id,
        userId       = userId ?: localFallback?.userId,
        consultantId = consultantId ?: localFallback?.consultantId,
        documentId   = documentId,
        status       = status ?: localFallback?.status ?: "submitted",
        serviceType  = svcType,
        quotationId  = quotationId,
        quotationWords   = this.quotationWords ?: localFallback?.quotationWords,
        quotationAmount  = this.quotationAmount ?: localFallback?.quotationAmount,
        quotationCurrency= this.quotationCurrency ?: localFallback?.quotationCurrency,
        description  = desc,
        priority     = prio,
        deadlineIso  = deadlineIso,
        customName   = custom,
        feedback     = feedback ?: localFallback?.feedback,
        feedbackFileName = feedbackFileName ?: localFallback?.feedbackFileName,
        feedbackFileUrl  = feedbackFileUrl  ?: localFallback?.feedbackFileUrl,
        updatedAt    = System.currentTimeMillis()
    )
}

/** Map local entity → DTO for API communication */
fun ServiceRequestEntity.toServiceRequestDto(): ServiceRequestDto =
    ServiceRequestDto(
        id = remoteId,
        status = status,
        documentId = documentId,
        userId = userId,
        consultantId = consultantId,
        serviceType = runCatching { ServiceType.valueOf(serviceType) }.getOrNull(),
        quotationId = quotationId,

        quotationWords   = quotationWords,
        quotationAmount  = quotationAmount,
        quotationCurrency= quotationCurrency,

        description = description,
        priority = runCatching { ServicePriority.valueOf(priority ?: "LOW") }.getOrNull(),
        deadline = null,
        createdAt = createdAt.toFlex(),
        updatedAt = updatedAt.toFlex(),
        feedback = feedback,
        studentName = null,
        originalFileName = documentName.takeIf { it.isNotEmpty() },
        customName = customName.takeIf { it.isNotEmpty() },
        originalFileUrl = null,
        feedbackFileName = feedbackFileName,
        feedbackFileUrl = feedbackFileUrl
    )

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
