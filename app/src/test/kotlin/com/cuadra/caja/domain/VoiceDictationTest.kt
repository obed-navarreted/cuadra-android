package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDictationTest {
    private val ok = VoiceEvent.Tap(serviceAvailable = true, micGranted = true)

    @Test fun everyAndroidErrorCodeHasAFriendlyAdvice() {
        val expected = mapOf(
            1 to VoiceAdvice.OFFLINE_LANGUAGE, 2 to VoiceAdvice.OFFLINE_LANGUAGE, 11 to VoiceAdvice.OFFLINE_LANGUAGE,
            3 to VoiceAdvice.MICROPHONE, 4 to VoiceAdvice.SERVER, 5 to VoiceAdvice.GENERIC, 6 to VoiceAdvice.TRY_AGAIN, 7 to VoiceAdvice.TRY_AGAIN,
            8 to VoiceAdvice.BUSY, 9 to VoiceAdvice.PERMISSION, 10 to VoiceAdvice.BUSY, 12 to VoiceAdvice.LANGUAGE, 13 to VoiceAdvice.LANGUAGE,
            14 to VoiceAdvice.LANGUAGE, 15 to VoiceAdvice.LANGUAGE, 99 to VoiceAdvice.GENERIC,
        )
        expected.forEach { (code, advice) -> assertEquals("código $code", advice, VoiceAdvices.of(VoiceError.fromAndroid(code))) }
        assertEquals(VoiceAdvice.NO_SERVICE, VoiceAdvices.of(VoiceError.NO_SERVICE))
    }

    @Test fun onlySmallErrorsAreQuiet() {
        assertTrue(VoiceAdvices.isQuiet(VoiceError.NO_MATCH))
        assertTrue(VoiceAdvices.isQuiet(VoiceError.BUSY))
        assertTrue(!VoiceAdvices.isQuiet(VoiceError.NETWORK))
    }

    @Test fun tapStartsListeningWhenEverythingIsReady() {
        val s = VoiceMachine.reduce(VoiceState.Idle, ok)
        assertEquals(VoiceState.Listening(""), s.state)
        assertEquals(VoiceEffect.StartListening, s.effect)
    }

    @Test fun partialThenFinalCommitsAndReturnsToIdle() {
        var s = VoiceMachine.reduce(VoiceState.Idle, ok)
        s = VoiceMachine.reduce(s.state, VoiceEvent.Partial("que"))
        assertEquals(VoiceState.Listening("que"), s.state)
        s = VoiceMachine.reduce(s.state, VoiceEvent.Partial("queso seco"))
        s = VoiceMachine.reduce(s.state, VoiceEvent.Final("queso seco"))
        assertEquals(VoiceState.Idle, s.state)
        assertEquals(VoiceEffect.Commit("queso seco"), s.effect)
    }

    @Test fun tapWhileListeningStopsAndWaitsForTheFinalResult() {
        val listening = VoiceState.Listening("hola")
        val s = VoiceMachine.reduce(listening, ok)
        assertEquals(VoiceState.Finishing("hola"), s.state)
        assertEquals(VoiceEffect.StopListening, s.effect)
        assertEquals(VoiceEffect.Commit("hola mundo"), VoiceMachine.reduce(s.state, VoiceEvent.Final("hola mundo")).effect)
    }

    @Test fun noMatchAfterPartialKeepsWhatWasUnderstood() {
        val s = VoiceMachine.reduce(VoiceState.Finishing("dos libras"), VoiceEvent.Error(7))
        assertEquals(VoiceState.Idle, s.state)
        assertEquals(VoiceEffect.Commit("dos libras"), s.effect)
    }

    @Test fun errorsWithoutPartialFail() {
        val s = VoiceMachine.reduce(VoiceState.Listening(""), VoiceEvent.Error(2))
        assertEquals(VoiceState.Failed(VoiceError.NETWORK), s.state)
        assertEquals(VoiceEffect.Cancel, s.effect)
        assertEquals(VoiceState.Failed(VoiceError.NO_MATCH), VoiceMachine.reduce(VoiceState.Listening(""), VoiceEvent.Final("  ")).state)
    }

    @Test fun noServiceExplainsOrFallsBackToTheSystemDialog() {
        assertEquals(VoiceState.Failed(VoiceError.NO_SERVICE), VoiceMachine.reduce(VoiceState.Idle, VoiceEvent.Tap(false, true)).state)
        val fallback = VoiceMachine.reduce(VoiceState.Idle, VoiceEvent.Tap(false, true, fallbackAvailable = true))
        assertEquals(VoiceEffect.LaunchSystemDialog, fallback.effect)
        assertEquals(VoiceState.Idle, fallback.state)
    }

    @Test fun permissionFlow() {
        val ask = VoiceMachine.reduce(VoiceState.Idle, VoiceEvent.Tap(true, false))
        assertEquals(VoiceState.AwaitingPermission, ask.state)
        assertEquals(VoiceEffect.RequestPermission, ask.effect)
        val granted = VoiceMachine.reduce(ask.state, VoiceEvent.PermissionResult(true))
        assertEquals(VoiceEffect.StartListening, granted.effect)
        assertEquals(VoiceState.Listening(""), granted.state)
        assertEquals(VoiceState.Failed(VoiceError.PERMISSION, false), VoiceMachine.reduce(ask.state, VoiceEvent.PermissionResult(false, true)).state)
        assertEquals(VoiceState.Failed(VoiceError.PERMISSION, true), VoiceMachine.reduce(ask.state, VoiceEvent.PermissionResult(false, false)).state)
        // un permiso que llega fuera de lugar no cambia nada
        assertEquals(VoiceState.Idle, VoiceMachine.reduce(VoiceState.Idle, VoiceEvent.PermissionResult(true)).state)
    }

    @Test fun dismissCancelsAnActiveSession() {
        assertEquals(VoiceEffect.Cancel, VoiceMachine.reduce(VoiceState.Listening("x"), VoiceEvent.Dismiss).effect)
        assertEquals(null, VoiceMachine.reduce(VoiceState.Failed(VoiceError.SERVER), VoiceEvent.Dismiss).effect)
        assertEquals(VoiceState.Idle, VoiceMachine.reduce(VoiceState.Failed(VoiceError.SERVER), VoiceEvent.Dismiss).state)
    }

    @Test fun retryFromFailedStartsAgain() {
        assertEquals(VoiceEffect.StartListening, VoiceMachine.reduce(VoiceState.Failed(VoiceError.NETWORK), ok).effect)
    }
}
