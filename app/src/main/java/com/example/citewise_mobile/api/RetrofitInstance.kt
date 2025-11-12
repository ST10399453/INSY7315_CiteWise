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

    private const val BASE_URL = "https://citewise-api.onrender.com/"

    private val logging by lazy {
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    }

    private val authHeaderProbe = Interceptor { chain ->
        val req = chain.request()
        val hasAuth = req.header("Authorization")?.startsWith("Bearer ") == true
        android.util.Log.d("AUTH", "Has Authorization header? $hasAuth")
        chain.proceed(req)
    }

    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            .registerTypeAdapter(FlexTime::class.java, FlexTimeAdapter())
            .create()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(FirebaseAuthInterceptor())
            .addInterceptor(authHeaderProbe)
            .addInterceptor(logging)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // APIs
    val api: ServiceReviewsApi by lazy { retrofit.create(ServiceReviewsApi::class.java) }
    val documentsApi: DocumentsApi by lazy { retrofit.create(DocumentsApi::class.java) }
    val messagesApi: MessagesApi by lazy { retrofit.create(MessagesApi::class.java) }
    val resourcesApi: ResourcesApi by lazy { retrofit.create(ResourcesApi::class.java) }
}
