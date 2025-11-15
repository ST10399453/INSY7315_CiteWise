package com.example.citewise_mobile.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ServiceReviewsApi {

    @GET("/")
    suspend fun health(): Response<String>

    // 1) Create — POST /requests (multipart)
    @Multipart
    @POST("/requests")
    suspend fun createRequestMultipart(
        @Part file: MultipartBody.Part,
        @Part("documentName") documentName: RequestBody,
        @Part("customName") customName: RequestBody?,
        @Part("serviceType") serviceType: RequestBody,
        @Part("description") description: RequestBody,
        @Part("priority") priority: RequestBody,
        @Part("deadline") deadline: RequestBody?
    ): Response<ServiceRequestDto>

    // 2) List
    @GET("/requests")
    suspend fun listRequests(
        @Query("status") status: String? = null,
        @Query("userId") userId: String? = null,
        @Query("consultantId") consultantId: String? = null
    ): Response<List<ServiceRequestDto>>

    //Unused enpoints
//    // 3) Details
//    @GET("/requests/{id}")
//    suspend fun getRequest(@Path("id") id: String): Response<ServiceRequestDto>
//
//    // 4) Assign
//    @POST("/requests/{id}/assign")
//    suspend fun assignRequest(
//        @Path("id") id: String,
//        @Body body: AssignRequestPayload
//    ): Response<ServiceRequestDto>
//
//    // 5) Update assignment
//    @PUT("/requests/{id}/assign")
//    suspend fun updateAssignment(
//        @Path("id") id: String,
//        @Body body: UpdateAssignmentPayload
//    ): Response<ServiceRequestDto>
//
//    // 6) Start review
//    @POST("/requests/{id}/start-review")
//    suspend fun startReview(@Path("id") id: String): Response<ServiceRequestDto>
//
//    // 7) Submit review outcome
//    @POST("/requests/{id}/review")
//    suspend fun submitReview(
//        @Path("id") id: String,
//        @Body body: SubmitReviewPayload
//    ): Response<ServiceRequestDto>
//
//    // 8) Resubmit
//    @POST("/requests/{id}/resubmit")
//    suspend fun resubmit(@Path("id") id: String): Response<ServiceRequestDto>
//
//    // 9) Cancel
//    @POST("/requests/{id}/cancel")
//    suspend fun cancel(@Path("id") id: String): Response<ServiceRequestDto>

    // Utility
    @GET("/requests/pending-assignments")
    suspend fun listPendingAssignments(): Response<List<ServiceRequestDto>>

    @GET("/consultants/unassigned")
    suspend fun listUnassignedConsultants(): Response<UnassignedConsultantsResponse>

    // Annotated upload (feedback)
    @Multipart
    @POST("/requests/{id}/annotated")
    suspend fun uploadAnnotated(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("status") status: RequestBody? = null
    ): Response<ServiceRequestDto>

    @GET("/requests/{id}/feedback/download")
    suspend fun getFeedbackDownloadUrl(@Path("id") id: String): Response<SignedUrlDto>
}

/* ---------- Supporting DTOs ---------- */

data class ConsultantDto(
    val uid: String,
    val name: String,
    val email: String,
    val specialty: String?
)

data class UnassignedConsultantsResponse(
    val total: Int,
    val unassigned: Int,
    val items: List<ConsultantDto>
)

/*
 * REFERENCES
 *
 * Firebase. 2019c. “Firebase Cloud Messaging | Firebase”.
 * https://firebase.google.com/docs/cloud-messaging
 * [accessed 15 September 2025].
 *
 * Firebase. 2019d. “Firebase Realtime Database”.
 * https://firebase.google.com/docs/database
 * [accessed 23 September 2025].
 *
 * GeeksforGeeks. 2023. “How to GET Data from API Using Retrofit Library in Android?”.
 * https://www.geeksforgeeks.org/kotlin/how-to-get-data-from-api-using-retrofit-library-in-android/
 * [accessed 22 September 2025].
 *
 */