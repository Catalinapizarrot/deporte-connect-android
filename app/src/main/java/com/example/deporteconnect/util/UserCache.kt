package com.example.deporteconnect.util

import com.example.deporteconnect.network.ActivityResponse
import com.example.deporteconnect.network.UserResponse

/**
 * Cache en memoria del usuario y sus datos.
 *
 * Se llena UNA vez en la pantalla de carga (LoadingActivity) tras el login,
 * y las demás pantallas lo leen al instante sin volver a pedirlo al backend.
 *
 * Beneficios:
 *  - El perfil (nombre, avatar, deportes) aparece al instante, sin el
 *    "parpadeo" del avatar hasheado de relleno.
 *  - Menos llamadas al backend al navegar → menos presión sobre la conexión.
 *
 * Es un objeto en memoria: se borra cuando la app se cierra (lo cual está
 * bien, porque se vuelve a cargar en el próximo login). NO reemplaza al
 * SessionManager (que guarda token y sesión de forma persistente).
 */
object UserCache {

    @Volatile
    var profile: UserResponse? = null
        private set

    @Volatile
    var myMatches: List<ActivityResponse>? = null
        private set

    // Feed del Dashboard (actividades disponibles)
    @Volatile
    var feed: List<ActivityResponse>? = null
        private set

    fun setProfile(user: UserResponse) {
        profile = user
    }

    fun setMyMatches(matches: List<ActivityResponse>) {
        myMatches = matches
    }

    fun setFeed(activities: List<ActivityResponse>) {
        feed = activities
    }

    /** ¿Ya tenemos el perfil cargado en memoria? */
    fun hasProfile(): Boolean = profile != null

    /** Limpia todo. Llamar al cerrar sesión. */
    fun clear() {
        profile = null
        myMatches = null
        feed = null
    }
}