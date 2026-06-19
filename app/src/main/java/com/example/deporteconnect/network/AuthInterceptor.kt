package com.example.deporteconnect.network

import com.example.deporteconnect.util.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor de OkHttp que agrega automáticamente el header
 * "Authorization: Bearer <token>" a cada petición.
 *
 * Si el usuario no tiene sesión, deja pasar la petición sin token
 * (útil para endpoints públicos como /auth/login).
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.getToken()

        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
            addHeader("Content-Type", "application/json")
            addHeader("Accept", "application/json")
        }.build()

        return chain.proceed(request)
    }
}
