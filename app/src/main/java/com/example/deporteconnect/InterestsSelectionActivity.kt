package com.example.deporteconnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import com.example.deporteconnect.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class InterestsSelectionActivity : AppCompatActivity() {

    companion object { private const val TAG = "Interests" }

    private var fullName: String = ""
    private var phone: String = ""
    private var birthDate: String = ""
    private var gender: String = ""
    private var avatarUrl: String? = null

    private var sports: List<SportResponse> = emptyList()
    private val selectedSportIds = mutableSetOf<Long>()

    private val iconMap = mapOf(
        "ic_soccer"     to R.drawable.ic_soccer,
        "ic_padel"      to R.drawable.ic_padel,
        "ic_basketball" to R.drawable.ic_basketball,
        "ic_tennis"     to R.drawable.ic_tennis,
        "ic_volleyball" to R.drawable.ic_volleyball,
        "ic_flash"      to R.drawable.ic_flash
    )

    private lateinit var sportRepository: SportRepository
    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interests_selection)

        fullName  = intent.getStringExtra("FULL_NAME") ?: ""
        phone     = intent.getStringExtra("PHONE") ?: ""
        birthDate = intent.getStringExtra("BIRTH_DATE") ?: ""
        gender    = intent.getStringExtra("GENDER") ?: ""
        avatarUrl = intent.getStringExtra("AVATAR_URL")
        Log.d(TAG, "Datos: name=$fullName, gender=$gender, hasAvatar=${avatarUrl != null}")

        sportRepository = SportRepository(this)
        userRepository = UserRepository(this)
        sessionManager = SessionManager.getInstance(this)

        loadSports()

        findViewById<MaterialButton>(R.id.btnFinishProfile).setOnClickListener {
            finishOnboarding()
        }
    }

    private fun loadSports() {
        lifecycleScope.launch {
            findViewById<TextView>(R.id.tvLoadingSports)?.visibility = View.GONE

            when (val result = sportRepository.getAllSports()) {
                is Resource.Success -> {
                    sports = result.data
                    if (sports.isEmpty()) Toast.makeText(this@InterestsSelectionActivity, "No se cargaron deportes", Toast.LENGTH_LONG).show()
                    else renderSportsWhenReady()
                }
                is Resource.Error -> Toast.makeText(this@InterestsSelectionActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    private fun renderSportsWhenReady() {
        val container = findViewById<GridLayout>(R.id.containerSports)
        if (container.width > 0) renderSports(container.width)
        else container.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                renderSports(container.width)
            }
        })
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

            updateCardAppearance(card, false)

            card.setOnClickListener {
                val nowSelected = !selectedSportIds.contains(sport.id)
                if (nowSelected) selectedSportIds.add(sport.id)
                else selectedSportIds.remove(sport.id)
                updateCardAppearance(card, nowSelected)
            }

            container.addView(card)
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

    private fun finishOnboarding() {
        if (selectedSportIds.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un deporte", Toast.LENGTH_SHORT).show()
            return
        }

        val btn = findViewById<MaterialButton>(R.id.btnFinishProfile)
        btn.isEnabled = false
        btn.text = "Guardando..."

        if (fullName.isBlank()) fullName = "Usuario"
        if (phone.isBlank()) phone = "+56900000000"
        if (birthDate.isBlank()) birthDate = "2000-01-01"
        if (gender.isBlank()) gender = "HOMBRE"

        val request = UpdateProfileRequest(
            fullName = fullName,
            phone = phone,
            birthDate = birthDate,
            gender = gender,
            interestIds = selectedSportIds,
            avatarUrl = avatarUrl   // ← viene del paso anterior, puede ser color o base64
        )

        lifecycleScope.launch {
            when (val result = userRepository.updateProfile(request)) {
                is Resource.Success -> {
                    val user = result.data
                    val profileComplete = user.profileComplete == true

                    sessionManager.saveSession(
                        token = sessionManager.getToken() ?: "",
                        userId = user.id,
                        email = user.email,
                        fullName = user.fullName,
                        profileComplete = profileComplete
                    )

                    if (profileComplete) {
                        Toast.makeText(this@InterestsSelectionActivity, "✓ Perfil guardado", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@InterestsSelectionActivity, LoadingActivity::class.java)
                        intent.putExtra("FROM_ONBOARDING", true)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this@InterestsSelectionActivity,
                            "Faltan datos para completar tu perfil. Revisa nombre, telefono, fecha, genero e intereses.",
                            Toast.LENGTH_LONG
                        ).show()
                        btn.isEnabled = true
                        btn.text = "Finalizar  ✓"
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this@InterestsSelectionActivity, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                    btn.isEnabled = true
                    btn.text = "Finalizar  ✓"
                }
                else -> {}
            }
        }
    }
}
