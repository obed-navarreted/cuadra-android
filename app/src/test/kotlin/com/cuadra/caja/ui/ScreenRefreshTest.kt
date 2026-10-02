package com.cuadra.caja.ui

import com.cuadra.caja.domain.RefreshThrottle
import com.cuadra.caja.domain.RefreshTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las listas se refrescan cada vez que se abren (decisión del propietario): al abrir, al volver la app al frente, al cambiar de persona, al deslizar y (por
 * cobrar en caja) cada 15 s; con antirrebote para que toques rápidos no dupliquen llamadas, y sin conexión sin errores.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenRefreshTest {
    private var clock = 0L

    private fun TestScope.refresher(result: RefreshResult = RefreshResult.DONE, gate: CompletableDeferred<Unit>? = null, calls: MutableList<RefreshTrigger>) =
        ScreenRefresh(this, RefreshThrottle(2_000L) { clock }) { t -> calls += t; gate?.await(); result }

    @Test fun openingTheScreenRefreshesAndFastRepeatsAreIgnored() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<RefreshTrigger>()
        val r = refresher(calls = calls)
        assertTrue(r.request(RefreshTrigger.SHOWN))
        advanceUntilIdle()
        // Volver al frente 1 s después: no se repite la llamada.
        clock += 1_000
        assertFalse(r.request(RefreshTrigger.RESUMED))
        // Más tarde sí.
        clock += 2_000
        assertTrue(r.request(RefreshTrigger.RESUMED))
        advanceUntilIdle()
        assertEquals(listOf(RefreshTrigger.SHOWN, RefreshTrigger.RESUMED), calls)
    }

    @Test fun neverTwoAtOnceAndTheIndicatorShowsWhileRunning() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<RefreshTrigger>()
        val gate = CompletableDeferred<Unit>()
        val r = refresher(gate = gate, calls = calls)
        assertTrue(r.request(RefreshTrigger.SHOWN))
        runCurrent()
        assertTrue(r.refreshing.value)
        // Deslizar mientras ya refresca: se descarta (no hay dos llamadas a la vez).
        clock += 10_000
        assertFalse(r.request(RefreshTrigger.PULLED))
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(r.refreshing.value)
        assertEquals(1, calls.size)
    }

    @Test fun pullingOrChangingThePersonRefreshesEvenRightAfterAnother() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<RefreshTrigger>()
        val r = refresher(calls = calls)
        r.request(RefreshTrigger.SHOWN); advanceUntilIdle()
        clock += 100
        assertTrue(r.request(RefreshTrigger.PULLED)); advanceUntilIdle()
        clock += 100
        assertTrue(r.request(RefreshTrigger.PERSON_CHANGED)); advanceUntilIdle()
        // El refresco periódico sí respeta la pausa.
        clock += 100
        assertFalse(r.request(RefreshTrigger.PERIODIC))
        clock += 15_000
        assertTrue(r.request(RefreshTrigger.PERIODIC)); advanceUntilIdle()
        assertEquals(listOf(RefreshTrigger.SHOWN, RefreshTrigger.PULLED, RefreshTrigger.PERSON_CHANGED, RefreshTrigger.PERIODIC), calls)
    }

    @Test fun offlineIsAFlagNotAnErrorAndAFailureNeverSticksTheIndicator() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<RefreshTrigger>()
        val offline = refresher(RefreshResult.OFFLINE, calls = calls)
        offline.request(RefreshTrigger.SHOWN); advanceUntilIdle()
        assertTrue(offline.offline.value)
        assertFalse(offline.refreshing.value)
        // Un fallo inesperado del trabajo no deja el indicador puesto ni bloquea el siguiente refresco.
        var fail = true
        val flaky = ScreenRefresh(this, RefreshThrottle(0L) { clock }) { if (fail) error("boom") else RefreshResult.DONE }
        flaky.request(RefreshTrigger.SHOWN); advanceUntilIdle()
        assertFalse(flaky.refreshing.value)
        fail = false
        assertTrue(flaky.request(RefreshTrigger.PULLED)); advanceUntilIdle()
        assertFalse(flaky.offline.value)
    }
}
