package com.example.citewise_mobile.api

import com.example.citewise_mobile.net.FirebaseAuthInterceptor
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Central Retrofit provider for all backend APIs.
 * - Adds Firebase bearer token via FirebaseAuthInterceptor
 * - BODY logging for debug (adjust in production)
 * - Lenient Gson with FlexTime adapter support
 */
object RetrofitInstance {

    /** Use your deployed API base URL (must end with '/') */
    private const val BASE_URL = "https://citewise-api.onrender.com/"

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            // BODY for development; consider BASIC or HEADERS in production
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /** Debug-only probe to confirm Authorization header presence */
    private val authHeaderProbe = Interceptor { chain ->
        val req = chain.request()
        val hasAuth = req.header("Authorization")?.startsWith("Bearer ") == true
        android.util.Log.d("AUTH", "Has Authorization header? $hasAuth")
        chain.proceed(req)
    }

    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            // If your project defines FlexTime & FlexTimeAdapter, keep this.
            // (No-op if you later remove those types)
            .registerTypeAdapter(FlexTime::class.java, FlexTimeAdapter())
            .create()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(FirebaseAuthInterceptor()) // adds Bearer token
            .addInterceptor(authHeaderProbe)           // optional debug
            .addInterceptor(logging)                   // after auth so it logs signed calls
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // Expose typed APIs (interfaces must exist in your project)
    val api: ServiceReviewsApi by lazy { retrofit.create(ServiceReviewsApi::class.java) }
    val documentsApi: DocumentsApi by lazy { retrofit.create(DocumentsApi::class.java) }
    val messagesApi: MessagesApi by lazy { retrofit.create(MessagesApi::class.java) }
    val resourcesApi: ResourcesApi by lazy { retrofit.create(ResourcesApi::class.java) }

}
