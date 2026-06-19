package com.example.deporteconnect.data

import android.content.Context
import com.example.deporteconnect.network.ApiClient
import com.example.deporteconnect.network.LocationResponse

class LocationRepository(context: Context) {

    private val api = ApiClient.getInstance(context)

    suspend fun getAll(): Resource<List<LocationResponse>> {
        return try {
            val response = api.getLocations()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al cargar ubicaciones (${response.code()})")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error desconocido")
        }
    }
}
