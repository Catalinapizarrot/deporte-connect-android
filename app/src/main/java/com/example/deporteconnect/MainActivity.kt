package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity: punto de entrada de la app.
 * Redirige al Login o al Dashboard según si hay sesión activa.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Verificar sesión guardada (descomenta cuando tengas el Dashboard) ──
        // val prefs = getSharedPreferences("DeporteConnect", MODE_PRIVATE)
        // val token = prefs.getString("auth_token", null)
        // val destination = if (token != null) DashboardActivity::class.java
        //                   else LoginActivity::class.java
        // startActivity(Intent(this, destination))

        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
