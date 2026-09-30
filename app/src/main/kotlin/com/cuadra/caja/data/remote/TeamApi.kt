package com.cuadra.caja.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Gestión del equipo: personas y teléfonos. Solo en línea. Van con las credenciales de siempre (`Device` + `X-Member-Id`):
 * el servidor decide qué puede hacer cada rol (`MemberService`, `DeviceService`) y responde con un código estable.
 */
interface TeamApi {
    @POST("api/b/{businessId}/members") suspend fun createMember(@Path("businessId") businessId: String, @Body body: CreateMemberBody): MemberDto
    @PUT("api/b/{businessId}/members/{memberId}") suspend fun updateMember(@Path("businessId") businessId: String, @Path("memberId") memberId: String, @Body body: UpdateMemberBody): MemberDto
    @PUT("api/b/{businessId}/members/{memberId}/pin") suspend fun resetPin(@Path("businessId") businessId: String, @Path("memberId") memberId: String, @Body body: PinBody)

    @POST("api/b/{businessId}/access-code") suspend fun renewAccessCode(@Path("businessId") businessId: String): AccessCodeDto
    @PUT("api/b/{businessId}/access-code") suspend fun setAccessCode(@Path("businessId") businessId: String, @Body body: SetAccessCodeBody): AccessCodeDto

    @GET("api/b/{businessId}/devices") suspend fun devices(@Path("businessId") businessId: String): List<DeviceDto>
    @DELETE("api/b/{businessId}/devices/{deviceId}") suspend fun revokeDevice(@Path("businessId") businessId: String, @Path("deviceId") deviceId: String)
}
