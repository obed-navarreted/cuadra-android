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
    /** Con el teléfono ya vinculado la petición iría con el token del teléfono: `api/me` es de la cuenta de Google, así que se manda su sesión. */
    @GET("api/me") suspend fun meAs(@retrofit2.http.Header("Authorization") authorization: String): MeDto
    @GET("api/config") suspend fun config(): ConfigDto
    @GET("api/config/countries") suspend fun countries(): List<CountryDto>

    @POST("api/businesses") suspend fun createBusiness(@Body body: CreateBusinessBody): BusinessDto
    @GET("api/b/{businessId}") suspend fun business(@Path("businessId") businessId: String): BusinessDto
    @DELETE("api/b/{businessId}") suspend fun deleteBusiness(@Path("businessId") businessId: String)
    /** Eliminar mi cuenta (dueño con Google; antes debe eliminar sus negocios: OWNS_BUSINESSES). */
    @DELETE("api/me") suspend fun deleteMe(@retrofit2.http.Header("Authorization") authorization: String)
    @GET("api/b/{businessId}/plan") suspend fun plan(@Path("businessId") businessId: String): PlanDto
    @GET("api/b/{businessId}/activity") suspend fun activity(@Path("businessId") businessId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 30): PageDto<ActivityEntryDto>
    @POST("api/support/tickets") suspend fun createTicket(@Body body: TicketBody): TicketCreatedDto
    @GET("api/b/{businessId}/message-templates") suspend fun messageTemplates(@Path("businessId") businessId: String): List<TemplateDto>
    @PUT("api/b/{businessId}/message-templates/{kind}/{locale}") suspend fun saveMessageTemplate(@Path("businessId") businessId: String, @Path("kind") kind: String, @Path("locale") locale: String, @Body body: TemplateTextBody): TemplateDto
    @DELETE("api/b/{businessId}/message-templates/{kind}/{locale}") suspend fun resetMessageTemplate(@Path("businessId") businessId: String, @Path("kind") kind: String, @Path("locale") locale: String)
    @PUT("api/b/{businessId}") suspend fun updateBusiness(@Path("businessId") businessId: String, @Body body: UpdateBusinessBody): BusinessDto
    @POST("api/b/{businessId}/devices/self") suspend fun selfLink(@Path("businessId") businessId: String, @Body body: LinkInfoBody): SelfLinkedDto

    /** Entrada del equipo (ADR 0012): código del negocio + usuario + PIN. Sin sesión previa. */
    @POST("api/auth/member-login") suspend fun memberLogin(@Body body: MemberLoginBody): MemberLoginResult

    @GET("api/b/{businessId}/members") suspend fun members(@Path("businessId") businessId: String): List<MemberDto>
    /** Lo mismo con credenciales explícitas: el listado con `Device` trae el hash del PIN (para validarlo sin conexión); con la sesión de Google, no. */
    @GET("api/b/{businessId}/members") suspend fun membersAs(@Path("businessId") businessId: String, @Header("Authorization") authorization: String): List<MemberDto>
    /** Siempre como TELÉFONO (`Device <token>`): con la sesión de Google aún puesta, el interceptor mandaría la de Google. */
    @GET("api/devices/me") suspend fun deviceSelf(@Header("Authorization") authorization: String): DeviceSelfDto
    @POST("api/devices/me/members/{memberId}/verify-pin") suspend fun verifyPin(@Path("memberId") memberId: String, @Body body: VerifyPinBody, @Header("Authorization") authorization: String): VerifiedPinDto
    @PUT("api/b/{businessId}/members/{memberId}/pin") suspend fun setPin(@Path("businessId") businessId: String, @Path("memberId") memberId: String, @Body body: PinBody)

    /** `member`: solo cuando no hay persona activa (pantalla de PIN, acceso desactivado): envía como quien hizo las operaciones. Nulo = la de la sesión. */
    @POST("api/b/{businessId}/sync/push") suspend fun push(@Path("businessId") businessId: String, @Body body: PushBody, @retrofit2.http.Header("X-Member-Id") member: String? = null): PushResponse
    @GET("api/b/{businessId}/sync/pull") suspend fun pull(@Path("businessId") businessId: String, @Query("since") since: Long, @Query("limit") limit: Int = 200, @Query("pendingOps") pendingOps: Int? = null): PullResponse

    @GET("api/b/{businessId}/sales") suspend fun sales(
        @Path("businessId") businessId: String, @Query("status") status: String?, @Query("from") from: String, @Query("to") to: String,
        @Query("method") method: String? = null, @Query("byMember") byMember: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 100,
    ): PageDto<SaleDto>
    @GET("api/b/{businessId}/reports/sales") suspend fun salesReport(@Path("businessId") businessId: String, @Query("from") from: String, @Query("to") to: String): SalesReportDto
    @GET("api/b/{businessId}/reports/daily-close") suspend fun dailyClose(@Path("businessId") businessId: String, @Query("from") from: String, @Query("to") to: String): DailyCloseDto

    @POST("api/b/{businessId}/sales/{saleId}/lock") suspend fun lockSale(@Path("businessId") businessId: String, @Path("saleId") saleId: String): SaleDto
    @DELETE("api/b/{businessId}/sales/{saleId}/lock") suspend fun unlockSale(@Path("businessId") businessId: String, @Path("saleId") saleId: String)

    @GET("api/b/{businessId}/products/barcode/{code}") suspend fun productByBarcode(@Path("businessId") businessId: String, @Path("code") code: String): ProductDto
    @GET("api/b/{businessId}/products/{productId}/history") suspend fun productHistory(@Path("businessId") businessId: String, @Path("productId") productId: String): List<ProductHistoryEntryDto>

    // ---------- notificaciones (solo en línea: preferencias y programaciones) ----------
    @PUT("api/b/{businessId}/expense-categories/{categoryId}") suspend fun saveExpenseCategory(@Path("businessId") businessId: String, @Path("categoryId") categoryId: String, @Body body: CategoryInputDto): ExpenseCategoryDto

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
