package com.example.deporteconnect

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.AuthRepository
import com.example.deporteconnect.data.Resource
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var tilConfirmNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmNewPassword: TextInputEditText
    private lateinit var btnUpdatePassword: MaterialButton

    private lateinit var authRepository: AuthRepository
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
        if (userEmail.isBlank()) {
            Toast.makeText(this, "Email no recibido. Vuelve a intentar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        authRepository = AuthRepository(this)

        initViews()
        setListeners()
    }

    private fun initViews() {
        ivBack                  = findViewById(R.id.ivBack)
        tilNewPassword          = findViewById(R.id.tilNewPassword)
        tilConfirmNewPassword   = findViewById(R.id.tilConfirmNewPassword)
        etNewPassword           = findViewById(R.id.etNewPassword)
        etConfirmNewPassword    = findViewById(R.id.etConfirmNewPassword)
        btnUpdatePassword       = findViewById(R.id.btnUpdatePassword)
    }

    private fun setListeners() {
        ivBack.setOnClickListener { finish() }

        btnUpdatePassword.setOnClickListener {
            if (validatePasswords()) performUpdate()
        }
    }

    private fun validatePasswords(): Boolean {
        var isValid = true
        val newPass     = etNewPassword.text.toString().trim()
        val confirmPass = etConfirmNewPassword.text.toString().trim()

        when {
            newPass.isEmpty() -> {
                tilNewPassword.error = "Ingresa una contraseña"; isValid = false
            }
            newPass.length < 6 -> {
                tilNewPassword.error = "Mínimo 6 caracteres"; isValid = false
            }
            else -> tilNewPassword.error = null
        }

        when {
            confirmPass.isEmpty() -> {
                tilConfirmNewPassword.error = "Confirma tu contraseña"; isValid = false
            }
            confirmPass != newPass -> {
                tilConfirmNewPassword.error = "Las contraseñas no coinciden"; isValid = false
            }
            else -> tilConfirmNewPassword.error = null
        }

        return isValid
    }

    private fun performUpdate() {
        val newPassword = etNewPassword.text.toString().trim()

        btnUpdatePassword.isEnabled = false
        btnUpdatePassword.text = "Actualizando..."

        lifecycleScope.launch {
            when (val result = authRepository.resetPassword(userEmail, newPassword)) {
                is Resource.Success -> {
                    navigateToSuccess()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnUpdatePassword.isEnabled = true
                    btnUpdatePassword.text = "Actualizar contraseña"
                }
                else -> {
                    btnUpdatePassword.isEnabled = true
                    btnUpdatePassword.text = "Actualizar contraseña"
                }
            }
        }
    }

    private fun navigateToSuccess() {
        val intent = Intent(this, PasswordSuccessActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
