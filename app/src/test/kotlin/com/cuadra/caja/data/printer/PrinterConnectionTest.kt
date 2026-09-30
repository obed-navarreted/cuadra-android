package com.cuadra.caja.data.printer

import com.cuadra.caja.domain.printing.Backoff
import com.cuadra.caja.domain.printing.ConnIssue
import com.cuadra.caja.domain.printing.ConnState
import com.cuadra.caja.domain.printing.PrintOutcome
import com.cuadra.caja.domain.printing.PrinterBadge
import com.cuadra.caja.domain.printing.PrinterSettings
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Impresora simulada que apunta CUÁNDO (tiempo virtual) se intentó conectar y puede fallar al conectar o al escribir. */
private class ScriptedTransport(private val scope: TestScope, var connectFailures: Int = 0, var writeFailures: Int = 0) : PrinterTransport {
    val connectTimes = mutableListOf<Long>()
    val jobs = mutableListOf<ByteArray>()
    var connected = false
    var alive = true
    var closes = 0
    var btOff = false

    override suspend fun connect(timeoutMs: Long) {
        connectTimes += scope.currentTime
        if (btOff) throw TransportException(ConnIssue.BLUETOOTH_OFF)
        if (connectFailures > 0) { connectFailures--; throw TransportException(ConnIssue.NOT_FOUND) }
        connected = true
    }

    override suspend fun write(bytes: ByteArray) {
        if (writeFailures > 0) { writeFailures--; connected = false; throw IOException("roto") }
        if (!connected) throw IOException("sin enlace")
        jobs += bytes
    }

    override suspend fun isAlive() = connected && alive
    override fun close() { connected = false; closes++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterConnectionTest {
    private val on = PrinterSettings(enabled = true, deviceKey = "AA:BB")
    private val job = byteArrayOf(1, 2, 3)

    @Test fun backoffIsTwoFourEightSixteenThenThirtySeconds() {
        assertEquals(listOf(2_000L, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), (1..7).map { Backoff.delayMs(it) })
        assertEquals(2_000L, Backoff.delayMs(0))
    }

    @Test fun retriesWithGrowingWaitsAndConnectsWhenThePrinterAppears() = runTest {
        val t = ScriptedTransport(this, connectFailures = 5)
        val settings = MutableStateFlow(on)
        val conn = PrinterConnection(backgroundScope, settings, { t })
        runCurrent()
        conn.setForeground(true)
        runCurrent()
        assertEquals(ConnState.ERROR, conn.state.value)
        advanceTimeBy(60_000); runCurrent()
        // Intentos en 0, 2, 6, 14, 30 s (esperas 2, 4, 8, 16) y el sexto, a los 60 s (tope de 30 s), ya conecta.
        assertEquals(listOf(0L, 2_000, 6_000, 14_000, 30_000, 60_000), t.connectTimes)
        assertEquals(ConnState.CONNECTED, conn.state.value)
        assertEquals(ConnIssue.NONE, conn.issue.value)
    }

    @Test fun disabledNeverTriesAndAskingNothingIsPrinted() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(PrinterSettings(enabled = false, deviceKey = "AA:BB")), { t })
        runCurrent()
        conn.setForeground(true)
        advanceTimeBy(120_000); runCurrent()
        assertTrue(t.connectTimes.isEmpty())
        assertEquals(ConnState.DISCONNECTED, conn.state.value)
        assertEquals(PrintOutcome.DISABLED, conn.print(job))
        assertTrue(t.connectTimes.isEmpty())
        assertEquals(PrinterBadge.OFF, PrinterBadge.of(false, conn.state.value))
    }

    @Test fun goingToBackgroundStopsEveryAttemptAndReleasesTheLink() = runTest {
        val t = ScriptedTransport(this, connectFailures = 100)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        conn.setForeground(true)
        advanceTimeBy(10_000); runCurrent()
        val before = t.connectTimes.size
        assertTrue(before >= 2)
        conn.setForeground(false)
        advanceTimeBy(300_000); runCurrent()
        assertEquals(before, t.connectTimes.size)
        assertEquals(ConnState.DISCONNECTED, conn.state.value)
    }

    @Test fun connectedThenBackgroundClosesAndForegroundReconnectsAtOnce() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        conn.setForeground(true); runCurrent()
        assertEquals(ConnState.CONNECTED, conn.state.value)
        conn.setForeground(false); runCurrent()
        assertEquals(ConnState.DISCONNECTED, conn.state.value)
        assertFalse(t.connected)
        advanceTimeBy(20_000)
        val start = currentTime
        conn.setForeground(true); runCurrent()
        assertEquals(ConnState.CONNECTED, conn.state.value)
        assertEquals(start, t.connectTimes.last()) // de inmediato, sin esperar ninguna espera
    }

    @Test fun printWhenDisconnectedTriesAFreshConnectionFirst() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent() // opción activada pero la app «no está a la vista»: no hay bucle; imprimir conecta por su cuenta
        assertEquals(ConnState.DISCONNECTED, conn.state.value)
        assertEquals(PrintOutcome.PRINTED, conn.print(job))
        assertEquals(1, t.connectTimes.size)
        assertArrayEquals(job, t.jobs.single())
        assertEquals(ConnState.CONNECTED, conn.state.value)
        assertFalse(conn.hasPending.value)
    }

    @Test fun printWithNoPrinterFailsKeepsTheJobAndRetryPrintsIt() = runTest {
        val t = ScriptedTransport(this, connectFailures = 1)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        assertEquals(PrintOutcome.NOT_CONNECTED, conn.print(job))
        assertTrue(conn.hasPending.value)
        assertEquals(ConnIssue.NOT_FOUND, conn.issue.value)
        assertTrue(t.jobs.isEmpty())
        assertEquals(PrintOutcome.PRINTED, conn.retryPending()) // «Reintentar»: la impresora ya responde
        assertArrayEquals(job, t.jobs.single())
        assertFalse(conn.hasPending.value)
        assertEquals(PrintOutcome.FAILED, conn.retryPending()) // ya no queda nada pendiente
    }

    @Test fun aBrokenLinkDuringPrintReconnectsOnceAndTriesAgain() = runTest {
        val t = ScriptedTransport(this, writeFailures = 1)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        assertEquals(PrintOutcome.PRINTED, conn.print(job))
        assertEquals(2, t.connectTimes.size)
        assertArrayEquals(job, t.jobs.single())
        // Si además falla el segundo intento, es FAILED y el trabajo queda para reintentar.
        val t2 = ScriptedTransport(this, writeFailures = 2)
        val conn2 = PrinterConnection(backgroundScope, MutableStateFlow(on), { t2 })
        runCurrent()
        assertEquals(PrintOutcome.FAILED, conn2.print(job))
        assertTrue(conn2.hasPending.value)
    }

    @Test fun linkLostFromTheSystemReconnectsImmediately() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        conn.setForeground(true); runCurrent()
        advanceTimeBy(5_000)
        val at = currentTime
        conn.onLinkLost(); runCurrent()
        assertEquals(2, t.connectTimes.size)
        assertEquals(at, t.connectTimes.last())
        assertEquals(ConnState.CONNECTED, conn.state.value)
    }

    @Test fun bluetoothTurnedOffDisconnectsAndTurnedOnReconnects() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        conn.setForeground(true); runCurrent()
        assertEquals(ConnState.CONNECTED, conn.state.value)
        t.btOff = true
        conn.onBluetoothState(false); runCurrent()
        assertEquals(ConnState.ERROR, conn.state.value)
        assertEquals(ConnIssue.BLUETOOTH_OFF, conn.issue.value)
        advanceTimeBy(1_000)
        t.btOff = false
        val at = currentTime
        conn.onBluetoothState(true); runCurrent() // se encendió: se reintenta ya, sin esperar los 2 s
        assertEquals(ConnState.CONNECTED, conn.state.value)
        assertEquals(at, t.connectTimes.last())
    }

    @Test fun aDeadLinkIsNoticedByTheHealthCheckAndReconnected() = runTest {
        val t = ScriptedTransport(this)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t }, healthEveryMs = 15_000)
        runCurrent()
        conn.setForeground(true); runCurrent()
        assertEquals(1, t.connectTimes.size)
        t.alive = false
        advanceTimeBy(14_999); runCurrent()
        assertEquals(1, t.connectTimes.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(2, t.connectTimes.size) // a los 15 s: no respondió, se soltó y se reconectó
        assertEquals(15_000L, t.connectTimes.last())
    }

    @Test fun turningTheOptionOffClosesTheLinkAndStopsTrying() = runTest {
        val t = ScriptedTransport(this)
        val settings = MutableStateFlow(on)
        val conn = PrinterConnection(backgroundScope, settings, { t })
        runCurrent()
        conn.setForeground(true); runCurrent()
        assertEquals(ConnState.CONNECTED, conn.state.value)
        settings.value = on.copy(enabled = false); runCurrent()
        assertEquals(ConnState.DISCONNECTED, conn.state.value)
        assertFalse(t.connected)
        val n = t.connectTimes.size
        advanceTimeBy(200_000); runCurrent()
        assertEquals(n, t.connectTimes.size)
    }

    @Test fun noPrinterChosenDoesNotSpinAndSaysSo() = runTest {
        var calls = 0
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(PrinterSettings(enabled = true)), { calls++; null })
        runCurrent()
        conn.setForeground(true); runCurrent()
        advanceTimeBy(300_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(ConnIssue.NO_DEVICE, conn.issue.value)
        assertEquals(PrintOutcome.NOT_CONNECTED, conn.print(job))
    }

    @Test fun choosingAnotherPrinterDropsTheOldLinkAndConnectsTheNewOne() = runTest {
        val a = ScriptedTransport(this)
        val b = ScriptedTransport(this)
        val settings = MutableStateFlow(on)
        val conn = PrinterConnection(backgroundScope, settings, { if (it.deviceKey == "AA:BB") a else b })
        runCurrent()
        conn.setForeground(true); runCurrent()
        assertTrue(a.connected)
        settings.value = on.copy(deviceKey = "CC:DD"); runCurrent()
        assertFalse(a.connected)
        assertTrue(b.connected)
    }

    @Test fun reconnectNowStartsOverWithoutWaitingForTheBackoff() = runTest {
        val t = ScriptedTransport(this, connectFailures = 3)
        val conn = PrinterConnection(backgroundScope, MutableStateFlow(on), { t })
        runCurrent()
        conn.setForeground(true); runCurrent()
        advanceTimeBy(7_000); runCurrent() // fallos a los 0, 2 y 6 s; el cuarto intento sería a los 14 s
        assertEquals(3, t.connectTimes.size)
        conn.reconnectNow(); runCurrent()
        assertEquals(7_000L, t.connectTimes.last())
        assertEquals(ConnState.CONNECTED, conn.state.value)
    }

    @Test fun badgeFollowsTheState() {
        assertEquals(PrinterBadge.OFF, PrinterBadge.of(false, ConnState.CONNECTED))
        assertEquals(PrinterBadge.CONNECTED, PrinterBadge.of(true, ConnState.CONNECTED))
        assertEquals(PrinterBadge.CONNECTING, PrinterBadge.of(true, ConnState.CONNECTING))
        assertEquals(PrinterBadge.DISCONNECTED, PrinterBadge.of(true, ConnState.ERROR))
        assertEquals(PrinterBadge.DISCONNECTED, PrinterBadge.of(true, ConnState.DISCONNECTED))
    }
}
