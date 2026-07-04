package com.example.deporteconnect.data

import android.content.Context
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.ApiErrorResponse
import com.example.deporteconnect.network.UpdateAvatarRequest
import com.example.deporteconnect.network.UpdateProfileRequest
import com.example.deporteconnect.network.UserResponse
import com.example.deporteconnect.util.SessionManager
import com.google.gson.Gson
import retrofit2.Response

class UserRepository(context: Context) {

    private val api = ApiClient.getInstance(context)
    private val sessionManager = SessionManager.getInstance(context)
    private val gson = Gson()

    suspend fun getMyProfile(): Resource<UserResponse> {
        return try {
            val response = api.getMyProfile()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(parseError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Resource<UserResponse> {
        return try {
            val response = api.updateMyProfile(request)
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                sessionManager.saveSession(
                    token = sessionManager.getToken() ?: "",
                    userId = user.id,
                    email = user.email,
                    fullName = user.fullName,
                    profileComplete = user.profileComplete == true
                )
                Resource.Success(user)
            } else {
                Resource.Error(parseError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    /**
     * Sube el avatar al backend.
     * El parámetro puede ser:
     *   - "color:#22C55E" → avatar de color
     *   - base64          → foto comprimida en base64
     */
    suspend fun updateAvatar(avatarUrl: String): Resource<UserResponse> {
        return try {
            val response = api.updateAvatar(UpdateAvatarRequest(avatarUrl))
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error(parseError(response))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    private fun parseError(response: Response<*>): String {
        return when (response.code()) {
            401, 403 -> "Tu sesión expiró. Cierra y abre la app de nuevo."
            404 -> "Recurso no encontrado"
            413 -> "La imagen es demasiado grande. Intenta con otra."
            500 -> "Error del servidor. Intenta más tarde."
            else -> {
                try {
                    val errorBody = response.errorBody()?.string()
                    if (errorBody.isNullOrBlank()) {
                        "Error ${response.code()}"
                    } else {
                        val parsed = gson.fromJson(errorBody, ApiErrorResponse::class.java)
                        parsed.message ?: parsed.error ?: "Error ${response.code()}"
                    }
                } catch (e: Exception) {
                    "Error ${response.code()}"
                }
            }
        }
    }

    private fun Exception.toFriendlyMessage(): String = when (this) {
        is java.net.ConnectException -> "No se pudo conectar al servidor"
        is java.net.SocketTimeoutException -> "El servidor tardó demasiado"
        is java.net.UnknownHostException -> "Sin conexión a internet"
        else -> this.message ?: "Error desconocido"
    }
}
