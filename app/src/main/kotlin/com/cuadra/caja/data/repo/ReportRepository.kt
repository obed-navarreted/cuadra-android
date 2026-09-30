package com.cuadra.caja.data.repo

import com.cuadra.caja.data.remote.CuadraApi
import com.cuadra.caja.data.remote.DailyCloseDto
import com.cuadra.caja.data.remote.PageDto
import com.cuadra.caja.data.remote.SaleDto
import com.cuadra.caja.data.remote.SalesReportDto
import com.cuadra.caja.data.remote.apiCall
import com.cuadra.caja.data.session.SessionStore
import java.time.LocalDate

/**
 * Lecturas de gestión que SOLO existen en el servidor (lista completa de ventas, totales por periodo, cierre del día). Necesitan conexión: sin ella
 * devuelven `ApiFailure.Offline` y la pantalla lo dice con un mensaje amable. Las fechas son jornadas del negocio (el servidor aplica su zona y corte).
 */
class ReportRepository(private val api: CuadraApi, private val session: SessionStore) {
    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        val b = session.current().businessId ?: return Result.failure(IllegalStateException("no business"))
        return apiCall { block(b) }
    }

    suspend fun sales(from: LocalDate, to: LocalDate, status: String, method: String?, memberId: String?, page: Int, size: Int = PAGE): Result<PageDto<SaleDto>> =
        call { api.sales(it, status, from.toString(), to.toString(), method, memberId, page, size) }

    suspend fun salesReport(from: LocalDate, to: LocalDate): Result<SalesReportDto> = call { api.salesReport(it, from.toString(), to.toString()) }

    suspend fun dailyClose(from: LocalDate, to: LocalDate): Result<DailyCloseDto> = call { api.dailyClose(it, from.toString(), to.toString()) }

    companion object { const val PAGE = 100 }
}
