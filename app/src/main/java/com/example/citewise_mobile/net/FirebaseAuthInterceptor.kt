package com.example.citewise_mobile.net

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Adds `Authorization: Bearer <Firebase ID token>` to every request.
 * Tries cached token first; if missing/expired, force-refreshes once.
 */
class FirebaseAuthInterceptor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val timeoutSeconds: Long = 5
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val user = auth.currentUser

        var token: String? = null
        if (user != null) {
            // 1) Try cached token (fast path)
            token = runCatching {
                Tasks.await(user.getIdToken(false), timeoutSeconds, TimeUnit.SECONDS)?.token
            }.getOrNull()

            // 2) If missing/expired, force-refresh once
            if (token.isNullOrBlank()) {
                token = runCatching {
                    Tasks.await(user.getIdToken(true), timeoutSeconds, TimeUnit.SECONDS)?.token
                }.getOrNull()
            }
        }

        val req = original.newBuilder()
            .header("Accept", "application/json")
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(req)
    }
}
