// app/src/main/java/com/example/citewise_mobile/api/ServiceReviewsApi.kt
package com.example.citewise_mobile.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ServiceReviewsApi {

    // Health (plain text)
    @GET("/")
    suspend fun health(): Response<String>

    // 1) Create — POST /requests (multipart: file + all fields)
    @Multipart
    @POST("/requests")
    suspend fun createRequestMultipart(
        @Part file: MultipartBody.Part,
        @Part("documentName") documentName: RequestBody,
        @Part("serviceType") serviceType: RequestBody,
        @Part("description") description: RequestBody,
        @Part("priority") priority: RequestBody,
        @Part("deadline") deadline: RequestBody? // nullable ISO string
    ): Response<ServiceRequestDto>

    // 2) List — GET /requests?status=&userId=&consultantId=
    @GET("/requests")
    suspend fun listRequests(
        @Query("status") status: String? = null,
        @Query("userId") userId: String? = null,
        @Query("consultantId") consultantId: String? = null
    ): Response<List<ServiceRequestDto>>


    // 3) Details — GET /requests/{id}
    @GET("/requests/{id}")
    suspend fun getRequest(
        @Path("id") id: String
    ): Response<ServiceRequestDto>

    // 4) Assign — POST /requests/{id}/assign
    @POST("/requests/{id}/assign")
    suspend fun assignRequest(
        @Path("id") id: String,
        @Body body: AssignRequestPayload
    ): Response<ServiceRequestDto>

    // 5) Update assignment — PUT /requests/{id}/assign
    @PUT("/requests/{id}/assign")
    suspend fun updateAssignment(
        @Path("id") id: String,
        @Body body: UpdateAssignmentPayload
    ): Response<ServiceRequestDto>

    // 6) Start review — POST /requests/{id}/start-review
    @POST("/requests/{id}/start-review")
    suspend fun startReview(
        @Path("id") id: String
    ): Response<ServiceRequestDto>

    // 7) Submit review outcome — POST /requests/{id}/review
    @POST("/requests/{id}/review")
    suspend fun submitReview(
        @Path("id") id: String,
        @Body body: SubmitReviewPayload
    ): Response<ServiceRequestDto>

    // 8) Resubmit — POST /requests/{id}/resubmit
    @POST("/requests/{id}/resubmit")
    suspend fun resubmit(
        @Path("id") id: String
    ): Response<ServiceRequestDto>

    // 9) Cancel — POST /requests/{id}/cancel
    @POST("/requests/{id}/cancel")
    suspend fun cancel(
        @Path("id") id: String
    ): Response<ServiceRequestDto>
}
