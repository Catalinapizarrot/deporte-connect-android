package com.example.deporteconnect.data

import android.content.Context
import android.util.Log
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.SportResponse
import com.example.deporteconnect.util.SessionManager

class SportRepository(context: Context) {

    companion object {
        private const val TAG = "SportRepo"
    }

    private val api = ApiClient.getInstance(context)
    private val sessionManager = SessionManager.getInstance(context)

    suspend fun getAllSports(): Resource<List<SportResponse>> {
        return try {
            Log.d(TAG, "→ GET /deportes")
            val response = api.getSports()

            Log.d(TAG, "← código HTTP: ${response.code()}")

            when {
                response.isSuccessful && response.body() != null -> {
                    val sports = response.body()!!
                    Log.d(TAG, "✓ ${sports.size} deportes")
                    Resource.Success(sports)
                }
                response.code() == 401 || response.code() == 403 -> {
                    // Token inválido / sesión expirada / usuario no existe
                    Log.w(TAG, "⚠ Sesión inválida (${response.code()}). Limpiando token.")
                    sessionManager.clearSession()
                    Resource.Error("Tu sesión expiró. Cierra y vuelve a abrir la app para iniciar sesión.")
                }
                else -> {
                    val errorBody = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    Log.e(TAG, "❌ Error HTTP ${response.code()}: $errorBody")
                    Resource.Error("Error del servidor (${response.code()})")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "❌ ConnectException: ${e.message}")
            Resource.Error("No se pudo conectar al servidor. ¿Está el backend corriendo?")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "❌ Sin internet")
            Resource.Error("Sin conexión a internet")
        } catch (e: java.io.EOFException) {
            // Resto del cuerpo vacío después de un 401/403 sin body
            Log.w(TAG, "⚠ EOF (probablemente sesión expirada)")
            sessionManager.clearSession()
            Resource.Error("Tu sesión expiró. Cierra y vuelve a abrir la app.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ${e::class.java.simpleName}: ${e.message}", e)
            Resource.Error("${e::class.java.simpleName}: ${e.message ?: "sin detalle"}")
        }
    }
}

