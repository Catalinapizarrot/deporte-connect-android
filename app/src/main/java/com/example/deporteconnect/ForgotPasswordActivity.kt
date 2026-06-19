package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.AuthRepository
import com.example.deporteconnect.data.Resource
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilEmailForgot: TextInputLayout
    private lateinit var etEmailForgot: TextInputEditText
    private lateinit var btnSendReset: MaterialButton
    private lateinit var tvBackToLogin: TextView

    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        authRepository = AuthRepository(this)
        initViews()
        setListeners()
    }

    private fun initViews() {
        tilEmailForgot = findViewById(R.id.tilEmailForgot)
        etEmailForgot  = findViewById(R.id.etEmailForgot)
        btnSendReset   = findViewById(R.id.btnSendReset)
        tvBackToLogin  = findViewById(R.id.tvBackToLogin)
    }

    private fun setListeners() {
        tvBackToLogin.setOnClickListener { finish() }

        btnSendReset.setOnClickListener {
            val email = etEmailForgot.text.toString().trim()
            when {
                email.isEmpty() ->
                    tilEmailForgot.error = "Ingresa tu correo"
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                    tilEmailForgot.error = "Correo no válido"
                else -> {
                    tilEmailForgot.error = null
                    verifyEmail(email)
                }
            }
        }
    }

    /**
     * Verifica que el email exista en el backend antes de pasar a cambiar contraseña.
     */
    private fun verifyEmail(email: String) {
        btnSendReset.isEnabled = false
        btnSendReset.text = "Verificando..."

        lifecycleScope.launch {
            when (val result = authRepository.forgotPassword(email)) {
                is Resource.Success -> {
                    val data = result.data
                    if (data.exists) {
                        // Email registrado → pasar a cambiar contraseña
                        navigateToResetPassword(email)
                    } else {
                        // Email no existe en el sistema
                        tilEmailForgot.error = "Este correo no está registrado"
                        resetButton()
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    resetButton()
                }
                else -> resetButton()
            }
        }
    }

    private fun resetButton() {
        btnSendReset.isEnabled = true
        btnSendReset.text = "Continuar  →"
    }

    private fun navigateToResetPassword(email: String) {
        val intent = Intent(this, ResetPasswordActivity::class.java)
        intent.putExtra("USER_EMAIL", email)
        startActivity(intent)
    }
}
