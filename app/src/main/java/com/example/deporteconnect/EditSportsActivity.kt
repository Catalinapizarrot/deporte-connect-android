package com.example.deporteconnect

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.SportRepository
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.network.SportResponse
import com.example.deporteconnect.network.UpdateProfileRequest
import com.example.deporteconnect.network.UserResponse
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Pantalla para editar los deportes de preferencia.
 *
 * - Carga el perfil actual (para preseleccionar los deportes ya elegidos)
 * - Carga todos los deportes disponibles
 * - Permite seleccionar/deseleccionar tocando las cards (grilla 2 columnas)
 * - Exige mínimo 1 deporte
 * - Guarda reutilizando updateProfile (reenvía los datos actuales + nuevos interestIds)
 */
class EditSportsActivity : AppCompatActivity() {

    companion object { private const val TAG = "EditSports" }

    private lateinit var sportRepository: SportRepository
    private lateinit var userRepository: UserRepository

    private var sports: List<SportResponse> = emptyList()
    private val selectedSportIds = mutableSetOf<Long>()

    // Datos del perfil actual (para reenviarlos sin cambios al guardar)
    private var currentUser: UserResponse? = null

    private val iconMap = mapOf(
        "ic_soccer"     to R.drawable.ic_soccer,
        "ic_padel"      to R.drawable.ic_padel,
        "ic_basketball" to R.drawable.ic_basketball,
        "ic_tennis"     to R.drawable.ic_tennis,
        "ic_volleyball" to R.drawable.ic_volleyball,
        "ic_flash"      to R.drawable.ic_flash
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_sports)

        sportRepository = SportRepository(this)
        userRepository = UserRepository(this)

        findViewById<ImageView>(R.id.ivBackSports).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSaveSports).setOnClickListener { saveSports() }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            // 1. Cargar perfil actual (para saber qué deportes ya tengo)
            when (val profileResult = userRepository.getMyProfile()) {
                is Resource.Success -> {
                    currentUser = profileResult.data
                    profileResult.data.interests?.forEach { selectedSportIds.add(it.id) }
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@EditSportsActivity,
                        "No se pudo cargar tu perfil: ${profileResult.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }

            // 2. Cargar todos los deportes disponibles
            when (val sportsResult = sportRepository.getAllSports()) {
                is Resource.Success -> {
                    sports = sportsResult.data
                    findViewById<TextView>(R.id.tvLoadingSports)?.visibility = View.GONE
                    if (sports.isEmpty()) {
                        Toast.makeText(this@EditSportsActivity, "No hay deportes disponibles", Toast.LENGTH_SHORT).show()
                    } else {
                        renderSportsWhenReady()
                    }
                }
                is Resource.Error -> {
                    findViewById<TextView>(R.id.tvLoadingSports)?.text = "Error cargando deportes"
                    Toast.makeText(this@EditSportsActivity, "Error: ${sportsResult.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun renderSportsWhenReady() {
        val container = findViewById<GridLayout>(R.id.containerSports)
        if (container.width > 0) {
            renderSports(container.width)
        } else {
            container.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    renderSports(container.width)
                }
            })
        }
    }

    private fun renderSports(containerWidth: Int) {
        val container = findViewById<GridLayout>(R.id.containerSports)
        container.removeAllViews()
        container.columnCount = 2

        val density = resources.displayMetrics.density
        val cardMargin = (4 * density).toInt()
        val totalMargin = cardMargin * 4
        val cardWidth = (containerWidth - totalMargin) / 2

        sports.forEach { sport ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_sport_card, container, false)

            val params = GridLayout.LayoutParams().apply {
                width = cardWidth
                height = (110 * density).toInt()
                setMargins(cardMargin, cardMargin, cardMargin, cardMargin)
            }
            card.layoutParams = params

            card.findViewById<TextView>(R.id.tvSportName).text = sport.name
            val ivIcon = card.findViewById<ImageView>(R.id.ivSportIcon)
            ivIcon.setImageResource(iconMap[sport.iconKey] ?: R.drawable.ic_soccer)

            sport.color?.let { hex ->
                try { ivIcon.setColorFilter(Color.parseColor(hex)) } catch (_: Exception) {}
            }

            // Preseleccionar si ya está en mis intereses
            val isSelected = selectedSportIds.contains(sport.id)
            updateCardAppearance(card, isSelected)

            card.setOnClickListener {
                val nowSelected = !selectedSportIds.contains(sport.id)
                if (nowSelected) selectedSportIds.add(sport.id)
                else selectedSportIds.remove(sport.id)
                updateCardAppearance(card, nowSelected)
                updateCounter()
            }

            container.addView(card)
        }

        updateCounter()
    }

    private fun updateCounter() {
        val tv = findViewById<TextView>(R.id.tvSportsCounter)
        tv?.text = when (selectedSportIds.size) {
            0 -> "Selecciona al menos 1 deporte"
            1 -> "1 deporte seleccionado"
            else -> "${selectedSportIds.size} deportes seleccionados"
        }
    }

    private fun updateCardAppearance(card: View, isSelected: Boolean) {
        val iconContainer = card.findViewById<FrameLayout>(R.id.sportIconContainer)
        val checkBadge = card.findViewById<FrameLayout>(R.id.checkBadge)

        if (isSelected) {
            iconContainer.setBackgroundResource(R.drawable.bg_sport_icon_selected)
            checkBadge.visibility = View.VISIBLE
            (card as CardView).setCardBackgroundColor(Color.parseColor("#DCFCE7"))
        } else {
            iconContainer.setBackgroundResource(R.drawable.bg_sport_icon_default)
            checkBadge.visibility = View.GONE
            (card as CardView).setCardBackgroundColor(Color.WHITE)
        }
    }

    private fun saveSports() {
        if (selectedSportIds.isEmpty()) {
            Toast.makeText(this, "Debes tener al menos 1 deporte de preferencia", Toast.LENGTH_SHORT).show()
            return
        }

        val user = currentUser
        if (user == null) {
            Toast.makeText(this, "Espera a que cargue tu perfil", Toast.LENGTH_SHORT).show()
            return
        }

        val btn = findViewById<MaterialButton>(R.id.btnSaveSports)
        btn.isEnabled = false
        btn.text = "Guardando..."

        // Reenviar datos actuales + nuevos deportes (updateProfile exige todos los campos)
        val request = UpdateProfileRequest(
            fullName = user.fullName ?: "Usuario",
            phone = user.phone ?: "+56900000000",
            birthDate = user.birthDate ?: "2000-01-01",
            gender = user.gender ?: "HOMBRE",
            interestIds = selectedSportIds,
            avatarUrl = user.avatarUrl,
            bio = user.bio
        )

        lifecycleScope.launch {
            when (val result = userRepository.updateProfile(request)) {
                is Resource.Success -> {
                    Toast.makeText(this@EditSportsActivity, "✓ Deportes actualizados", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(this@EditSportsActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                    btn.isEnabled = true
                    btn.text = "Guardar cambios"
                }
                else -> {}
            }
        }
    }
}
