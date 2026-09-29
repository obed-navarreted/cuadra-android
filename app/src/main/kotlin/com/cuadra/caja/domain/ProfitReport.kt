package com.cuadra.caja.domain

/**
 * Resumen de ganancia. MISMA fórmula que el servidor (`ReportService`) y que el panel web, para que los números coincidan en todas partes:
 *   ganancia = ventas − costo de lo vendido − gastos operativos
 * · ventas: ventas cobradas (con el descuento de la cuenta ya aplicado);
 * · costo de lo vendido: cantidad × el costo que tenía CADA línea al venderse (una línea sin costo no resta nada);
 * · gastos operativos: gastos no anulados SIN las compras de mercadería (pagos a proveedor y categoría "Mercadería"), que ya están en el costo;
 * · los retiros de caja no son gastos.
 */
data class ProfitLines(val costOfGoodsMinor: Long, val costedRevenueMinor: Long, val lineRevenueMinor: Long)

data class ExpenseSplit(val operatingMinor: Long, val purchasesMinor: Long)

data class Profit(
    val salesMinor: Long, val costOfGoodsMinor: Long, val operatingExpensesMinor: Long, val purchasesExcludedMinor: Long, val estimatedProfitMinor: Long,
    /** Qué parte de lo vendido tenía costo anotado (0–100). Debajo de 100 la ganancia está sobrestimada. */
    val costCoveragePercent: Int,
)

object ProfitReport {
    fun compute(salesMinor: Long, lines: ProfitLines, expenses: ExpenseSplit): Profit {
        val coverage = if (lines.lineRevenueMinor == 0L) 100 else Math.round(100.0 * lines.costedRevenueMinor / lines.lineRevenueMinor).toInt()
        return Profit(salesMinor, lines.costOfGoodsMinor, expenses.operatingMinor, expenses.purchasesMinor, salesMinor - lines.costOfGoodsMinor - expenses.operatingMinor, coverage)
    }
}
