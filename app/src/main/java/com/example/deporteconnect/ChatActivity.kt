package com.example.deporteconnect

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.example.deporteconnect.data.MessageRepository
import com.example.deporteconnect.data.Resource
import com.example.deporteconnect.network.MessageResponse
import com.example.deporteconnect.util.SessionManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Chat de coordinación de una actividad.
 * Conectado a GET y POST /actividades/{id}/mensajes.
 */
class ChatActivity : AppCompatActivity() {

    sealed class ChatItem {
        data class DaySeparator(val label: String) : ChatItem()
        data class OtherMessage(
            val senderName: String,
            val content: String,
            val avatarColor: String
        ) : ChatItem()
        data class MyMessage(val content: String) : ChatItem()
        data class SystemMessage(val title: String, val content: String) : ChatItem()
    }

    private lateinit var messageRepository: MessageRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var container: LinearLayout
    private lateinit var scrollView: NestedScrollView

    private var activityId: Long = 0L
    private var currentUserId: Long = 0L

    /** Paleta de colores fijos por usuario para los avatares */
    private val avatarColors = listOf(
        "#F87171", "#60A5FA", "#34D399", "#FBBF24",
        "#A78BFA", "#FB7185", "#22D3EE", "#FB923C"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        container  = findViewById(R.id.containerMessages)
        scrollView = findViewById(R.id.scrollMessages)

        // Recibir el id de la actividad (compatible con CHAT_ID viejo)
        activityId = intent.getLongExtra("ACTIVITY_ID", 0L)
        if (activityId == 0L) activityId = intent.getIntExtra("CHAT_ID", 0).toLong()
        if (activityId == 0L) {
            Toast.makeText(this, "Chat no encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Mi userId desde el SessionManager
        sessionManager = SessionManager.getInstance(this)
        currentUserId = sessionManager.getUserId()

        messageRepository = MessageRepository(this)

        // Título del chat
        val title = intent.getStringExtra("CHAT_TITLE") ?: "Chat"
        findViewById<TextView>(R.id.tvChatTitle).text = title

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        setupSend()
    }

    override fun onResume() {
        super.onResume()
        loadMessages()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            when (val result = messageRepository.getMessages(activityId)) {
                is Resource.Success -> renderBackendMessages(result.data)
                is Resource.Error -> {
                    Toast.makeText(
                        this@ChatActivity,
                        "Error al cargar mensajes: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmpty()
                }
                else -> {}
            }
        }
    }

    private fun setupSend() {
        val etMessage = findViewById<TextInputEditText>(R.id.etMessage)
        val btnSend = findViewById<FloatingActionButton>(R.id.btnSend)

        btnSend.setOnClickListener {
            val text = etMessage.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) return@setOnClickListener

            btnSend.isEnabled = false
            etMessage.isEnabled = false

            lifecycleScope.launch {
                when (val result = messageRepository.sendMessage(activityId, text)) {
                    is Resource.Success -> {
                        etMessage.setText("")
                        loadMessages()
                    }
                    is Resource.Error -> {
                        Toast.makeText(
                            this@ChatActivity,
                            "No se pudo enviar: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {}
                }
                btnSend.isEnabled = true
                etMessage.isEnabled = true
            }
        }
    }

    /** Convierte la lista del backend en ChatItems con separadores por día */
    private fun renderBackendMessages(messages: List<MessageResponse>) {
        if (messages.isEmpty()) {
            showEmpty()
            return
        }

        val items = mutableListOf<ChatItem>()
        var lastDayLabel: String? = null

        for (msg in messages) {
            val dayLabel = formatDayLabel(msg.sentAt)
            if (dayLabel != lastDayLabel) {
                items.add(ChatItem.DaySeparator(dayLabel))
                lastDayLabel = dayLabel
            }

            // Mensaje del sistema
            if (msg.type?.equals("SYSTEM", ignoreCase = true) == true) {
                items.add(ChatItem.SystemMessage("INFO", msg.content))
                continue
            }

            // ¿Es mi mensaje?
            val senderId = msg.sender?.id ?: 0L
            if (senderId == currentUserId) {
                items.add(ChatItem.MyMessage(msg.content))
            } else {
                val senderName = msg.sender?.fullName ?: "Usuario"
                val color = avatarColors[(senderId % avatarColors.size).toInt()]
                items.add(ChatItem.OtherMessage(senderName, msg.content, color))
            }
        }

        renderItems(items)
        scrollToBottom()
    }

    private fun renderItems(items: List<ChatItem>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        items.forEach { item ->
            val view = when (item) {
                is ChatItem.DaySeparator -> {
                    val v = inflater.inflate(R.layout.item_chat_day_separator, container, false)
                    v.findViewById<TextView>(R.id.tvDaySeparator).text = item.label
                    v
                }
                is ChatItem.OtherMessage -> {
                    val v = inflater.inflate(R.layout.item_chat_msg_other, container, false)
                    v.findViewById<TextView>(R.id.tvMsgSender).text = item.senderName
                    v.findViewById<TextView>(R.id.tvMsgContent).text = item.content

                    val avatar = v.findViewById<View>(R.id.msgAvatar)
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.parseColor(item.avatarColor))
                    }
                    avatar.background = bg
                    v
                }
                is ChatItem.MyMessage -> {
                    val v = inflater.inflate(R.layout.item_chat_msg_mine, container, false)
                    v.findViewById<TextView>(R.id.tvMsgContent).text = item.content
                    v
                }
                is ChatItem.SystemMessage -> {
                    val v = inflater.inflate(R.layout.item_chat_msg_system, container, false)
                    v.findViewById<TextView>(R.id.tvSystemTitle).text = item.title
                    v.findViewById<TextView>(R.id.tvSystemContent).text = item.content
                    v
                }
            }
            container.addView(view)
        }
    }

    private fun showEmpty() {
        container.removeAllViews()
        val tv = TextView(this).apply {
            text = "No hay mensajes aún.\n¡Sé el primero en escribir!"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = android.view.Gravity.CENTER
            setPadding(40, 80, 40, 40)
        }
        container.addView(tv)
    }

    private fun formatDayLabel(iso: String?): String {
        if (iso.isNullOrBlank()) return "HOY"
        return try {
            val cleaned = iso.removeSuffix("Z").substringBefore('.')
            val dt = LocalDateTime.parse(cleaned)
            val today = LocalDate.now()
            when {
                dt.toLocalDate() == today -> "HOY"
                dt.toLocalDate() == today.minusDays(1) -> "AYER"
                else -> dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("es")))
                    .uppercase()
            }
        } catch (e: Exception) {
            "HOY"
        }
    }

    private fun scrollToBottom() {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}