package com.cuadra.caja.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CuadraApi {
    @POST("api/auth/google") suspend fun google(@Body body: GoogleLoginBody): LoginDto
    @POST("api/auth/logout") suspend fun logout()
    @GET("api/me") suspend fun me(): MeDto
    @GET("api/config") suspend fun config(): ConfigDto

    @POST("api/businesses") suspend fun createBusiness(@Body body: CreateBusinessBody): BusinessDto
    @PUT("api/b/{businessId}") suspend fun updateBusiness(@Path("businessId") businessId: String, @Body body: UpdateBusinessBody): BusinessDto
    @POST("api/b/{businessId}/devices/self") suspend fun selfLink(@Path("businessId") businessId: String, @Body body: LinkInfoBody): SelfLinkedDto

    @POST("api/devices/link-requests") suspend fun createLinkRequest(@Body body: LinkInfoBody): LinkRequestCreatedDto
    @GET("api/devices/link-requests/{code}") suspend fun pollLink(@Path("code") code: String, @Header("X-Poll-Secret") secret: String): LinkStatusDto

    @GET("api/b/{businessId}/members") suspend fun members(@Path("businessId") businessId: String): List<MemberDto>
    @PUT("api/b/{businessId}/members/{memberId}/pin") suspend fun setPin(@Path("businessId") businessId: String, @Path("memberId") memberId: String, @Body body: PinBody)

    @POST("api/b/{businessId}/sync/push") suspend fun push(@Path("businessId") businessId: String, @Body body: PushBody): PushResponse
    @GET("api/b/{businessId}/sync/pull") suspend fun pull(@Path("businessId") businessId: String, @Query("since") since: Long, @Query("limit") limit: Int = 200): PullResponse

    @POST("api/b/{businessId}/sales/{saleId}/lock") suspend fun lockSale(@Path("businessId") businessId: String, @Path("saleId") saleId: String): SaleDto
    @DELETE("api/b/{businessId}/sales/{saleId}/lock") suspend fun unlockSale(@Path("businessId") businessId: String, @Path("saleId") saleId: String)

    @GET("api/b/{businessId}/products/barcode/{code}") suspend fun productByBarcode(@Path("businessId") businessId: String, @Path("code") code: String): ProductDto

    // ---------- notificaciones (solo en línea: preferencias y programaciones) ----------
    @GET("api/b/{businessId}/notification-preferences") suspend fun notificationPreferences(@Path("businessId") businessId: String): Map<String, Boolean>
    @PUT("api/b/{businessId}/notification-preferences") suspend fun setNotificationPreference(@Path("businessId") businessId: String, @Body body: PreferenceBody): Map<String, Boolean>
    @GET("api/b/{businessId}/notification-settings") suspend fun notificationSettings(@Path("businessId") businessId: String): NotificationSettingsDto
    @PUT("api/b/{businessId}/notification-settings") suspend fun updateNotificationSettings(@Path("businessId") businessId: String, @Body body: NotificationSettingsDto): NotificationSettingsDto
    @GET("api/b/{businessId}/notification-schedules") suspend fun schedules(@Path("businessId") businessId: String): List<ScheduleDto>
    @PUT("api/b/{businessId}/notification-schedules/{id}") suspend fun saveSchedule(@Path("businessId") businessId: String, @Path("id") id: String, @Body body: ScheduleInputDto): ScheduleDto
    @POST("api/b/{businessId}/notification-schedules/{id}/active") suspend fun setScheduleActive(@Path("businessId") businessId: String, @Path("id") id: String, @Body body: ActiveBody): ScheduleDto
    @POST("api/b/{businessId}/notification-schedules/{id}/send-now") suspend fun sendScheduleNow(@Path("businessId") businessId: String, @Path("id") id: String, @Body body: ScheduleInputDto): ScheduleDto
    @DELETE("api/b/{businessId}/notification-schedules/{id}") suspend fun deleteSchedule(@Path("businessId") businessId: String, @Path("id") id: String)
    @GET("api/b/{businessId}/notification-schedules/{id}/runs") suspend fun scheduleRuns(@Path("businessId") businessId: String, @Path("id") id: String): List<ScheduleRunDto>
    @PUT("api/b/{businessId}/push-tokens") suspend fun registerPushToken(@Path("businessId") businessId: String, @Body body: PushTokenBody)
}
