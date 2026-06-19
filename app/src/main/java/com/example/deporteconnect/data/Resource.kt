package com.example.deporteconnect.data

/**
 * Wrapper para representar el resultado de una operación de red:
 *  - Success: con los datos
 *  - Error: con un mensaje legible
 *  - Loading: mientras se ejecuta
 *
 * Esto evita usar try/catch en cada Activity.
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
