package com.example.deporteconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.network.ActivityResponse
import com.example.deporteconnect.util.SessionManager
import com.example.deporteconnect.util.UserCache
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla "Mis Partidos".
 * Lista las actividades en las que el usuario participa (organizador o inscrito).
 *
 * Características:
 *  - La PRIMERA card (la más próxima en fecha) se muestra DESTACADA con badge "ACTIVO"
 *  - Las demás se muestran normales con badge "PRÓXIMO"
 *  - Distingue si soy ORGANIZADOR vs PARTICIPANTE (badge superior)
 *  - Loading mientras carga
 *  - Pull-to-refresh para recargar
 *  - Refresh automático al volver a la pantalla (onResume)
 *  - Empty state si no tengo actividades
 */
class MyMatchesActivity : AppCompatActivity() {

    companion object { private const val TAG = "MyMatches" }

    private lateinit var activityRepository: ActivityRepository
    private lateinit var sessionManager: SessionManager
    private var currentUserId: Long = 0L

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
        setContentView(R.layout.activity_my_matches)

        activityRepository = ActivityRepository(this)
        sessionManager = SessionManager.getInstance(this)
        currentUserId = sessionManager.getUserId()

        setupBottomNav()
        setupTopButtons()
        setupSwipeRefresh()

        // Pintar al instante lo que ya esta en memoria (sin espera)
        UserCache.myMatches?.let { renderMatches(it) }
    }

    override fun onResume() {
        super.onResume()
        // Recarga automática al volver (después de crear/unirse/salirse de actividad)
        loadMyMatches()
    }

    // ─── Setup ───────────────────────────────────────

    private fun setupTopButtons() {
        findViewById<View>(R.id.fabCreateActivity)?.setOnClickListener {
            startActivity(Intent(this, CreateActivityActivity::class.java))
        }

        findViewById<View>(R.id.btnExplore)?.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            overridePendingTransition(0, 0); finish()
        }

        findViewById<View>(R.id.ivSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupSwipeRefresh() {
        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe?.setColorSchemeResources(R.color.dc_green, R.color.dc_blue)
        swipe?.setOnRefreshListener { loadMyMatches() }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = R.id.nav_matches

        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0); finish(); true
                }
                R.id.nav_matches -> true
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

    // ─── Carga de datos ──────────────────────────────

    private fun loadMyMatches() {
        // Solo mostrar el "Cargando..." si NO tenemos nada en cache todavia.
        // Si ya hay datos en pantalla (del cache), refrescamos sin parpadeo.
        if (UserCache.myMatches == null) {
            val loading = findViewById<TextView>(R.id.tvLoadingMatches)
            loading?.visibility = View.VISIBLE
            loading?.text = "Cargando tus partidos..."
        }

        lifecycleScope.launch {
            when (val result = activityRepository.getMyMatches()) {
                is Resource.Success -> {
                    Log.d(TAG, "Mis partidos cargados: ${result.data.size}")
                    UserCache.setMyMatches(result.data)
                    renderMatches(result.data)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Error: ${result.message}")
                    // Solo mostrar error/vaciar si no teniamos nada en cache
                    if (UserCache.myMatches == null) {
                        Toast.makeText(
                            this@MyMatchesActivity,
                            "Error: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        renderMatches(emptyList())
                    }
                }
                else -> {}
            }
            findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
        }
    }

    // ─── Render ──────────────────────────────────────

    private fun renderMatches(matches: List<ActivityResponse>) {
        val container = findViewById<LinearLayout>(R.id.containerMyMatches)
        val loading = findViewById<TextView>(R.id.tvLoadingMatches)
        val subtitle = findViewById<TextView>(R.id.tvAgendaSubtitle)

        container.removeAllViews()
        loading?.visibility = View.GONE

        // Actualizar subtítulo dinámicamente
        subtitle?.text = when (matches.size) {
            0 -> "Aún no tienes partidos. ¡Inscríbete o crea uno!"
            1 -> "Tienes 1 partido programado."
            else -> "Tienes ${matches.size} partidos programados."
        }

        // Empty state
        if (matches.isEmpty()) {
            val empty = TextView(this).apply {
                text = "📭 No tienes actividades aún.\n\nÚsa el botón \"Buscar Partidos\" para unirte a alguno, o el botón \"+\" para crear el tuyo."
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                gravity = android.view.Gravity.CENTER
                setPadding(20, 60, 20, 60)
                setLineSpacing(0f, 1.4f)
            }
            container.addView(empty)
            return
        }

        // Ordenar por fecha ascendente (la más próxima primero)
        val sorted = matches.sortedBy {
            try { LocalDateTime.parse(it.eventAt) } catch (e: Exception) { LocalDateTime.MAX }
        }

        // Renderizar cards: la primera destacada, las demás normales
        sorted.forEachIndexed { index, activity ->
            val card = createMatchCard(activity, isFeatured = (index == 0))
            container.addView(card)
        }
    }

    private fun createMatchCard(activity: ActivityResponse, isFeatured: Boolean): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_my_match_card, null, false)

        // Soy organizador?
        val isOrganizer = activity.organizerId == currentUserId

        // ─── Banda superior (color según estado) ───
        val stripe = card.findViewById<View>(R.id.cardTopStripe)
        val isPast = isEventPast(activity.eventAt)
        when {
            activity.status == "CANCELLED" -> stripe.setBackgroundColor(Color.parseColor("#9CA3AF"))
            isPast -> stripe.setBackgroundColor(Color.parseColor("#9CA3AF"))
            isFeatured -> stripe.setBackgroundColor(Color.parseColor("#22C55E"))
            else -> stripe.setBackgroundColor(Color.parseColor("#2563EB"))
        }

        // ─── Icono del deporte con color ───
        val iconInfo = iconMap[activity.sport.iconKey] ?: Pair(R.drawable.ic_soccer, "#22C55E")
        card.findViewById<ImageView>(R.id.ivSportIcon).apply {
            setImageResource(iconInfo.first)
            setColorFilter(Color.parseColor(iconInfo.second))
        }

        // Background del contenedor del icono - color suave
        val iconContainer = card.findViewById<FrameLayout>(R.id.sportIconContainer)
        val softBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (8 * resources.displayMetrics.density)
            setColor(parseColorWithAlpha(iconInfo.second, 0x22))  // 13% alpha
        }
        iconContainer.background = softBg

        // ─── Título ───
        val rolePrefix = if (isOrganizer) "🎯 " else ""
        card.findViewById<TextView>(R.id.tvCardTitle).text =
            "$rolePrefix${activity.sport.name}"

        // ─── Ubicación ───
        val location = "📍 ${activity.location.commune ?: activity.location.name}"
        card.findViewById<TextView>(R.id.tvCardLocation).text = location

        // ─── Fecha ───
        card.findViewById<TextView>(R.id.tvCardDate).text = formatEventDate(activity.eventAt)

        // ─── Participantes ───
        card.findViewById<TextView>(R.id.tvCardParticipants).text =
            "${activity.currentParticipants} / ${activity.maxParticipants} participantes"

        // ─── Badge ───
        val badge = card.findViewById<TextView>(R.id.tvCardBadge)
        when {
            activity.status == "CANCELLED" -> {
                badge.text = "CANCELADA"
                badge.setTextColor(Color.parseColor("#DC2626"))
                applyBadgeBg(badge, "#FEE2E2")
            }
            isPast -> {
                badge.text = "FINALIZADO"
                badge.setTextColor(Color.parseColor("#6B7280"))
                applyBadgeBg(badge, "#E5E7EB")
            }
            isOrganizer -> {
                badge.text = "ORGANIZADOR"
                badge.setTextColor(Color.parseColor("#7C3AED"))
                applyBadgeBg(badge, "#F3E8FF")
            }
            isFeatured -> {
                badge.text = "PRÓXIMO"
                badge.setTextColor(Color.parseColor("#15803D"))
                applyBadgeBg(badge, "#DCFCE7")
            }
            else -> {
                badge.text = "INSCRITO"
                badge.setTextColor(Color.parseColor("#1D4ED8"))
                applyBadgeBg(badge, "#DBEAFE")
            }
        }

        // ─── Botones VER CHAT / VER DETALLE ───
        card.findViewById<MaterialButton>(R.id.btnCardChat).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("ACTIVITY_ID", activity.id)
            intent.putExtra(
                "CHAT_TITLE",
                "${activity.sport.name} - ${activity.location.commune ?: activity.location.name}"
            )
            startActivity(intent)
        }

        card.findViewById<MaterialButton>(R.id.btnCardDetail).setOnClickListener {
            val intent = Intent(this, MatchDetailActivity::class.java)
            intent.putExtra("ACTIVITY_ID", activity.id)
            intent.putExtra("MATCH_ID", activity.id)
            intent.putExtra("SPORT_TAG", activity.sport.name.uppercase())
            intent.putExtra("LOCATION", activity.location.name)
            startActivity(intent)
        }

        return card
    }

    // ─── Helpers ──────────────────────────────────────

    private fun applyBadgeBg(textView: TextView, hexColor: String) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (10 * resources.displayMetrics.density)
            setColor(Color.parseColor(hexColor))
        }
        textView.background = bg
    }

    private fun parseColorWithAlpha(hexColor: String, alpha: Int): Int {
        val baseColor = Color.parseColor(hexColor)
        return Color.argb(
            alpha,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
    }

    private fun formatEventDate(iso: String): String {
        return try {
            val dt = LocalDateTime.parse(iso)
            val date = dt.toLocalDate()
            val today = LocalDate.now()
            val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))

            when {
                date == today -> "Hoy, $time hrs"
                date == today.plusDays(1) -> "Mañana, $time hrs"
                date.isBefore(today) -> dt.format(
                    DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.forLanguageTag("es"))
                )
                else -> {
                    val daysAhead = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    if (daysAhead <= 7) {
                        val dayName = dt.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es")))
                            .replaceFirstChar { it.uppercase() }
                        "$dayName, $time hrs"
                    } else {
                        dt.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.forLanguageTag("es")))
                    }
                }
            }
        } catch (e: Exception) {
            iso
        }
    }

    private fun isEventPast(iso: String): Boolean {
        return try {
            LocalDateTime.parse(iso).isBefore(LocalDateTime.now())
        } catch (e: Exception) {
            false
        }
    }
}