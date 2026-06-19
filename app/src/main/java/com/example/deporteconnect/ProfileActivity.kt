package com.example.deporteconnect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.network.ActivityResponse
import com.example.deporteconnect.network.UserResponse
import com.example.deporteconnect.util.AvatarHelper
import com.example.deporteconnect.util.UserCache
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    companion object { private const val TAG = "ProfileActivity" }

    private lateinit var userRepository: UserRepository
    private lateinit var activityRepository: ActivityRepository
    private var photoUri: Uri? = null

    private val iconMap = mapOf(
        "ic_soccer"     to Pair(R.drawable.ic_soccer, "#22C55E"),
        "ic_padel"      to Pair(R.drawable.ic_padel, "#7C3AED"),
        "ic_basketball" to Pair(R.drawable.ic_basketball, "#2563EB"),
        "ic_tennis"     to Pair(R.drawable.ic_tennis, "#10B981"),
        "ic_volleyball" to Pair(R.drawable.ic_volleyball, "#F59E0B"),
        "ic_flash"      to Pair(R.drawable.ic_flash, "#EF4444")
    )

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        Log.d(TAG, "TakePicture result: success=$success, photoUri=$photoUri")
        if (success) {
            val uri = photoUri
            if (uri == null) {
                Toast.makeText(this, "Error: no se obtuvo la foto", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val bitmap = loadBitmapFromUri(uri)
            Log.d(TAG, "Bitmap from camera: ${bitmap?.width}x${bitmap?.height}")
            if (bitmap == null) {
                Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            processBitmap(bitmap)
        } else {
            Log.d(TAG, "Camara cancelada o fallo")
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "Gallery uri: $uri")
                processBitmap(loadBitmapFromUri(uri))
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Permiso de camara denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        userRepository = UserRepository(this)
        activityRepository = ActivityRepository(this)

        savedInstanceState?.getString("photoUri")?.let {
            photoUri = Uri.parse(it)
        }

        findViewById<ImageView>(R.id.ivSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.avatarContainer).setOnClickListener {
            showAvatarOptionsDialog()
        }

        setupBottomNav()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoUri?.let { outState.putString("photoUri", it.toString()) }
    }

    override fun onResume() {
        super.onResume()
        // Pintar al instante lo que ya esta en memoria (sin parpadeo),
        // y luego refrescar del backend en segundo plano.
        UserCache.profile?.let { renderUser(it) }
        UserCache.myMatches?.let { renderActivities(it) }
        loadUserProfile()
        loadMyActivities()
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            when (val result = userRepository.getMyProfile()) {
                is Resource.Success -> {
                    UserCache.setProfile(result.data)
                    renderUser(result.data)
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@ProfileActivity,
                        "No se pudo cargar el perfil: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun renderUser(user: UserResponse) {
        findViewById<TextView>(R.id.tvUserName).text = user.fullName ?: "Usuario"
        val handleView = findViewById<TextView>(R.id.tvUserHandle)
        handleView.text = ""
        handleView.visibility = View.GONE

        findViewById<TextView>(R.id.tvFullName).text = user.fullName ?: "—"

        val age = calculateAge(user.birthDate)
        findViewById<TextView>(R.id.tvAge).text = if (age > 0) "$age anos" else "—"

        findViewById<TextView>(R.id.tvGender).text = when (user.gender) {
            "HOMBRE" -> "Hombre"
            "MUJER" -> "Mujer"
            else -> "—"
        }

        val sports = user.interests
        findViewById<TextView>(R.id.tvSports).text =
            if (sports.isNullOrEmpty()) "—"
            else sports.joinToString(", ") { it.name }

        findViewById<TextView>(R.id.tvMemberSince).text = formatMemberSince(user.createdAt)

        // ⭐ Rating de participante (Fase 1)
        val ratingValue = user.participantRating ?: 0.0
        val ratingCount = user.participantRatingCount ?: 0
        val starsView = findViewById<TextView>(R.id.tvRatingStars)
        val valueView = findViewById<TextView>(R.id.tvRatingValue)
        val countView = findViewById<TextView>(R.id.tvRatingCount)
        if (ratingValue > 0.0 && ratingCount > 0) {
            val filled = Math.round(ratingValue).toInt().coerceIn(0, 5)
            starsView.text = "★".repeat(filled) + "☆".repeat(5 - filled)
            valueView.text = String.format(Locale.US, "%.1f", ratingValue)
            countView.text = if (ratingCount == 1) "1 RESEÑA" else "$ratingCount RESEÑAS"
        } else {
            starsView.text = "☆☆☆☆☆"
            valueView.text = "—"
            countView.text = "SIN RESEÑAS"
        }

        AvatarHelper.applyAvatar(
            avatarUrl = user.avatarUrl,
            fullName = user.fullName,
            ivImage = findViewById(R.id.ivAvatarPhoto),
            tvInitials = findViewById(R.id.tvAvatarInitials),
            bgView = findViewById(R.id.avatarBg)
        )
    }

    private fun loadMyActivities() {
        lifecycleScope.launch {
            when (val result = activityRepository.getMyMatches()) {
                is Resource.Success -> {
                    UserCache.setMyMatches(result.data)
                    renderActivities(result.data)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Error cargando actividades: ${result.message}")
                    renderActivities(emptyList())
                }
                else -> {}
            }
        }
    }

    private fun renderActivities(activities: List<ActivityResponse>) {
        val container = findViewById<LinearLayout>(R.id.containerRecentActivities)
        val loading = findViewById<TextView>(R.id.tvLoadingActivities)
        container.removeAllViews()
        loading?.visibility = View.GONE

        findViewById<TextView>(R.id.tvEventCount).text = activities.size.toString()

        if (activities.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Aun no participas en actividades."
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                gravity = android.view.Gravity.CENTER
                setPadding(20, 30, 20, 30)
            }
            container.addView(empty)
            return
        }

        val sorted = activities.sortedBy {
            try { LocalDateTime.parse(it.eventAt) } catch (e: Exception) { LocalDateTime.MAX }
        }.take(5)

        sorted.forEach { activity ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_profile_activity, container, false)

            val iconInfo = iconMap[activity.sport.iconKey] ?: Pair(R.drawable.ic_soccer, "#22C55E")
            card.findViewById<ImageView>(R.id.ivSportIcon).apply {
                setImageResource(iconInfo.first)
                setColorFilter(Color.parseColor(iconInfo.second))
            }
            card.findViewById<FrameLayout>(R.id.sportIconContainer).background =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorWithAlpha(iconInfo.second, 0x22))
                }

            card.findViewById<TextView>(R.id.tvActivitySport).text = activity.sport.name
            card.findViewById<TextView>(R.id.tvActivityDate).text = formatActivityDate(activity.eventAt)

            val badge = card.findViewById<TextView>(R.id.tvActivityBadge)
            val isPast = isEventPast(activity.eventAt)
            when {
                activity.status == "CANCELLED" -> {
                    badge.text = "CANCELADA"
                    badge.setTextColor(Color.parseColor("#DC2626"))
                }
                isPast -> {
                    badge.text = "FINALIZADO"
                    badge.setTextColor(Color.parseColor("#888888"))
                }
                else -> {
                    badge.text = "PROXIMO"
                    badge.setTextColor(resources.getColor(R.color.dc_green, theme))
                }
            }

            card.setOnClickListener {
                val intent = Intent(this, MatchDetailActivity::class.java)
                intent.putExtra("ACTIVITY_ID", activity.id)
                intent.putExtra("MATCH_ID", activity.id)
                intent.putExtra("SPORT_TAG", activity.sport.name.uppercase())
                intent.putExtra("LOCATION", activity.location.name)
                startActivity(intent)
            }

            container.addView(card)
        }
    }

    private fun calculateAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 0
        return try {
            val birth = LocalDate.parse(birthDate)
            Period.between(birth, LocalDate.now()).years
        } catch (e: Exception) {
            0
        }
    }

    private fun formatMemberSince(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val dt = LocalDateTime.parse(iso.substringBefore('.').removeSuffix("Z"))
            val mes = dt.format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es")))
            val anio = dt.year
            "Miembro desde ${mes.replaceFirstChar { it.uppercase() }} $anio"
        } catch (e: Exception) {
            "—"
        }
    }

    private fun formatActivityDate(iso: String): String {
        return try {
            val dt = LocalDateTime.parse(iso)
            val date = dt.toLocalDate()
            val today = LocalDate.now()
            val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            when {
                date == today -> "Hoy, $time"
                date == today.plusDays(1) -> "Manana, $time"
                else -> dt.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.forLanguageTag("es")))
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

    private fun colorWithAlpha(hexColor: String, alpha: Int): Int {
        val base = Color.parseColor(hexColor)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun showAvatarOptionsDialog() {
        val options = arrayOf(
            "Tomar foto",
            "Elegir de galeria",
            "Seleccionar avatar de color"
        )

        AlertDialog.Builder(this)
            .setTitle("Cambiar avatar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraOrLaunch()
                    1 -> launchGallery()
                    2 -> startActivity(Intent(this, AvatarPickerActivity::class.java))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestCameraOrLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: cacheDir
            val photoFile = File.createTempFile("DC_${timestamp}_", ".jpg", storageDir)
            Log.d(TAG, "Saving photo at: ${photoFile.absolutePath}")

            photoUri = FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", photoFile
            )
            Log.d(TAG, "photoUri: $photoUri")

            takePictureLauncher.launch(photoUri!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir camara", e)
            Toast.makeText(this, "No se pudo abrir la camara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error decodificando bitmap de $uri", e)
        null
    }

    private fun processBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Procesando bitmap: ${bitmap.width}x${bitmap.height}")

        val resized = resize(bitmap, 250)
        val base64 = bitmapToBase64(resized, 50)
        Log.d(TAG, "Base64 length: ${base64.length}")

        if (base64.isBlank()) {
            Toast.makeText(this, "Error: imagen vacia", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Subiendo foto (${base64.length / 1024}KB)...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = userRepository.updateAvatar(base64)) {
                is Resource.Success -> {
                    Log.d(TAG, "Avatar guardado, length=${result.data.avatarUrl?.length}")
                    Toast.makeText(this@ProfileActivity, "Foto actualizada", Toast.LENGTH_SHORT).show()
                    UserCache.setProfile(result.data)
                    renderUser(result.data)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Error: ${result.message}")
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun resize(source: Bitmap, maxSize: Int): Bitmap {
        val w = source.width; val h = source.height
        if (w <= maxSize && h <= maxSize) return source
        val ratio = w.toFloat() / h
        val (newW, newH) = if (w > h) maxSize to (maxSize / ratio).toInt()
        else (maxSize * ratio).toInt() to maxSize
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 50): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
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
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}