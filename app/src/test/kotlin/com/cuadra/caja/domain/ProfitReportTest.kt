package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** El escenario de `ReportTest` (servidor): los mismos números deben salir aquí. */
class ProfitReportTest {
    @Test fun theServerScenarioGivesTheSameProfit() {
        val p = ProfitReport.compute(53000, ProfitLines(30000, 50000, 54000), ExpenseSplit(10000, 4000))
        assertEquals(13000L, p.estimatedProfitMinor)
        assertEquals(93, p.costCoveragePercent)
        assertEquals(4000L, p.purchasesExcludedMinor)
    }

    @Test fun noSalesMeansFullCoverageAndNoProfit() {
        val p = ProfitReport.compute(0, ProfitLines(0, 0, 0), ExpenseSplit(0, 0))
        assertEquals(100, p.costCoveragePercent)
        assertEquals(0L, p.estimatedProfitMinor)
    }

    @Test fun expensesBiggerThanTheMarginGiveANegativeProfit() {
        assertEquals(-3000L, ProfitReport.compute(10000, ProfitLines(6000, 10000, 10000), ExpenseSplit(7000, 0)).estimatedProfitMinor)
    }

    @Test fun coverageRoundsToTheNearestPercent() {
        assertEquals(67, ProfitReport.compute(300, ProfitLines(0, 200, 300), ExpenseSplit(0, 0)).costCoveragePercent)
    }
}
