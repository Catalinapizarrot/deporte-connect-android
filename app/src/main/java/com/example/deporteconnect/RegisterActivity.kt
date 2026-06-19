package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.AuthRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.util.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * Pantalla de Registro.
 *
 * Tras crear la cuenta:
 *  - Limpia la sesión automática (queremos que haga login por primera vez)
 *  - Muestra "Cuenta creada exitosamente" durante 2 segundos
 *  - Redirige al Login con el email pre-llenado
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var tvGoToLogin: TextView

    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        authRepository = AuthRepository(this)
        sessionManager = SessionManager.getInstance(this)

        initViews()
        setListeners()
    }

    private fun initViews() {
        ivBack             = findViewById(R.id.ivBack)
        tilEmail           = findViewById(R.id.tilEmail)
        tilPassword        = findViewById(R.id.tilPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etEmail            = findViewById(R.id.etEmail)
        etPassword         = findViewById(R.id.etPassword)
        etConfirmPassword  = findViewById(R.id.etConfirmPassword)
        btnCreateAccount   = findViewById(R.id.btnCreateAccount)
        tvGoToLogin        = findViewById(R.id.tvGoToLogin)
    }

    private fun setListeners() {
        ivBack.setOnClickListener { finish() }

        btnCreateAccount.setOnClickListener {
            if (validateForm()) performRegister()
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirm  = etConfirmPassword.text.toString().trim()

        when {
            email.isEmpty() -> { tilEmail.error = "Ingresa tu correo"; isValid = false }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { tilEmail.error = "Correo no válido"; isValid = false }
            else -> tilEmail.error = null
        }

        when {
            password.isEmpty() -> { tilPassword.error = "Ingresa una contraseña"; isValid = false }
            password.length < 6 -> { tilPassword.error = "Mínimo 6 caracteres"; isValid = false }
            else -> tilPassword.error = null
        }

        when {
            confirm.isEmpty() -> { tilConfirmPassword.error = "Confirma tu contraseña"; isValid = false }
            confirm != password -> { tilConfirmPassword.error = "Las contraseñas no coinciden"; isValid = false }
            else -> tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun performRegister() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        btnCreateAccount.isEnabled = false
        btnCreateAccount.text = "Creando cuenta..."

        lifecycleScope.launch {
            val result = authRepository.register(email, password)

            when (result) {
                is Resource.Success -> {
                    // Limpiar sesión automática (queremos login manual)
                    sessionManager.clearSession()

                    // Toast LARGO (5 seg en Android) para que se alcance a leer
                    Toast.makeText(
                        this@RegisterActivity,
                        "🎉  Cuenta creada exitosamente",
                        Toast.LENGTH_LONG
                    ).show()

                    // Cambiar el texto del botón también, así el usuario lo ve
                    btnCreateAccount.text = "✓ Cuenta creada"

                    // Esperar 2 segundos antes de navegar al Login
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                        intent.putExtra("PREFILL_EMAIL", email)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }, 2000)
                }
                is Resource.Error -> {
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                    resetButton()
                }
                else -> resetButton()
            }
        }
    }

    private fun resetButton() {
        btnCreateAccount.isEnabled = true
        btnCreateAccount.text = "Crear Cuenta  →"
    }
}