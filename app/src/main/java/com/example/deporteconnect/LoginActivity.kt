package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
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

class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvRegister: TextView

    private lateinit var authRepository: AuthRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        authRepository = AuthRepository(this)
        sessionManager = SessionManager.getInstance(this)

        // Si ya hay sesión activa, decidir a dónde ir
        if (sessionManager.isLoggedIn()) {
            if (sessionManager.isProfileComplete()) {
                navigateToLoading(fromOnboarding = false)
            } else {
                navigateToWelcome()
            }
            return
        }

        initViews()
        setListeners()

        // Pre-rellenar email si viene desde el Registro
        intent.getStringExtra("PREFILL_EMAIL")?.let {
            etEmail.setText(it)
            etPassword.requestFocus()
        }
    }

    private fun initViews() {
        tilEmail         = findViewById(R.id.tilEmail)
        tilPassword      = findViewById(R.id.tilPassword)
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvRegister       = findViewById(R.id.tvRegister)
    }

    private fun setListeners() {
        btnLogin.setOnClickListener {
            if (validateForm()) performLogin()
        }
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        when {
            email.isEmpty() -> { tilEmail.error = "Ingresa tu correo"; isValid = false }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { tilEmail.error = "Correo no válido"; isValid = false }
            else -> tilEmail.error = null
        }

        when {
            password.isEmpty() -> { tilPassword.error = "Ingresa tu contraseña"; isValid = false }
            password.length < 6 -> { tilPassword.error = "Mínimo 6 caracteres"; isValid = false }
            else -> tilPassword.error = null
        }

        return isValid
    }

    private fun performLogin() {
        val email    = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        btnLogin.isEnabled = false
        btnLogin.text = "Ingresando..."

        lifecycleScope.launch {
            val result = authRepository.login(email, password)

            when (result) {
                is Resource.Success -> {
                    val data = result.data

                    if (data.profileComplete) {
                        Toast.makeText(
                            this@LoginActivity,
                            "¡Bienvenido de vuelta!",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToLoading(fromOnboarding = false)
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Configuremos tu perfil",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToWelcome()
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                    resetButton()
                }
                else -> resetButton()
            }
        }
    }

    private fun navigateToLoading(fromOnboarding: Boolean) {
        val intent = Intent(this, LoadingActivity::class.java)
        intent.putExtra("FROM_ONBOARDING", fromOnboarding)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun navigateToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun resetButton() {
        btnLogin.isEnabled = true
        btnLogin.text = "Iniciar Sesión  →"
    }
}
