package com.example.deporteconnect.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper para tomar foto con la cámara o seleccionar de la galería.
 *
 * Uso desde una Activity:
 *
 *   private lateinit var photoPicker: PhotoPickerHelper
 *
 *   override fun onCreate(...) {
 *       photoPicker = PhotoPickerHelper(this) { bitmap, base64 ->
 *           // bitmap: la imagen lista para mostrar en ImageView
 *           // base64: string con la imagen codificada (para enviar al backend)
 *           ivAvatar.setImageBitmap(bitmap)
 *           // sessionManager.saveAvatar(base64)  // si quieres persistirla
 *       }
 *
 *       btnFoto.setOnClickListener { photoPicker.show() }
 *   }
 */
class PhotoPickerHelper(
    private val activity: AppCompatActivity,
    private val onPhotoSelected: (Bitmap, String) -> Unit
) {

    private var photoUri: Uri? = null

    private val cameraLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                photoUri?.let { uri ->
                    val bitmap = loadBitmapFromUri(uri)
                    bitmap?.let {
                        val resized = resize(it, 600)
                        onPhotoSelected(resized, bitmapToBase64(resized))
                    }
                }
            }
        }

    private val galleryLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val bitmap = loadBitmapFromUri(uri)
                    bitmap?.let {
                        val resized = resize(it, 600)
                        onPhotoSelected(resized, bitmapToBase64(resized))
                    }
                }
            }
        }

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
        }

    /** Muestra el diálogo Cámara / Galería */
    fun show() {
        AlertDialog.Builder(activity)
            .setTitle("Foto de perfil")
            .setItems(arrayOf("📷  Tomar foto", "🖼️  Elegir de galería")) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionAndLaunch()
                    1 -> launchGallery()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                activity,
                "No se pudo abrir la cámara: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("DC_${timestamp}_", ".jpg", storageDir)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            activity.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Redimensiona la foto manteniendo proporciones */
    private fun resize(source: Bitmap, maxSize: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxSize && h <= maxSize) return source
        val ratio = w.toFloat() / h
        val (newW, newH) = if (w > h) maxSize to (maxSize / ratio).toInt()
                           else (maxSize * ratio).toInt() to maxSize
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /** Convierte el bitmap a Base64 para guardar/enviar al backend */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    companion object {
        /** Convierte un base64 guardado de vuelta a bitmap */
        fun base64ToBitmap(base64: String?): Bitmap? {
            if (base64.isNullOrBlank()) return null
            return try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        /** Guarda el avatar en SharedPreferences */
        fun saveAvatarLocal(context: Context, base64: String) {
            context.getSharedPreferences("deporte_connect_session", Context.MODE_PRIVATE)
                .edit()
                .putString("avatar_base64", base64)
                .apply()
        }

        /** Recupera el avatar desde SharedPreferences */
        fun getAvatarLocal(context: Context): String? {
            return context.getSharedPreferences("deporte_connect_session", Context.MODE_PRIVATE)
                .getString("avatar_base64", null)
        }
    }
}
