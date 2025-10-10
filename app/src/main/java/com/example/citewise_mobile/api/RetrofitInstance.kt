package com.example.citewise_mobile.api

import com.example.citewise_mobile.net.FirebaseAuthInterceptor
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    // Use your deployed API
    private const val BASE_URL = "https://citewise-api.onrender.com/"

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            // BODY while debugging; switch to BASIC/HEADERS for production
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // Debug-only probe to confirm the Authorization header exists
    private val authHeaderProbe = Interceptor { chain ->
        val hasAuth = chain.request().header("Authorization")?.startsWith("Bearer ") == true
        android.util.Log.d("AUTH", "Has Authorization header? $hasAuth")
        chain.proceed(chain.request())
    }

    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(FirebaseAuthInterceptor()) // << adds Bearer token
            .addInterceptor(authHeaderProbe)           // << optional debug
            .addInterceptor(logging)                   // keep after auth so it logs post-auth request
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val api: ServiceReviewsApi by lazy { retrofit.create(ServiceReviewsApi::class.java) }
}
