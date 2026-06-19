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
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.deporteconnect.util.AvatarHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilAge: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etAge: TextInputEditText
    private lateinit var btnNext: MaterialButton
    private lateinit var fabCamera: FrameLayout
    private lateinit var ivAvatar: ImageView
    private lateinit var btnGenderMale: MaterialButton
    private lateinit var btnGenderFemale: MaterialButton
    private lateinit var tvGenderError: TextView

    private var selectedGender: String? = null
    private var photoUri: Uri? = null

    /** Avatar elegido. Puede ser "color:#XXXXXX", base64, o null */
    private var selectedAvatar: String? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri?.let { uri -> processBitmap(loadBitmapFromUri(uri)) }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processBitmap(loadBitmapFromUri(uri)) }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)
        initViews()
        setListeners()
    }

    private fun initViews() {
        tilFullName     = findViewById(R.id.tilFullName)
        tilPhone        = findViewById(R.id.tilPhone)
        tilAge          = findViewById(R.id.tilAge)
        etFullName      = findViewById(R.id.etFullName)
        etPhone         = findViewById(R.id.etPhone)
        etAge           = findViewById(R.id.etAge)
        btnNext         = findViewById(R.id.btnNextToInterests)
        fabCamera       = findViewById(R.id.fabCamera)
        ivAvatar        = findViewById(R.id.ivAvatar)
        btnGenderMale   = findViewById(R.id.btnGenderMale)
        btnGenderFemale = findViewById(R.id.btnGenderFemale)
        tvGenderError   = findViewById(R.id.tvGenderError)
    }

    private fun setListeners() {
        fabCamera.setOnClickListener { showPhotoPicker() }
        ivAvatar.setOnClickListener { showPhotoPicker() }

        btnNext.setOnClickListener {
            if (validateForm()) goToInterests()
        }
        btnGenderMale.setOnClickListener { selectGender("HOMBRE") }
        btnGenderFemale.setOnClickListener { selectGender("MUJER") }
    }

    private fun selectGender(gender: String) {
        selectedGender = gender
        tvGenderError.visibility = View.GONE

        if (gender == "HOMBRE") {
            btnGenderMale.setBackgroundColor(Color.parseColor("#16A34A"))
            btnGenderMale.setTextColor(Color.WHITE)
            btnGenderMale.strokeWidth = 0

            btnGenderFemale.setBackgroundColor(Color.TRANSPARENT)
            btnGenderFemale.setTextColor(Color.parseColor("#666666"))
            btnGenderFemale.strokeWidth = 2
        } else {
            btnGenderFemale.setBackgroundColor(Color.parseColor("#16A34A"))
            btnGenderFemale.setTextColor(Color.WHITE)
            btnGenderFemale.strokeWidth = 0

            btnGenderMale.setBackgroundColor(Color.TRANSPARENT)
            btnGenderMale.setTextColor(Color.parseColor("#666666"))
            btnGenderMale.strokeWidth = 2
        }
    }

    // ─── Picker de foto: 3 opciones ────────────────

    private fun showPhotoPicker() {
        val options = arrayOf(
            "📷  Tomar foto",
            "🖼️  Elegir de galería",
            "🎨  Seleccionar avatar de color"
        )

        AlertDialog.Builder(this)
            .setTitle("Foto de perfil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraOrLaunch()
                    1 -> launchGallery()
                    2 -> showColorPickerDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Muestra un diálogo bonito con grilla 5x2 de colores (mismo estilo que AvatarPickerActivity)
     * pero como diálogo modal porque aún no estamos registrados (no hay backend al cual subir).
     */
    private fun showColorPickerDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_color_picker, null)

        val grid = dialogView.findViewById<GridLayout>(R.id.colorPickerGrid)
        val preview = dialogView.findViewById<View>(R.id.colorPickerPreviewBg)
        val tvPreviewInitials = dialogView.findViewById<TextView>(R.id.tvColorPreviewInitials)

        // Mostrar iniciales del nombre actual (o "?" si no escribió)
        val initials = AvatarHelper.getInitials(etFullName.text?.toString())
        tvPreviewInitials.text = initials

        var chosenColor = AvatarHelper.AVATAR_COLORS[0]
        val ringViews = mutableMapOf<String, View>()

        val density = resources.displayMetrics.density
        val cellSize = (52 * density).toInt()
        val margin = (6 * density).toInt()

        // Setup preview inicial
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(chosenColor))
        }

        AvatarHelper.AVATAR_COLORS.forEach { hex ->
            val cell = FrameLayout(this).apply {
                val params = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = params
            }

            val circle = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(cellSize, cellSize)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
            }
            cell.addView(circle)

            val ring = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(cellSize, cellSize)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((3 * density).toInt(), Color.parseColor("#111111"))
                }
                visibility = if (hex == chosenColor) View.VISIBLE else View.GONE
            }
            cell.addView(ring)
            ringViews[hex] = ring

            cell.setOnClickListener {
                chosenColor = hex
                ringViews.forEach { (h, r) -> r.visibility = if (h == chosenColor) View.VISIBLE else View.GONE }
                preview.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(chosenColor))
                }
            }

            grid.addView(cell)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Elegir") { _, _ ->
                selectedAvatar = AvatarHelper.encodeColorAvatar(chosenColor)
                applyColorToAvatar(chosenColor)
                Toast.makeText(this, "✓ Avatar de color seleccionado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyColorToAvatar(hex: String) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
        ivAvatar.background = bg
        ivAvatar.setImageDrawable(null)
        ivAvatar.setPadding(0, 0, 0, 0)
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
            val photoFile = File.createTempFile("DC_${timestamp}_", ".jpg", storageDir)

            photoUri = FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", photoFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun processBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
            return
        }
        // Resize agresivo: 250px max, calidad 50% → ~5-15KB → caben en BDD sin problema
        val resized = resize(bitmap, 250)
        ivAvatar.setPadding(0, 0, 0, 0)
        ivAvatar.background = null
        ivAvatar.setImageBitmap(resized)

        val base64 = bitmapToBase64(resized, 50)
        selectedAvatar = base64

        Toast.makeText(this, "✓ Foto seleccionada", Toast.LENGTH_SHORT).show()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

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

    // ─── Validación y navegación ───────────────────────────

    private fun validateForm(): Boolean {
        var isValid = true
        val name  = etFullName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val age   = etAge.text.toString().trim()

        when {
            name.isEmpty() -> { tilFullName.error = "Ingresa tu nombre"; isValid = false }
            name.length < 3 -> { tilFullName.error = "Nombre muy corto"; isValid = false }
            else -> tilFullName.error = null
        }

        when {
            phone.isEmpty() -> { tilPhone.error = "Ingresa tu teléfono"; isValid = false }
            phone.length < 8 -> { tilPhone.error = "Teléfono inválido"; isValid = false }
            else -> tilPhone.error = null
        }

        val ageNum = age.toIntOrNull()
        when {
            age.isEmpty() -> { tilAge.error = "Ingresa tu edad"; isValid = false }
            ageNum == null -> { tilAge.error = "Edad inválida"; isValid = false }
            ageNum < 18 -> { tilAge.error = "Debes ser mayor de 18 años"; isValid = false }
            ageNum > 120 -> { tilAge.error = "Edad inválida"; isValid = false }
            else -> tilAge.error = null
        }

        if (selectedGender == null) {
            tvGenderError.text = "Selecciona tu género"
            tvGenderError.visibility = View.VISIBLE
            isValid = false
        } else {
            tvGenderError.visibility = View.GONE
        }

        return isValid
    }

    private fun goToInterests() {
        val name = etFullName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val age = etAge.text.toString().trim().toInt()

        val birthDate = LocalDate.now().minusYears(age.toLong())
        val birthDateStr = birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val intent = Intent(this, InterestsSelectionActivity::class.java)
        intent.putExtra("FULL_NAME", name)
        intent.putExtra("PHONE", phone)
        intent.putExtra("AGE", age)
        intent.putExtra("BIRTH_DATE", birthDateStr)
        intent.putExtra("GENDER", selectedGender)
        intent.putExtra("AVATAR_URL", selectedAvatar)
        startActivity(intent)
    }
}
