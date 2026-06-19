package com.example.deporteconnect

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.network.UserResponse
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de "Editar perfil".
 *
 * En esta fase (B.1) los campos son SOLO de visualización (pintados,
 * no editables). Muestra: nombre completo, teléfono, edad, género.
 * Los campos vacíos (ej. segundo nombre/apellido no rellenados) no se muestran.
 *
 * La edición real de deportes se hace desde "Mis deportes" (bloque B.2).
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        userRepository = UserRepository(this)

        findViewById<ImageView>(R.id.ivBackEdit).setOnClickListener { finish() }

        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            when (val result = userRepository.getMyProfile()) {
                is Resource.Success -> renderProfile(result.data)
                is Resource.Error -> {
                    Toast.makeText(
                        this@EditProfileActivity,
                        "No se pudo cargar el perfil: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {}
            }
        }
    }

    private fun renderProfile(user: UserResponse) {
        // Nombre completo
        setField(R.id.rowName, R.id.tvFieldName, user.fullName)

        // Email (informativo)
        setField(R.id.rowEmail, R.id.tvFieldEmail, user.email)

        // Teléfono
        setField(R.id.rowPhone, R.id.tvFieldPhone, user.phone)

        // Edad
        val age = calculateAge(user.birthDate)
        setField(R.id.rowAge, R.id.tvFieldAge, if (age > 0) "$age años" else null)

        // Fecha de nacimiento (formateada)
        setField(R.id.rowBirth, R.id.tvFieldBirth, formatBirth(user.birthDate))

        // Género
        val genderText = when (user.gender) {
            "HOMBRE" -> "Hombre"
            "MUJER" -> "Mujer"
            else -> null
        }
        setField(R.id.rowGender, R.id.tvFieldGender, genderText)
    }

    /**
     * Rellena un campo. Si el valor está vacío/null, oculta toda la fila
     * (así no se muestran campos sin completar, como pediste).
     */
    private fun setField(rowId: Int, valueId: Int, value: String?) {
        val row = findViewById<LinearLayout>(rowId)
        val tv = findViewById<TextView>(valueId)
        if (value.isNullOrBlank()) {
            row.visibility = View.GONE
        } else {
            row.visibility = View.VISIBLE
            tv.text = value
        }
    }

    private fun calculateAge(birthDate: String?): Int {
        if (birthDate.isNullOrBlank()) return 0
        return try {
            Period.between(LocalDate.parse(birthDate), LocalDate.now()).years
        } catch (e: Exception) {
            0
        }
    }

    private fun formatBirth(birthDate: String?): String? {
        if (birthDate.isNullOrBlank()) return null
        return try {
            val d = LocalDate.parse(birthDate)
            d.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))
        } catch (e: Exception) {
            null
        }
    }
}
