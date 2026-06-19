package com.example.deporteconnect.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestiona la sesión del usuario:
 *   - Guarda el JWT después del login
 *   - Lo recupera para usarlo en peticiones autenticadas
 *   - Lo borra al cerrar sesión
 *
 * Usa SharedPreferences (almacenamiento local del dispositivo).
 */
class SessionManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "deporte_connect_session"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_COMPLETE = "profile_complete"

        // Singleton
        @Volatile private var INSTANCE: SessionManager? = null
        fun getInstance(context: Context): SessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(
        token: String,
        userId: Long,
        email: String,
        fullName: String?,
        profileComplete: Boolean
    ) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putLong(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, fullName)
            putBoolean(KEY_PROFILE_COMPLETE, profileComplete)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, 0L)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun isProfileComplete(): Boolean = prefs.getBoolean(KEY_PROFILE_COMPLETE, false)

    fun isLoggedIn(): Boolean = getToken() != null

    fun setProfileComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_PROFILE_COMPLETE, complete).apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
