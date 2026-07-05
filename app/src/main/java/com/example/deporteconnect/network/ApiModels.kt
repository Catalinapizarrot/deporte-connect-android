package com.example.deporteconnect.network

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

// ─── AUTH ─────────────────────────────────────────────
data class RegisterRequest(val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)

data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val fullName: String?,
    val profileComplete: Boolean
)

data class ForgotPasswordRequest(val email: String)
data class ForgotPasswordResponse(
    val ok: Boolean,
    val exists: Boolean,
    val message: String
)
data class ResetPasswordRequest(val email: String, val newPassword: String)
data class GenericResponse(val ok: Boolean, val message: String)

// ─── USER ─────────────────────────────────────────────
data class UpdateProfileRequest(
    val fullName: String,
    val phone: String,
    val birthDate: String,
    val gender: String,
    val interestIds: Set<Long>,
    val avatarUrl: String? = null,
    val bio: String? = null
)

data class UserResponse(
    val id: Long,
    val email: String,
    val fullName: String?,
    val phone: String?,
    val birthDate: String?,
    val gender: String?,
    val avatarUrl: String?,
    val bio: String?,
    val skillLevel: String?,
    val profileComplete: Boolean?,
    val interests: List<SportResponse>?,
    val createdAt: String?,            // ⭐ NUEVO: para "Miembro desde"
    val participantRating: Double?,    // ⭐ NUEVO: rating de participante
    val participantRatingCount: Int?   // ⭐ NUEVO: cantidad de reseñas
)

data class UpdateAvatarRequest(val avatarUrl: String)

// ─── SPORT ────────────────────────────────────────────
data class SportResponse(
    val id: Long,
    val name: String,
    @SerializedName("iconKey") val iconKey: String?,
    val color: String?
)

// ─── LOCATION ─────────────────────────────────────────
data class LocationResponse(
    val id: Long,
    val name: String,
    val address: String?,
    val commune: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?
)

// ─── ACTIVITY ─────────────────────────────────────────
data class CreateActivityRequest(
    val title: String,
    val sportId: Long,
    val locationId: Long,
    val eventAt: String,
    val maxParticipants: Int,
    val price: BigDecimal? = null,
    val level: String,
    val gender: String,
    val requiresReservation: Boolean = false,
    val description: String? = null
)

data class ActivityResponse(
    val id: Long,
    val title: String,
    val sport: SportResponse,
    val location: LocationResponse,
    val organizerId: Long,
    val organizerName: String?,
    val organizerVerified: Boolean?,
    val organizerRating: Double?,
    val organizerRatingCount: Int?,
    val eventAt: String,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val price: BigDecimal?,
    val level: String?,
    val gender: String?,
    val requiresReservation: Boolean?,
    val description: String?,
    val status: String?
)

data class CreateActivityReportRequest(
    val reason: String,
    val description: String? = null
)

data class CreateOrganizerRatingRequest(
    val stars: Int
)

// ─── MESSAGE ──────────────────────────────────────────
data class MessageSender(
    val id: Long,
    val fullName: String?,
    val avatarUrl: String?
)

data class MessageResponse(
    val id: Long,
    val activityId: Long,
    val sender: MessageSender?,
    val content: String,
    val type: String?,
    val sentAt: String
)

// ─── ERROR ────────────────────────────────────────────
data class ApiErrorResponse(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null
)
