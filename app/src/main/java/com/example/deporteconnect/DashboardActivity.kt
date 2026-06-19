package com.example.deporteconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.deporteconnect.util.UserCache
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DashboardActivity : AppCompatActivity() {

    private lateinit var activityRepository: ActivityRepository

    private var allActivities: List<ActivityResponse> = emptyList()
    private var activeDateFilter: String? = null
    private var searchQuery: String = ""

    // iconKey → Pair(drawable, colorHex)
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
        setContentView(R.layout.activity_dashboard)

        activityRepository = ActivityRepository(this)

        setupBottomNav()
        setupSettings()
        setupChips()
        setupSearch()
        setupSwipeRefresh()

        // Pintar al instante el feed que la pantalla de carga dejo en memoria
        UserCache.feed?.let {
            allActivities = it
            applyFilters()
        }
    }

    override fun onResume() {
        super.onResume()
        loadActivities()
    }

    private fun setupSettings() {
        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupSwipeRefresh() {
        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipe?.setColorSchemeResources(R.color.dc_green, R.color.dc_blue)
        swipe?.setOnRefreshListener { loadActivities() }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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

    private fun setupChips() {
        val chipHoy = findViewById<Chip>(R.id.chipHoy)
        val chipManana = findViewById<Chip>(R.id.chipManana)
        val chipSemana = findViewById<Chip>(R.id.chipSemana)
        val chipMes = findViewById<Chip>(R.id.chipMes)

        val chipsMap = mapOf(
            chipHoy to "HOY",
            chipManana to "MANANA",
            chipSemana to "SEMANA",
            chipMes to "MES"
        )

        chipsMap.forEach { (chip, code) ->
            chip.setOnClickListener {
                activeDateFilter = if (activeDateFilter == code) {
                    chip.isChecked = false
                    null
                } else {
                    code
                }
                applyFilters()
            }
        }
    }

    private fun setupSearch() {
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })
    }

    private fun loadActivities() {
        lifecycleScope.launch {
            when (val result = activityRepository.getFeed()) {
                is Resource.Success -> {
                    allActivities = result.data
                    UserCache.setFeed(result.data)
                    applyFilters()
                }
                is Resource.Error -> {
                    // Solo mostrar error/vaciar si no teniamos nada en cache
                    if (UserCache.feed == null) {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Error al cargar partidos: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        allActivities = emptyList()
                        applyFilters()
                    }
                }
                else -> {}
            }
            findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
        }
    }

    private fun applyFilters() {
        var filtered = allActivities

        if (activeDateFilter != null) {
            filtered = filtered.filter { matchesDateFilter(it.eventAt, activeDateFilter!!) }
        }

        if (searchQuery.isNotBlank()) {
            val q = normalize(searchQuery)
            filtered = filtered.filter { activity ->
                normalize(activity.title).contains(q) ||
                        normalize(activity.location.name).contains(q) ||
                        normalize(activity.location.commune ?: "").contains(q) ||
                        normalize(activity.sport.name).contains(q)
            }
        }

        renderActivities(filtered)
    }

    private fun normalize(text: String): String {
        val lowered = text.lowercase().trim()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun matchesDateFilter(isoDateTime: String, filter: String): Boolean {
        return try {
            val eventDate = LocalDateTime.parse(isoDateTime).toLocalDate()
            val today = LocalDate.now()

            when (filter) {
                "HOY" -> eventDate == today
                "MANANA" -> eventDate == today.plusDays(1)
                "SEMANA" -> {
                    !eventDate.isBefore(today) &&
                            ChronoUnit.DAYS.between(today, eventDate) <= 6
                }
                "MES" -> {
                    eventDate.year == today.year &&
                            eventDate.month == today.month &&
                            !eventDate.isBefore(today)
                }
                else -> true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun renderActivities(activities: List<ActivityResponse>) {
        val container = findViewById<LinearLayout>(R.id.containerMatches)
        container.removeAllViews()

        if (activities.isEmpty()) {
            val message = when {
                searchQuery.isNotBlank() -> "No se encontraron partidos para \"$searchQuery\""
                activeDateFilter != null -> "No hay partidos en este rango de fechas."
                else -> "Aún no hay partidos disponibles.\n¡Sé el primero en crear uno! 🏃"
            }
            val empty = TextView(this).apply {
                text = message
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(40, 60, 40, 60)
                setTextColor(Color.parseColor("#888888"))
            }
            container.addView(empty)
            return
        }

        activities.forEach { activity ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_match_card, container, false)

            // ─── Ícono del deporte con color ───
            val iconInfo = iconMap[activity.sport.iconKey] ?: Pair(R.drawable.ic_soccer, "#22C55E")
            card.findViewById<ImageView>(R.id.ivSportTagIcon).apply {
                setImageResource(iconInfo.first)
                setColorFilter(Color.parseColor(iconInfo.second))
            }
            // Fondo circular suave del ícono
            val iconContainer = card.findViewById<FrameLayout>(R.id.sportIconContainer)
            iconContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorWithAlpha(iconInfo.second, 0x22))
            }

            // ─── Comuna como etiqueta (arriba) ───
            card.findViewById<TextView>(R.id.tvLocationTag).text =
                (activity.location.commune ?: activity.location.name).uppercase()

            // ─── Deporte en grande ───
            card.findViewById<TextView>(R.id.tvMatchName).text = activity.sport.name

            // ─── Fecha ───
            card.findViewById<TextView>(R.id.tvMatchDate).text = formatDate(activity.eventAt)

            // ─── Cupos ───
            val slots = activity.maxParticipants - activity.currentParticipants
            val slotsText = when {
                slots <= 0 -> "Sin cupos"
                slots == 1 -> "¡Último cupo!"
                else -> "$slots cupos disponibles"
            }
            val tvSlots = card.findViewById<TextView>(R.id.tvSlots)
            val ivIcon  = card.findViewById<ImageView>(R.id.ivSlotsIcon)
            tvSlots.text = slotsText

            if (slots == 1) {
                tvSlots.setTextColor(resources.getColor(R.color.dc_warning, theme))
                ivIcon.setImageResource(R.drawable.ic_warning)
                ivIcon.setColorFilter(resources.getColor(R.color.dc_warning, theme))
            } else {
                tvSlots.setTextColor(resources.getColor(R.color.dc_green, theme))
                ivIcon.setImageResource(R.drawable.ic_group)
                ivIcon.setColorFilter(resources.getColor(R.color.dc_green, theme))
            }

            val onClick = View.OnClickListener {
                val intent = Intent(this, MatchDetailActivity::class.java)
                intent.putExtra("MATCH_ID", activity.id)
                intent.putExtra("ACTIVITY_ID", activity.id)
                intent.putExtra("SPORT_TAG", activity.sport.name.uppercase())
                intent.putExtra("LOCATION", activity.location.name)
                startActivity(intent)
            }

            card.setOnClickListener(onClick)
            card.findViewById<MaterialButton>(R.id.btnViewDetail)?.setOnClickListener(onClick)

            container.addView(card)
        }
    }

    private fun colorWithAlpha(hexColor: String, alpha: Int): Int {
        val base = Color.parseColor(hexColor)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun formatDate(isoDateTime: String): String {
        return try {
            val dt = LocalDateTime.parse(isoDateTime)
            val today = LocalDateTime.now().toLocalDate()
            val date = dt.toLocalDate()
            val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))

            when {
                date == today -> "Hoy $time"
                date == today.plusDays(1) -> "Mañana $time"
                else -> dt.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))
            }
        } catch (e: Exception) {
            isoDateTime
        }
    }
}