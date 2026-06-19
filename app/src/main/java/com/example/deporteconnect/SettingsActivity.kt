package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.network.UserResponse
import com.example.deporteconnect.util.AvatarHelper
import com.example.deporteconnect.util.SessionManager
import com.example.deporteconnect.util.UserCache
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var userRepository: UserRepository

    private var currentEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        sessionManager = SessionManager.getInstance(this)
        userRepository = UserRepository(this)
        setupListeners()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        // Pintar al instante lo que ya esta en memoria (sin parpadeo)
        UserCache.profile?.let { renderHeader(it) }
        loadProfileHeader()
    }

    private fun loadProfileHeader() {
        lifecycleScope.launch {
            when (val result = userRepository.getMyProfile()) {
                is Resource.Success -> {
                    UserCache.setProfile(result.data)
                    renderHeader(result.data)
                }
                is Resource.Error -> { /* silencioso */ }
                else -> {}
            }
        }
    }

    private fun renderHeader(user: UserResponse) {
        currentEmail = user.email
        findViewById<TextView>(R.id.tvSettingsName).text = user.fullName ?: "Usuario"
        findViewById<TextView>(R.id.tvSettingsMemberSince).text = formatMemberSince(user.createdAt)

        AvatarHelper.applyAvatar(
            avatarUrl = user.avatarUrl,
            fullName = user.fullName,
            ivImage = findViewById(R.id.settingsAvatarPhoto),
            tvInitials = findViewById(R.id.settingsAvatarInitials),
            bgView = findViewById(R.id.settingsAvatarBg)
        )
    }

    private fun formatMemberSince(iso: String?): String {
        if (iso.isNullOrBlank()) return "Miembro de Deporte Connect"
        return try {
            val dt = LocalDateTime.parse(iso.substringBefore('.').removeSuffix("Z"))
            val mes = dt.format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es")))
            "Miembro desde ${mes.replaceFirstChar { it.uppercase() }} ${dt.year}"
        } catch (e: Exception) {
            "Miembro de Deporte Connect"
        }
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        // ─── Cuenta ───
        findViewById<LinearLayout>(R.id.rowEditProfile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowEditSports).setOnClickListener {
            startActivity(Intent(this, EditSportsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowChangePassword).setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            currentEmail?.let { intent.putExtra("PREFILL_EMAIL", it) }
            startActivity(intent)
        }

        // ─── Ajustes de la App ───
        findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
            Toast.makeText(this, "Privacidad (próximamente)", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<SwitchMaterial>(R.id.switchNotifications)
            .setOnCheckedChangeListener { _, isChecked ->
                val msg = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            confirmLogout()
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Acerca de Deporte Connect")
            .setMessage(
                "Deporte Connect\n\n" +
                        "Versión 1.0\n\n" +
                        "App para encontrar y organizar actividades deportivas cerca de ti.\n\n" +
                        "Proyecto APT © 2026"
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Sí, cerrar sesión") { _, _ -> performLogout() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun performLogout() {
        sessionManager.clearSession()
        UserCache.clear()   // limpia el cache para que el proximo usuario no vea datos del anterior
        Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_matches -> {
                    startActivity(Intent(this, MyMatchesActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_messages -> {
                    startActivity(Intent(this, MessagesActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}