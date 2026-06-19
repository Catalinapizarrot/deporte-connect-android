package com.example.deporteconnect

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.data.UserRepository
import com.example.deporteconnect.util.AvatarHelper
import com.example.deporteconnect.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class AvatarPickerActivity : AppCompatActivity() {

    private lateinit var userRepository: UserRepository
    private lateinit var sessionManager: SessionManager

    private var selectedColor: String = AvatarHelper.AVATAR_COLORS[0]
    private val colorViews = mutableMapOf<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_picker)

        userRepository = UserRepository(this)
        sessionManager = SessionManager.getInstance(this)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        renderColorGrid()
        updatePreview()

        findViewById<MaterialButton>(R.id.btnSaveAvatar).setOnClickListener {
            saveAvatar()
        }
    }

    private fun renderColorGrid() {
        val grid = findViewById<GridLayout>(R.id.colorGrid)
        grid.removeAllViews()
        grid.columnCount = 5
        grid.rowCount = 2

        val density = resources.displayMetrics.density
        val cellSize = (60 * density).toInt()
        val margin = (8 * density).toInt()

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
                val lp = FrameLayout.LayoutParams(cellSize, cellSize)
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
            }
            cell.addView(circle)

            val ring = View(this).apply {
                val lp = FrameLayout.LayoutParams(cellSize, cellSize)
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke((3 * density).toInt(), Color.parseColor("#111111"))
                }
                visibility = View.GONE
            }
            cell.addView(ring)

            cell.setOnClickListener {
                selectedColor = hex
                updateRings()
                updatePreview()
            }

            colorViews[hex] = ring
            grid.addView(cell)
        }

        updateRings()
    }

    private fun updateRings() {
        colorViews.forEach { (hex, ring) ->
            ring.visibility = if (hex == selectedColor) View.VISIBLE else View.GONE
        }
    }

    private fun updatePreview() {
        val bg = findViewById<View>(R.id.avatarPreviewBg)
        bg.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(selectedColor))
        }

        val tvInitials = findViewById<TextView>(R.id.tvAvatarPreviewInitials)
        tvInitials.text = AvatarHelper.getInitials(sessionManager.getUserName())
    }

    private fun saveAvatar() {
        val btn = findViewById<MaterialButton>(R.id.btnSaveAvatar)
        btn.isEnabled = false
        btn.text = "Guardando..."

        val avatarValue = AvatarHelper.encodeColorAvatar(selectedColor)

        lifecycleScope.launch {
            when (val result = userRepository.updateAvatar(avatarValue)) {
                is Resource.Success -> {
                    Toast.makeText(
                        this@AvatarPickerActivity,
                        "✓ Avatar actualizado",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(
                        this@AvatarPickerActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btn.isEnabled = true
                    btn.text = "Guardar avatar"
                }
                else -> {
                    btn.isEnabled = true
                    btn.text = "Guardar avatar"
                }
            }
        }
    }
}