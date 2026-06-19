package com.example.deporteconnect

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.ActivityRepository
import com.example.deporteconnect.data.LocationRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.SportRepository
import com.example.deporteconnect.network.CreateActivityRequest
import com.example.deporteconnect.network.LocationResponse
import com.example.deporteconnect.network.SportResponse
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

class CreateActivityActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etMaxParticipants: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var tvSport: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var cardSport: MaterialCardView
    private lateinit var cardLocation: MaterialCardView
    private lateinit var cardDate: MaterialCardView
    private lateinit var cardTime: MaterialCardView
    private lateinit var btnPublicMen: MaterialButton
    private lateinit var btnPublicMixed: MaterialButton
    private lateinit var btnPublicWomen: MaterialButton
    private lateinit var btnLevelBeginner: MaterialButton
    private lateinit var btnLevelIntermediate: MaterialButton
    private lateinit var btnLevelPro: MaterialButton
    private lateinit var btnPublish: MaterialButton

    private var sports: List<SportResponse> = emptyList()
    private var locations: List<LocationResponse> = emptyList()
    private var selectedSport: SportResponse? = null
    private var selectedLocation: LocationResponse? = null
    private val selectedDateTime = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 19)
        set(Calendar.MINUTE, 0)
    }
    private var dateChosen = false
    private var timeChosen = false
    private var selectedPublic: String = "MIXTO"
    private var selectedLevel: String = "INTERMEDIO"

    private lateinit var sportRepository: SportRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var activityRepository: ActivityRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_activity)

        sportRepository = SportRepository(this)
        locationRepository = LocationRepository(this)
        activityRepository = ActivityRepository(this)

        initViews()
        setListeners()
        loadCatalogs()
        selectPublic("MIXTO")
        selectLevel("INTERMEDIO")
    }

    private fun initViews() {
        ivBack            = findViewById(R.id.ivBack)
        etTitle           = findViewById(R.id.etTitle)
        etMaxParticipants = findViewById(R.id.etMaxParticipants)
        etPrice           = findViewById(R.id.etPrice)
        etDescription     = findViewById(R.id.etDescription)
        tvSport           = findViewById(R.id.tvSport)
        tvLocation        = findViewById(R.id.tvLocation)
        tvDate            = findViewById(R.id.tvDate)
        tvTime            = findViewById(R.id.tvTime)
        cardSport         = findViewById(R.id.cardSport)
        cardLocation      = findViewById(R.id.cardLocation)
        cardDate          = findViewById(R.id.cardDate)
        cardTime          = findViewById(R.id.cardTime)
        btnPublicMen      = findViewById(R.id.btnPublicMen)
        btnPublicMixed    = findViewById(R.id.btnPublicMixed)
        btnPublicWomen    = findViewById(R.id.btnPublicWomen)
        btnLevelBeginner  = findViewById(R.id.btnLevelBeginner)
        btnLevelIntermediate = findViewById(R.id.btnLevelIntermediate)
        btnLevelPro       = findViewById(R.id.btnLevelPro)
        btnPublish        = findViewById(R.id.btnPublish)
    }

    private fun setListeners() {
        ivBack.setOnClickListener { finish() }
        cardSport.setOnClickListener { showSportPicker() }
        cardLocation.setOnClickListener { showLocationPicker() }
        cardDate.setOnClickListener { showDatePicker() }
        cardTime.setOnClickListener { showTimePicker() }
        btnPublicMen.setOnClickListener { selectPublic("HOMBRES") }
        btnPublicMixed.setOnClickListener { selectPublic("MIXTO") }
        btnPublicWomen.setOnClickListener { selectPublic("MUJERES") }
        btnLevelBeginner.setOnClickListener { selectLevel("PRINCIPIANTE") }
        btnLevelIntermediate.setOnClickListener { selectLevel("INTERMEDIO") }
        btnLevelPro.setOnClickListener { selectLevel("PROFESIONAL") }
        btnPublish.setOnClickListener { publish() }
    }

    private fun loadCatalogs() {
        lifecycleScope.launch {
            when (val r = sportRepository.getAllSports()) {
                is Resource.Success -> sports = r.data
                is Resource.Error -> Toast.makeText(
                    this@CreateActivityActivity,
                    "Error cargando deportes: ${r.message}",
                    Toast.LENGTH_SHORT
                ).show()
                else -> {}
            }
            when (val r = locationRepository.getAll()) {
                is Resource.Success -> locations = r.data
                is Resource.Error -> Toast.makeText(
                    this@CreateActivityActivity,
                    "Error cargando ubicaciones: ${r.message}",
                    Toast.LENGTH_SHORT
                ).show()
                else -> {}
            }
        }
    }

    private fun showSportPicker() {
        if (sports.isEmpty()) {
            Toast.makeText(this, "Cargando deportes...", Toast.LENGTH_SHORT).show()
            return
        }
        val items = sports.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecciona un deporte")
            .setItems(items) { _, which ->
                selectedSport = sports[which]
                tvSport.text = selectedSport!!.name
                tvSport.setTextColor(Color.parseColor("#111111"))
            }
            .show()
    }

    private fun showLocationPicker() {
        if (locations.isEmpty()) {
            Toast.makeText(this, "Cargando ubicaciones...", Toast.LENGTH_SHORT).show()
            return
        }
        val items = locations.map { "${it.name}${if (it.commune != null) " (${it.commune})" else ""}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecciona una ubicación")
            .setItems(items) { _, which ->
                selectedLocation = locations[which]
                tvLocation.text = selectedLocation!!.name
                tvLocation.setTextColor(Color.parseColor("#111111"))
            }
            .show()
    }

    private fun showDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, day)
                dateChosen = true
                val fmt = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                tvDate.text = fmt.format(selectedDateTime.time)
                tvDate.setTextColor(Color.parseColor("#111111"))
            },
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH),
            selectedDateTime.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = today.timeInMillis
        }.show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hour)
                selectedDateTime.set(Calendar.MINUTE, minute)
                timeChosen = true
                tvTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                tvTime.setTextColor(Color.parseColor("#111111"))
            },
            selectedDateTime.get(Calendar.HOUR_OF_DAY),
            selectedDateTime.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun selectPublic(value: String) {
        selectedPublic = value
        styleToggle(btnPublicMen, value == "HOMBRES")
        styleToggle(btnPublicMixed, value == "MIXTO")
        styleToggle(btnPublicWomen, value == "MUJERES")
    }

    private fun selectLevel(value: String) {
        selectedLevel = value
        styleToggle(btnLevelBeginner, value == "PRINCIPIANTE")
        styleToggle(btnLevelIntermediate, value == "INTERMEDIO")
        styleToggle(btnLevelPro, value == "PROFESIONAL")
    }

    private fun styleToggle(btn: MaterialButton, selected: Boolean) {
        if (selected) {
            btn.setBackgroundColor(Color.parseColor("#16A34A"))
            btn.setTextColor(Color.WHITE)
            btn.strokeWidth = 0
        } else {
            btn.setBackgroundColor(Color.TRANSPARENT)
            btn.setTextColor(Color.parseColor("#666666"))
            btn.strokeWidth = 2
        }
    }

    private fun publish() {
        val title = etTitle.text?.toString()?.trim() ?: ""
        if (title.length < 3) {
            Toast.makeText(this, "Ingresa un título descriptivo", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedSport == null) {
            Toast.makeText(this, "Selecciona un deporte", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedLocation == null) {
            Toast.makeText(this, "Selecciona una ubicación", Toast.LENGTH_SHORT).show()
            return
        }
        if (!dateChosen || !timeChosen) {
            Toast.makeText(this, "Selecciona fecha y hora", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDateTime.before(Calendar.getInstance())) {
            Toast.makeText(this, "La fecha debe ser futura", Toast.LENGTH_SHORT).show()
            return
        }
        val maxStr = etMaxParticipants.text?.toString()?.trim() ?: ""
        val maxParticipants = maxStr.toIntOrNull()
        if (maxParticipants == null || maxParticipants < 2 || maxParticipants > 100) {
            Toast.makeText(this, "Cupo entre 2 y 100", Toast.LENGTH_SHORT).show()
            return
        }
        val priceStr = etPrice.text?.toString()?.trim()
        val price = if (priceStr.isNullOrBlank()) BigDecimal.ZERO
        else priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

        val description = etDescription.text?.toString()?.trim()

        val eventAtIso = LocalDateTime.of(
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH) + 1,
            selectedDateTime.get(Calendar.DAY_OF_MONTH),
            selectedDateTime.get(Calendar.HOUR_OF_DAY),
            selectedDateTime.get(Calendar.MINUTE)
        ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val request = CreateActivityRequest(
            title = title,
            sportId = selectedSport!!.id,
            locationId = selectedLocation!!.id,
            eventAt = eventAtIso,
            maxParticipants = maxParticipants,
            price = price,
            level = selectedLevel,
            gender = selectedPublic,
            requiresReservation = false,
            description = description
        )

        btnPublish.isEnabled = false
        btnPublish.text = "Publicando..."

        lifecycleScope.launch {
            when (val r = activityRepository.create(request)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@CreateActivityActivity,
                        "✓ Actividad publicada",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@CreateActivityActivity,
                        "Error: ${r.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnPublish.isEnabled = true
                    btnPublish.text = "Publicar Actividad"
                }
                else -> {}
            }
        }
    }
}
