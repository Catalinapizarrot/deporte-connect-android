package com.example.deporteconnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.network.ActivityResponse
import com.example.deporteconnect.util.UserCache
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lista de chats — uno por cada actividad en la que participo.
 * Usa GET /actividades/mis-partidos.
 */
class MessagesActivity : AppCompatActivity() {

    private lateinit var activityRepository: ActivityRepository

    /** Mapeo iconKey → drawable + color del avatar */
    private val iconMap = mapOf(
        "ic_soccer"     to Pair(R.drawable.ic_soccer, "#22C55E"),
        "ic_padel"      to Pair(R.drawable.ic_padel, "#7C3AED"),
        "ic_basketball" to Pair(R.drawable.ic_basketball, "#2563EB"),
        "ic_tennis"     to Pair(R.drawable.ic_tennis, "#10B981"),
        "ic_volleyball" to Pair(R.drawable.ic_volleyball, "#F59E0B"),
        "ic_flash"      to Pair(R.drawable.ic_flash, "#EF4444")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        activityRepository = ActivityRepository(this)

        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnExplore)?.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            overridePendingTransition(0, 0); finish()
        }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        // Pintar al instante los chats que ya estan en memoria (sin espera),
        // y luego refrescar del backend en segundo plano.
        UserCache.myMatches?.let { renderChats(it) }
        loadChats()
    }

    private fun loadChats() {
        lifecycleScope.launch {
            when (val result = activityRepository.getMyMatches()) {
                is Resource.Success -> {
                    UserCache.setMyMatches(result.data)
                    renderChats(result.data)
                }
                is Resource.Error -> {
                    // Solo mostrar error si no teniamos nada en cache
                    if (UserCache.myMatches == null) showEmpty("Error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun renderChats(activities: List<ActivityResponse>) {
        val container = findViewById<LinearLayout>(R.id.containerChats)
        container.removeAllViews()

        if (activities.isEmpty()) {
            showEmpty("Aún no participas en ninguna actividad.\n¡Únete a una para empezar a chatear!")
            return
        }

        activities.forEach { activity ->
            val item = LayoutInflater.from(this)
                .inflate(R.layout.item_chat_preview, container, false)

            // Título: deporte + ubicación
            item.findViewById<TextView>(R.id.tvChatTitle).text =
                "${activity.sport.name} - ${activity.location.commune ?: activity.location.name}"

            // Subtitle: estado del partido
            item.findViewById<TextView>(R.id.tvChatLastMessage).text =
                getStatusText(activity)

            // Hora: fecha del evento formateada
            item.findViewById<TextView>(R.id.tvChatTime).text = formatEventTime(activity.eventAt)

            // Avatar: ícono del deporte con color de fondo
            val iconInfo = iconMap[activity.sport.iconKey] ?: Pair(R.drawable.ic_soccer, "#22C55E")
            val avatar = item.findViewById<View>(R.id.chatAvatar)
            val avatarIcon = item.findViewById<ImageView>(R.id.chatAvatarIcon)
            avatarIcon.setImageResource(iconInfo.first)

            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(iconInfo.second))
            }
            avatar.background = bg

            // Badge de no leídos: por ahora oculto (no tenemos endpoint de unread count)
            item.findViewById<TextView>(R.id.tvUnreadBadge).visibility = View.GONE

            item.setOnClickListener {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("ACTIVITY_ID", activity.id)
                intent.putExtra("CHAT_TITLE", "${activity.sport.name} - ${activity.location.commune ?: activity.location.name}")
                startActivity(intent)
            }

            container.addView(item)
        }
    }

    private fun getStatusText(activity: ActivityResponse): String {
        return when (activity.status) {
            "OPEN" -> "Próximo partido"
            "FULL" -> "Lleno"
            "CANCELLED" -> "Cancelado"
            "FINISHED" -> "Finalizado"
            else -> "Activo"
        }
    }

    private fun formatEventTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val dt = LocalDateTime.parse(iso)
            val today = LocalDate.now()
            when {
                dt.toLocalDate() == today -> dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                dt.toLocalDate() == today.plusDays(1) -> "Mañana"
                dt.toLocalDate().isBefore(today.plusDays(7)) ->
                    dt.format(DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("es")))
                        .replaceFirstChar { it.uppercase() }
                else -> dt.format(DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es")))
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun showEmpty(message: String) {
        val container = findViewById<LinearLayout>(R.id.containerChats)
        container.removeAllViews()
        val tv = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = android.view.Gravity.CENTER
            setPadding(40, 80, 40, 40)
        }
        container.addView(tv)
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_messages

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
                R.id.nav_messages -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                else -> false
            }
        }
    }
}