package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.network.ActivityResponse
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Detalle de actividad deportiva.
 * Recibe via Intent:
 *  - ACTIVITY_ID (Long): ID de la actividad
 *  (también acepta MATCH_ID para compatibilidad)
 */
class MatchDetailActivity : AppCompatActivity() {

    private var activityId: Long = 0L
    private var currentUserId: Long = 0L
    private var currentActivity: ActivityResponse? = null
    private var isUserJoined: Boolean = false
    private var isUserOrganizer: Boolean = false

    private lateinit var activityRepository: ActivityRepository
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_detail)

        // Recibir el ID (compatible con MATCH_ID viejo y ACTIVITY_ID nuevo)
        activityId = intent.getLongExtra("ACTIVITY_ID", 0L)
        if (activityId == 0L) {
            activityId = intent.getIntExtra("MATCH_ID", 0).toLong()
        }
        if (activityId == 0L) {
            Toast.makeText(this, "Actividad no encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        activityRepository = ActivityRepository(this)
        userRepository = UserRepository(this)

        // Obtener mi userId para saber si soy organizador
        currentUserId = getSharedPreferences("deporte_connect_session", MODE_PRIVATE)
            .getLong("user_id", 0L)

        setupListeners()
        loadActivityDetails()
    }

    override fun onResume() {
        super.onResume()
        // Recargar al regresar (por si me uní/salí desde otra pantalla)
        if (activityId != 0L) loadActivityDetails()
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.ivShare).setOnClickListener {
            shareMatch()
        }

        findViewById<TextView>(R.id.tvViewRoute).setOnClickListener {
            Toast.makeText(this, "Abriendo ruta en mapa (próximamente)", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnReportActivity).setOnClickListener {
            showReportDialog()
        }

        findViewById<MaterialButton>(R.id.btnJoinMatch).setOnClickListener {
            when {
                isUserOrganizer -> confirmCancel()
                isUserJoined -> confirmLeave()
                else -> confirmJoin()
            }
        }

        findViewById<MaterialButton>(R.id.btnRateOrganizer).setOnClickListener {
            showRateOrganizerDialog()
        }
    }

    private fun loadActivityDetails() {
        lifecycleScope.launch {
            when (val result = activityRepository.getById(activityId)) {
                is Resource.Success -> {
                    currentActivity = result.data
                    renderActivity(result.data)
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun renderActivity(act: ActivityResponse) {
        // Determinar mi rol
        isUserOrganizer = act.organizerId == currentUserId
        // No tenemos endpoint específico para "estoy inscrito" — lo deducimos:
        //   si soy organizador → estoy "inscrito" (es mi actividad)
        //   si no soy organizador → revisamos al consultar mis-partidos en background
        if (isUserOrganizer) {
            isUserJoined = true
        } else {
            checkIfUserJoined()
        }

        // ─── Header ──────────────────────────────
        // Título del deporte (ej: "Fútbol")
        findViewById<TextView>(R.id.tvSportTitle).text = act.sport.name

        // Nombre del complejo / ubicación
        findViewById<TextView>(R.id.tvComplexName).text = act.location.name

        // Status tag
        findViewById<TextView>(R.id.tvStatusTag).text = formatStatus(act)

        // Dirección
        val address = listOfNotNull(
            act.location.address,
            act.location.commune,
            act.location.city
        ).joinToString(", ")
        findViewById<TextView>(R.id.tvAddress).text = address.ifBlank { act.location.name }

        // Descripción
        findViewById<TextView>(R.id.tvDescription).text =
            act.description?.ifBlank { "Sin descripción adicional." } ?: "Sin descripción adicional."

        // ─── Stat cards (HORA / PRECIO / NIVEL) ──────────
        setupStatCards(act)

        // ─── Participantes (organizador real + cupos reales) ──────
        setupParticipants(act)

        // ─── Precio total ────────────────────────────
        val price = act.price ?: BigDecimal.ZERO
        val priceText = if (price.compareTo(BigDecimal.ZERO) == 0) "Gratis"
        else "$${formatPrice(price)}"
        findViewById<TextView>(R.id.tvTotalPrice).text = priceText

        // ─── Botón principal ──────────────────────────
        updateMainButton(act)
        updateRateOrganizerButton(act)
    }

    /**
     * Llena la card de participantes con datos REALES:
     *  - Organizador (nombre + iniciales en círculo de color)
     *  - Contador de cupos real (inscritos / máximo)
     *  - Barra de progreso de cupos
     *
     * Nota: el backend no entrega la lista completa de participantes
     * (solo el número y el organizador), por eso mostramos el organizador
     * y el contador en vez de avatares individuales inventados.
     */
    private fun setupParticipants(act: ActivityResponse) {
        val inscritos = act.currentParticipants
        val maximo = act.maxParticipants
        val libres = (maximo - inscritos).coerceAtLeast(0)

        // Contador "X de Y"
        findViewById<TextView>(R.id.tvParticipantsCount).text = "$inscritos de $maximo"
        findViewById<TextView>(R.id.tvInscritos).text =
            if (inscritos == 1) "1 inscrito" else "$inscritos inscritos"
        findViewById<TextView>(R.id.tvCuposLibres).text =
            if (libres == 1) "1 cupo libre" else "$libres cupos libres"

        // Barra de progreso de cupos
        val progress = if (maximo > 0) (inscritos * 100 / maximo) else 0
        findViewById<android.widget.ProgressBar>(R.id.progressCupos).progress = progress

        // Organizador real
        val orgName = act.organizerName ?: "Organizador"
        findViewById<TextView>(R.id.tvOrganizerName).text = orgName
        findViewById<TextView>(R.id.tvOrganizerInitials).text = initialsOf(orgName)
        findViewById<TextView>(R.id.tvOrganizerVerified).apply {
            if (act.organizerVerified == true) {
                text = "Organizador verificado"
                setTextColor(getColor(R.color.dc_green))
            } else {
                text = "Organizador no verificado"
                setTextColor(getColor(R.color.dc_warning))
            }
        }

        // Color del círculo del organizador según su nombre (consistente)
        findViewById<TextView>(R.id.tvOrganizerRating).text = formatOrganizerRating(act)

        val bg = findViewById<View>(R.id.organizerAvatarBg)
        bg.background?.let {
            val color = colorFromName(orgName)
            it.setTint(color)
        }
    }

    /** Iniciales de un nombre: "Daniel Méndez" -> "DM" */
    private fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    /** Color estable a partir del nombre (mismo nombre -> mismo color). */
    private fun colorFromName(name: String): Int {
        val colors = listOf(
            0xFF185FA5.toInt(), // azul
            0xFF0F6E56.toInt(), // teal
            0xFF993C1D.toInt(), // coral
            0xFF534AB7.toInt(), // morado
            0xFF993556.toInt(), // rosa
            0xFF854F0B.toInt()  // ámbar
        )
        val idx = Math.abs(name.hashCode()) % colors.size
        return colors[idx]
    }

    private fun setupStatCards(act: ActivityResponse) {
        // HORA
        findViewById<View>(R.id.statHora).apply {
            findViewById<ImageView>(R.id.ivStatIcon).setImageResource(R.drawable.ic_clock)
            findViewById<ImageView>(R.id.ivStatIcon).setColorFilter(getColor(R.color.dc_blue))
            findViewById<TextView>(R.id.tvStatLabel).text = "HORA"
            findViewById<TextView>(R.id.tvStatValue).text = formatTime(act.eventAt)
        }

        // PRECIO
        findViewById<View>(R.id.statPrecio).apply {
            findViewById<ImageView>(R.id.ivStatIcon).setImageResource(R.drawable.ic_dollar)
            findViewById<ImageView>(R.id.ivStatIcon).setColorFilter(getColor(R.color.dc_green))
            findViewById<TextView>(R.id.tvStatLabel).text = "PRECIO"
            val price = act.price ?: BigDecimal.ZERO
            val text = if (price.compareTo(BigDecimal.ZERO) == 0) "Gratis"
            else "$${formatPrice(price)}"
            findViewById<TextView>(R.id.tvStatValue).text = text
        }

        // NIVEL
        findViewById<View>(R.id.statNivel).apply {
            findViewById<ImageView>(R.id.ivStatIcon).setImageResource(R.drawable.ic_level_bars)
            findViewById<ImageView>(R.id.ivStatIcon).setColorFilter(getColor(R.color.dc_blue))
            findViewById<TextView>(R.id.tvStatLabel).text = "NIVEL"
            findViewById<TextView>(R.id.tvStatValue).text = formatLevel(act.level)
        }
    }

    /** Verifica si el usuario actual está inscrito consultando mis-partidos. */
    private fun checkIfUserJoined() {
        lifecycleScope.launch {
            when (val result = activityRepository.getMyMatches()) {
                is Resource.Success -> {
                    isUserJoined = result.data.any { it.id == activityId }
                    currentActivity?.let { updateMainButton(it) }
                    currentActivity?.let { updateRateOrganizerButton(it) }
                }
                else -> {}
            }
        }
    }

    private fun updateMainButton(act: ActivityResponse) {
        val btn = findViewById<MaterialButton>(R.id.btnJoinMatch)
        btn.isEnabled = true

        when {
            // Status que bloquean cualquier acción
            act.status == "CANCELLED" -> {
                btn.text = "Actividad cancelada"
                btn.isEnabled = false
                btn.setBackgroundColor(getColor(android.R.color.darker_gray))
            }
            act.status == "FINISHED" -> {
                btn.text = "Actividad finalizada"
                btn.isEnabled = false
                btn.setBackgroundColor(getColor(android.R.color.darker_gray))
            }
            // Soy el organizador → puedo cancelar
            isUserOrganizer -> {
                btn.text = "Cancelar actividad"
                btn.setBackgroundColor(getColor(R.color.dc_warning))
            }
            // Estoy inscrito → puedo salirme
            isUserJoined -> {
                btn.text = "✓ Inscrito — Salirme"
                btn.setBackgroundColor(getColor(R.color.dc_green))
            }
            // Está lleno
            act.currentParticipants >= act.maxParticipants -> {
                btn.text = "Actividad llena"
                btn.isEnabled = false
                btn.setBackgroundColor(getColor(android.R.color.darker_gray))
            }
            // Puedo unirme
            else -> {
                btn.text = "Unirme al Partido  ⚽"
                btn.setBackgroundColor(getColor(R.color.dc_blue))
            }
        }
    }

    private fun updateRateOrganizerButton(act: ActivityResponse) {
        val btn = findViewById<MaterialButton>(R.id.btnRateOrganizer)
        val canRate = act.status == "FINISHED" &&
                isUserJoined &&
                !isUserOrganizer &&
                !hasRatedOrganizer()

        btn.visibility = if (canRate) View.VISIBLE else View.GONE
        btn.isEnabled = canRate
        if (canRate) {
            btn.text = "Calificar organizador"
        }
    }

    private fun showRateOrganizerDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 18, 48, 0)
        }
        val ratingBar = RatingBar(this).apply {
            numStars = 5
            stepSize = 1f
            rating = 5f
        }
        container.addView(ratingBar)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Calificar organizador")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val stars = ratingBar.rating.toInt().coerceIn(1, 5)
                dialog.dismiss()
                rateOrganizer(stars)
            }
        }
        dialog.show()
    }

    private fun rateOrganizer(stars: Int) {
        val btn = findViewById<MaterialButton>(R.id.btnRateOrganizer)
        btn.isEnabled = false
        btn.text = "Enviando..."

        lifecycleScope.launch {
            when (val result = activityRepository.rateOrganizer(activityId, stars)) {
                is Resource.Success -> {
                    markOrganizerRated()
                    currentActivity = result.data
                    renderActivity(result.data)
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "Gracias por tu calificación.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is Resource.Error -> {
                    if (result.message.contains("Ya calificaste", ignoreCase = true)) {
                        markOrganizerRated()
                    }
                    Toast.makeText(
                        this@MatchDetailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    currentActivity?.let { updateRateOrganizerButton(it) }
                }
                else -> {}
            }
        }
    }

    private fun joinMatch() {
        val btn = findViewById<MaterialButton>(R.id.btnJoinMatch)
        btn.isEnabled = false
        btn.text = "Uniéndote..."

        lifecycleScope.launch {
            when (val result = activityRepository.join(activityId)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "🎉 ¡Te uniste al partido!",
                        Toast.LENGTH_SHORT
                    ).show()
                    currentActivity = result.data
                    isUserJoined = true
                    updateMainButton(result.data)
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    currentActivity?.let { updateMainButton(it) }
                }
                else -> {}
            }
        }
    }

    private fun confirmJoin() {
        AlertDialog.Builder(this)
            .setTitle("Antes de unirte")
            .setMessage(
                "Revisa que el organizador este verificado.\n\n" +
                        "Prefiere lugares publicos o recintos deportivos.\n\n" +
                        "No compartas datos personales fuera de la app."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Entiendo, unirme") { _, _ -> joinMatch() }
            .show()
    }

    private fun showReportDialog() {
        val reasons = arrayOf(
            "Actividad falsa",
            "Lugar sospechoso",
            "Conducta inapropiada",
            "Otro"
        )

        AlertDialog.Builder(this)
            .setTitle("Reportar actividad")
            .setItems(reasons) { _, which -> sendReport(reasons[which]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendReport(reason: String) {
        lifecycleScope.launch {
            when (val result = activityRepository.report(activityId, reason)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "Reporte enviado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("Salirme del partido")
            .setMessage("¿Seguro que quieres salirte de esta actividad?")
            .setPositiveButton("Sí, salirme") { _, _ -> leaveMatch() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun leaveMatch() {
        val btn = findViewById<MaterialButton>(R.id.btnJoinMatch)
        btn.isEnabled = false
        btn.text = "Saliendo..."

        lifecycleScope.launch {
            when (val result = activityRepository.leave(activityId)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "Te saliste del partido",
                        Toast.LENGTH_SHORT
                    ).show()
                    isUserJoined = false
                    loadActivityDetails()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    currentActivity?.let { updateMainButton(it) }
                }
                else -> {}
            }
        }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancelar actividad")
            .setMessage("¿Seguro que quieres cancelar esta actividad? Esto la quitará de la lista para todos los participantes.")
            .setPositiveButton("Sí, cancelar") { _, _ -> cancelMatch() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelMatch() {
        val btn = findViewById<MaterialButton>(R.id.btnJoinMatch)
        btn.isEnabled = false
        btn.text = "Cancelando..."

        lifecycleScope.launch {
            when (val result = activityRepository.cancel(activityId)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        "Actividad cancelada",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@MatchDetailActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    currentActivity?.let { updateMainButton(it) }
                }
                else -> {}
            }
        }
    }

    private fun shareMatch() {
        val act = currentActivity
        val message = if (act != null) {
            "¡Mira este partido de ${act.sport.name} en ${act.location.name} en Deporte Connect! ⚽"
        } else {
            "¡Mira este partido en Deporte Connect! ⚽"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir partido"))
    }

    // ─── Formatters ──────────────────────────────

    private fun formatStatus(act: ActivityResponse): String {
        return when (act.status) {
            "OPEN" -> {
                val cupos = act.maxParticipants - act.currentParticipants
                when {
                    cupos <= 0 -> "Lleno"
                    cupos == 1 -> "¡Último cupo!"
                    else -> "$cupos cupos disponibles"
                }
            }
            "FULL" -> "Lleno"
            "CANCELLED" -> "Cancelada"
            "FINISHED" -> "Finalizada"
            else -> act.status ?: ""
        }
    }

    private fun formatOrganizerRating(act: ActivityResponse): String {
        val count = act.organizerRatingCount ?: 0
        if (count <= 0) return "Sin reseñas todavía"

        val rating = act.organizerRating ?: 0.0
        val reviews = if (count == 1) "1 reseña" else "$count reseñas"
        return String.format(Locale.US, "⭐ %.1f (%s)", rating, reviews)
    }

    private fun hasRatedOrganizer(): Boolean {
        return getSharedPreferences("deporte_connect_session", MODE_PRIVATE)
            .getBoolean(organizerRatingKey(), false)
    }

    private fun markOrganizerRated() {
        getSharedPreferences("deporte_connect_session", MODE_PRIVATE)
            .edit()
            .putBoolean(organizerRatingKey(), true)
            .apply()
    }

    private fun organizerRatingKey(): String {
        return "organizer_rating_${currentUserId}_${activityId}"
    }

    private fun formatTime(iso: String): String = try {
        LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "" }

    private fun formatLevel(level: String?): String = when (level) {
        "PRINCIPIANTE" -> "Princ."
        "INTERMEDIO" -> "Interm."
        "PROFESIONAL" -> "Prof."
        else -> level ?: ""
    }

    private fun formatPrice(price: BigDecimal): String {
        val intValue = price.toInt()
        return String.format(Locale.US, "%,d", intValue).replace(',', '.')
    }
}
