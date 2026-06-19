package com.example.deporteconnect.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * Helper para renderizar avatares en toda la app.
 *
 * El campo `avatarUrl` del backend puede tener 3 formatos:
 *   - "color:#22C55E"  → avatar de color sólido con iniciales
 *   - base64 plain     → foto de la galería o cámara
 *   - null / vacío     → avatar default (gris con iniciales)
 */
object AvatarHelper {

    val AVATAR_COLORS = listOf(
        "#22C55E", "#2563EB", "#7C3AED", "#EF4444", "#F59E0B",
        "#06B6D4", "#EC4899", "#84CC16", "#6366F1", "#64748B"
    )

    fun isColorAvatar(avatarUrl: String?): Boolean {
        return avatarUrl?.startsWith("color:") == true
    }

    fun encodeColorAvatar(hexColor: String): String = "color:$hexColor"

    fun extractColor(avatarUrl: String?): String? {
        return if (isColorAvatar(avatarUrl)) avatarUrl?.removePrefix("color:") else null
    }

    /**
     * Decodifica un base64 a Bitmap. Retorna null si falla.
     *
     * IMPORTANTE: se decodifica con Base64.DEFAULT (sin URL_SAFE) porque
     * la foto se codifica con NO_WRAP (alfabeto estándar + y /). Usar
     * URL_SAFE aquí corrompía los bytes y rompía el decoder de imágenes.
     */
    fun decodeBase64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            // Quitar prefijo data:image/...;base64, si viniera
            val pureBase64 = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun getInitials(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "?"
        val parts = fullName.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts[0].length >= 2 -> parts[0].substring(0, 2).uppercase()
            else -> parts[0].uppercase()
        }
    }

    fun applyAvatar(
        avatarUrl: String?,
        fullName: String?,
        ivImage: ImageView?,
        tvInitials: TextView?,
        bgView: View
    ) {
        when {
            isColorAvatar(avatarUrl) -> {
                val color = extractColor(avatarUrl) ?: "#64748B"
                applyColorBg(bgView, color)
                ivImage?.visibility = View.GONE
                tvInitials?.visibility = View.VISIBLE
                tvInitials?.text = getInitials(fullName)
            }

            !avatarUrl.isNullOrBlank() -> {
                val bitmap = decodeBase64ToBitmap(avatarUrl)
                if (bitmap != null) {
                    ivImage?.visibility = View.VISIBLE
                    ivImage?.setImageBitmap(bitmap)
                    tvInitials?.visibility = View.GONE
                    applyColorBg(bgView, "#E5E7EB")
                } else {
                    showFallback(fullName, ivImage, tvInitials, bgView)
                }
            }

            else -> showFallback(fullName, ivImage, tvInitials, bgView)
        }
    }

    private fun showFallback(
        fullName: String?,
        ivImage: ImageView?,
        tvInitials: TextView?,
        bgView: View
    ) {
        val colorIdx = (fullName?.hashCode()?.let { Math.abs(it) } ?: 0) % AVATAR_COLORS.size
        applyColorBg(bgView, AVATAR_COLORS[colorIdx])
        ivImage?.visibility = View.GONE
        tvInitials?.visibility = View.VISIBLE
        tvInitials?.text = getInitials(fullName)
    }

    private fun applyColorBg(view: View, hexColor: String) {
        try {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hexColor))
            }
            view.background = bg
        } catch (_: Exception) {
            view.setBackgroundColor(Color.parseColor("#64748B"))
        }
    }
}
