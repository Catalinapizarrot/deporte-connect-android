package com.example.deporteconnect

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.util.UserCache
import kotlinx.coroutines.launch

/**
 * Pantalla de carga animada.
 *
 * Mantiene toda la parte visual original (barra animada, rotacion de
 * mensajes, tags con iconos, fullscreen, texto segun onboarding) y ANADE
 * una precarga REAL de los datos del usuario:
 *
 *  - Durante los ~7 segundos de animacion, trae el perfil, mis-partidos
 *    y el feed una sola vez y los guarda en UserCache.
 *  - Asi el Dashboard/Perfil/Mensajes abren con los datos ya en memoria,
 *    sin parpadeo y sin recargar en cada pantalla.
 *
 * Se usa en 2 flujos:
 *  - FROM_ONBOARDING=true  -> despues de completar el perfil (nuevo usuario)
 *  - FROM_ONBOARDING=false -> al hacer login con usuario existente
 */
class LoadingActivity : AppCompatActivity() {

    companion object {
        private const val LOADING_DURATION = 10000L  // 10 segundos
        private const val MESSAGE_ROTATION  = 2500L // cambio de mensaje cada 1.4s
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoadingMessage: TextView
    private lateinit var tvSyncStatus: TextView

    private lateinit var userRepository: UserRepository
    private lateinit var activityRepository: ActivityRepository

    // Mensajes de sincronizacion rotativos
    private val syncMessages = listOf(
        "Sincronizando pistas locales en Santiago",
        "Cargando actividades segun tus intereses",
        "Calculando partidos cercanos a ti",
        "Conectando con deportistas de tu zona",
        "Preparando tu feed personalizado"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var messageIndex = 0
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        progressBar      = findViewById(R.id.progressBar)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvSyncStatus     = findViewById(R.id.tvSyncStatus)

        userRepository = UserRepository(this)
        activityRepository = ActivityRepository(this)

        setupTags()
        customizeForFlow()
        startLoadingAnimation()
        preloadUserData()   // precarga real en paralelo a la animacion
    }

    private fun setupTags() {
        findViewById<View>(R.id.tagCercania).apply {
            findViewById<ImageView>(R.id.ivTagIcon).setImageResource(R.drawable.ic_location)
            findViewById<TextView>(R.id.tvTagLabel).text = "CERCANIA"
        }
        findViewById<View>(R.id.tagEquipos).apply {
            findViewById<ImageView>(R.id.ivTagIcon).setImageResource(R.drawable.ic_group)
            findViewById<TextView>(R.id.tvTagLabel).text = "EQUIPOS"
        }
        findViewById<View>(R.id.tagPartidas).apply {
            findViewById<ImageView>(R.id.ivTagIcon).setImageResource(R.drawable.ic_flash)
            findViewById<TextView>(R.id.tvTagLabel).text = "PARTIDAS"
        }
    }

    private fun customizeForFlow() {
        val fromOnboarding = intent.getBooleanExtra("FROM_ONBOARDING", false)
        tvLoadingMessage.text = if (fromOnboarding) {
            "Preparando tu experiencia\ndeportiva personalizada..."
        } else {
            "Cargando tu informacion\ny actividades disponibles..."
        }
    }

    private fun startLoadingAnimation() {
        // Barra animada 0 -> 100 en LOADING_DURATION
        ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = LOADING_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Rotar mensajes de sync
        val rotateRunnable = object : Runnable {
            override fun run() {
                messageIndex = (messageIndex + 1) % syncMessages.size
                tvSyncStatus.text = syncMessages[messageIndex]
                handler.postDelayed(this, MESSAGE_ROTATION)
            }
        }
        handler.postDelayed(rotateRunnable, MESSAGE_ROTATION)

        // Al terminar la animacion -> Dashboard.
        // (La precarga corre en paralelo; para cuando termina la animacion
        //  de 7s, los datos ya suelen estar listos en UserCache.)
        handler.postDelayed({ goToDashboard() }, LOADING_DURATION)
    }

    /**
     * Precarga el perfil, los partidos y el feed del usuario en UserCache.
     * Corre en paralelo a la animacion de 7 segundos, asi no anade espera.
     */
    private fun preloadUserData() {
        lifecycleScope.launch {
            // 1. Perfil (nombre, avatar, deportes)
            when (val profileResult = userRepository.getMyProfile()) {
                is Resource.Success -> UserCache.setProfile(profileResult.data)
                else -> { /* si falla, el Dashboard lo reintentara */ }
            }

            // 2. Mis partidos (para Mis Partidos y Mensajes, abren al instante)
            when (val matchesResult = activityRepository.getMyMatches()) {
                is Resource.Success -> UserCache.setMyMatches(matchesResult.data)
                else -> { /* no bloqueante */ }
            }

            // 3. Feed de actividades (para que el Dashboard abra al instante)
            when (val feedResult = activityRepository.getFeed()) {
                is Resource.Success -> UserCache.setFeed(feedResult.data)
                else -> { /* no bloqueante, el Dashboard lo reintentara */ }
            }
        }
    }

    private fun goToDashboard() {
        if (navigated) return
        navigated = true
        handler.removeCallbacksAndMessages(null)

        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        // Bloqueado durante la carga
    }
}