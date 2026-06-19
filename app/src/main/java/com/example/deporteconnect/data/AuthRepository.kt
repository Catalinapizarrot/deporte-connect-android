package com.example.deporteconnect.data

import android.content.Context
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.ApiErrorResponse
import com.example.deporteconnect.network.AuthResponse
import com.example.deporteconnect.network.ForgotPasswordRequest
import com.example.deporteconnect.network.ForgotPasswordResponse
import com.example.deporteconnect.network.GenericResponse
import com.example.deporteconnect.network.LoginRequest
import com.example.deporteconnect.network.RegisterRequest
import com.example.deporteconnect.network.ResetPasswordRequest
import com.example.deporteconnect.util.SessionManager
import com.google.gson.Gson
import retrofit2.Response

/**
 * Repository para auth. Encapsula la lógica de:
 *  - llamar al API
 *  - guardar la sesión si funciona
 *  - convertir errores HTTP en mensajes legibles
 */
class AuthRepository(context: Context) {

    private val api = ApiClient.getInstance(context)
    private val sessionManager = SessionManager.getInstance(context)
    private val gson = Gson()

    suspend fun register(email: String, password: String): Resource<AuthResponse> {
        return try {
            val response = api.register(RegisterRequest(email, password))
            handleAuthResponse(response)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    suspend fun login(email: String, password: String): Resource<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            handleAuthResponse(response)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    /**
     * Verifica si un email está registrado en el sistema (para recuperar contraseña).
     * Backend devuelve { ok, exists, message }.
     */
    suspend fun forgotPassword(email: String): Resource<ForgotPasswordResponse> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(parseErrorMessage(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    /**
     * Cambia la contraseña de un usuario existente.
     * Backend recibe { email, newPassword } y devuelve { ok, message }.
     */
    suspend fun resetPassword(email: String, newPassword: String): Resource<GenericResponse> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, newPassword))
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(parseErrorMessage(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    private fun handleAuthResponse(response: Response<AuthResponse>): Resource<AuthResponse> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                sessionManager.saveSession(
                    token = body.token,
                    userId = body.userId,
                    email = body.email,
                    fullName = body.fullName,
                    profileComplete = body.profileComplete
                )
                return Resource.Success(body)
            }
            return Resource.Error("Respuesta vacía del servidor")
        }
        return Resource.Error(parseErrorMessage(response))
    }

    private fun parseErrorMessage(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody.isNullOrBlank()) {
                "Error ${response.code()}"
            } else {
                val parsed = gson.fromJson(errorBody, ApiErrorResponse::class.java)
                parsed.message ?: parsed.error ?: "Error ${response.code()}"
            }
        } catch (e: Exception) {
            "Error inesperado del servidor"
        }
    }

    private fun Exception.toFriendlyMessage(): String = when (this) {
        is java.net.ConnectException ->
            "No se pudo conectar al servidor.\n¿Está el backend corriendo en tu PC?"
        is java.net.SocketTimeoutException ->
            "El servidor tardó demasiado en responder"
        is java.net.UnknownHostException ->
            "Sin conexión a internet"
        else ->
            this.message ?: "Error desconocido"
    }
}
