// app/src/main/java/com/example/citewise_mobile/net/FirebaseAuthInterceptor.kt
package com.example.citewise_mobile.net

import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response

class FirebaseAuthInterceptor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req0 = chain.request()
        val token = runCatching {
            auth.currentUser?.getIdToken(false)?.result?.token
        }.getOrNull()

        val req = req0.newBuilder()
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

        return chain.proceed(req)
    }
}
