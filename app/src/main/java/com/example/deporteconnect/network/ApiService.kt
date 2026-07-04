package com.example.deporteconnect.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── AUTH ─────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<GenericResponse>

    // ─── USERS ────────────────────────────────────────────────
    @GET("usuarios/me")
    suspend fun getMyProfile(): Response<UserResponse>

    @PUT("usuarios/me")
    suspend fun updateMyProfile(@Body request: UpdateProfileRequest): Response<UserResponse>

    @PUT("usuarios/me/avatar")
    suspend fun updateAvatar(@Body request: UpdateAvatarRequest): Response<UserResponse>

    @GET("usuarios/{id}")
    suspend fun getUserById(@Path("id") id: Long): Response<UserResponse>

    // ─── SPORTS ───────────────────────────────────────────────
    @GET("deportes")
    suspend fun getSports(): Response<List<SportResponse>>

    // ─── LOCATIONS ────────────────────────────────────────────
    @GET("ubicaciones")
    suspend fun getLocations(): Response<List<LocationResponse>>

    // ─── ACTIVITIES ───────────────────────────────────────────
    @GET("actividades")
    suspend fun getActivities(): Response<List<ActivityResponse>>

    @GET("actividades/mis-partidos")
    suspend fun getMyMatches(): Response<List<ActivityResponse>>

    @GET("actividades/{id}")
    suspend fun getActivityById(@Path("id") id: Long): Response<ActivityResponse>

    @POST("actividades")
    suspend fun createActivity(@Body request: CreateActivityRequest): Response<ActivityResponse>

    @POST("actividades/{id}/unirse")
    suspend fun joinActivity(@Path("id") id: Long): Response<ActivityResponse>

    @HTTP(method = "DELETE", path = "actividades/{id}/salirse", hasBody = false)
    suspend fun leaveActivity(@Path("id") id: Long): Response<Unit>

    @DELETE("actividades/{id}")
    suspend fun cancelActivity(@Path("id") id: Long): Response<Unit>

    @POST("actividades/{id}/reportes")
    suspend fun reportActivity(
        @Path("id") id: Long,
        @Body request: CreateActivityReportRequest
    ): Response<Unit>

    // ─── MESSAGES ─────────────────────────────────────────────
    @GET("actividades/{activityId}/mensajes")
    suspend fun getMessages(@Path("activityId") activityId: Long): Response<List<MessageResponse>>

    @POST("actividades/{activityId}/mensajes")
    suspend fun sendMessage(
        @Path("activityId") activityId: Long,
        @Body request: SendMessageRequest
    ): Response<MessageResponse>
}

data class SendMessageRequest(val content: String)
