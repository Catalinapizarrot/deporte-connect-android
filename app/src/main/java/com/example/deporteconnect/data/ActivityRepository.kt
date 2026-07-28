package com.example.deporteconnect.data

import android.content.Context
import android.util.Log
import com.example.deporteconnect.network.ActivityResponse
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.ApiErrorResponse
import com.example.deporteconnect.network.CreateActivityRequest
import com.example.deporteconnect.network.CreateActivityReportRequest
import com.example.deporteconnect.network.CreateOrganizerRatingRequest
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * Repositorio de actividades.
 * Encapsula las llamadas al backend y el manejo de errores.
 */
class ActivityRepository(context: Context) {

    companion object {
        private const val TAG = "ActivityRepo"
    }

    private val api = ApiClient.getInstance(context)
    private val gson = Gson()

    suspend fun getFeed(): Resource<List<ActivityResponse>> = safeCall {
        api.getActivities()
    }

    suspend fun getMyMatches(): Resource<List<ActivityResponse>> = safeCall {
        api.getMyMatches()
    }

    suspend fun getById(id: Long): Resource<ActivityResponse> = safeCall {
        api.getActivityById(id)
    }

    suspend fun create(request: CreateActivityRequest): Resource<ActivityResponse> = safeCall {
        api.createActivity(request)
    }

    suspend fun join(activityId: Long): Resource<ActivityResponse> = safeCall {
        api.joinActivity(activityId)
    }

    suspend fun leave(activityId: Long): Resource<Unit> = safeCall {
        api.leaveActivity(activityId)
    }

    suspend fun cancel(activityId: Long): Resource<Unit> = safeCall {
        api.cancelActivity(activityId)
    }

    suspend fun finishActivity(activityId: Long): Resource<ActivityResponse> = safeCall {
        api.finishActivity(activityId)
    }

    suspend fun report(activityId: Long, reason: String, description: String? = null): Resource<Unit> = safeCall {
        api.reportActivity(activityId, CreateActivityReportRequest(reason, description))
    }

    suspend fun rateOrganizer(activityId: Long, stars: Int): Resource<ActivityResponse> = safeCall {
        api.rateOrganizer(activityId, CreateOrganizerRatingRequest(stars))
    }

    // ─── Helpers ────────────────────────────────────────────

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): Resource<T> {
        return try {
            val response = block()
            when {
                response.isSuccessful && response.body() != null ->
                    Resource.Success(response.body()!!)
                response.isSuccessful && response.code() == 204 ->
                    @Suppress("UNCHECKED_CAST")
                    Resource.Success(Unit as T)
                response.code() == 401 || response.code() == 403 ->
                    Resource.Error("Tu sesión expiró. Inicia sesión nuevamente.")
                else -> Resource.Error(parseError(response))
            }
        } catch (e: CancellationException) {
            // La corrutina fue cancelada (ej. el usuario cambió de pantalla).
            // NO es un error real: hay que relanzarla para que la cancelación
            // se propague correctamente y NO mostrar ningún mensaje al usuario.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ ${e::class.java.simpleName}: ${e.message}", e)
            Resource.Error(friendlyMessage(e))
        }
    }

    private fun parseError(response: Response<*>): String {
        return try {
            val body = response.errorBody()?.string()
            if (body.isNullOrBlank()) "Error ${response.code()}"
            else gson.fromJson(body, ApiErrorResponse::class.java).message
                ?: "Error ${response.code()}"
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
