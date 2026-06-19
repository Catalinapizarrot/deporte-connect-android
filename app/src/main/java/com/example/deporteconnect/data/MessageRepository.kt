package com.example.deporteconnect.data

import android.content.Context
import android.util.Log
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.ApiErrorResponse
import com.example.deporteconnect.network.MessageResponse
import com.example.deporteconnect.network.SendMessageRequest
import com.google.gson.Gson
import retrofit2.Response

class MessageRepository(context: Context) {

    companion object { private const val TAG = "MessageRepo" }

    private val api = ApiClient.getInstance(context)
    private val gson = Gson()

    suspend fun getMessages(activityId: Long): Resource<List<MessageResponse>> = safeCall {
        api.getMessages(activityId)
    }

    suspend fun sendMessage(activityId: Long, content: String): Resource<MessageResponse> = safeCall {
        api.sendMessage(activityId, SendMessageRequest(content))
    }

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): Resource<T> {
        return try {
            val response = block()
            when {
                response.isSuccessful && response.body() != null ->
                    Resource.Success(response.body()!!)
                response.code() == 401 || response.code() == 403 ->
                    Resource.Error("Tu sesión expiró. Inicia sesión nuevamente.")
                else -> Resource.Error(parseError(response))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ${e::class.java.simpleName}: ${e.message}", e)
            Resource.Error(friendlyMessage(e))
        }
    }

    private fun parseError(response: Response<*>): String {
        return try {
            val body = response.errorBody()?.string()
            if (body.isNullOrBlank()) "Error ${response.code()}"
            else gson.fromJson(body, ApiErrorResponse::class.java).message ?: "Error ${response.code()}"
        } catch (e: Exception) {
            "Error ${response.code()}"
        }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is java.net.ConnectException -> "No se pudo conectar al servidor"
        is java.net.SocketTimeoutException -> "El servidor tardó demasiado"
        is java.net.UnknownHostException -> "Sin conexión a internet"
        else -> e.message ?: "Error desconocido"
    }
}
