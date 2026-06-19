package com.example.deporteconnect.network

import android.content.Context
import com.example.deporteconnect.util.SessionManager
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Singleton de Retrofit/ApiService.
 *
 * FIX V11 — EOFException en el feed (/actividades).
 *
 * Diagnóstico final: el backend responde con "Connection: close" y un
 * body chunked ("unknown-length body"). OkHttp, al intentar reutilizar
 * una conexión que el servidor ya marcó para cerrar, lee un stream HTTP
 * cortado → EOFException. Pasa más en el feed porque es la respuesta
 * más larga.
 *
 * Solución de raíz:
 *  1. NO reutilizar conexiones: pool de 0 conexiones inactivas →
 *     cada request abre una conexión nueva y limpia. Como el backend
 *     igual cierra cada conexión, no perdemos nada y evitamos el choque.
 *  2. Pedir "Accept-Encoding: identity" → la respuesta no viene
 *     comprimida, lo que reduce los problemas de chunked mal cerrado.
 *  3. Mantener el reintento ante EOFException como última red de seguridad.
 */
object ApiClient {

    @Volatile private var apiService: ApiService? = null

    fun getInstance(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: createApiService(context).also { apiService = it }
        }
    }

    private fun createApiService(context: Context): ApiService {

        val sessionManager = SessionManager.getInstance(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // Pide la respuesta sin comprimir y sin keep-alive desde el lado cliente.
        val cleanConnectionInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept-Encoding", "identity")
                .header("Connection", "close")
                .build()
            chain.proceed(request)
        }

        // Red de seguridad: reintenta si aún así aparece EOFException.
        val retryOnEofInterceptor = Interceptor { chain ->
            val request = chain.request()
            var attempt = 0
            var lastException: IOException? = null
            var response: Response? = null

            while (attempt < 3 && response == null) {
                try {
                    response = chain.proceed(request)
                } catch (e: EOFException) {
                    lastException = e
                    attempt++
                    Thread.sleep(150L * attempt)
                }
            }
            response ?: throw lastException ?: IOException("Sin respuesta del servidor")
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(retryOnEofInterceptor)
            .addInterceptor(cleanConnectionInterceptor)
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(logging)
            // Pool con 0 conexiones inactivas: nunca reutiliza una conexión vieja.
            // Cada petición usa una conexión nueva y limpia → no choca con el
            // "Connection: close" del backend.
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
