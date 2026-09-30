package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguagesTest {
    @Test fun spanishTriesTheDeviceTagThenTheRegionalFallbacks() {
        assertEquals(listOf("es-NI", "es-419", "es-US", "es-ES", "es"), VoiceLanguages.chain("es-NI"))
        assertEquals(listOf("es-MX", "es-419", "es-US", "es-ES", "es"), VoiceLanguages.chain("es-MX"))
    }

    @Test fun spanishWithoutRegionAndAlreadyInTheList() {
        assertEquals(listOf("es", "es-419", "es-US", "es-ES"), VoiceLanguages.chain("es"))
        assertEquals(listOf("es-419", "es-US", "es-ES", "es"), VoiceLanguages.chain("es-419"))
        assertEquals(listOf("es-ES", "es-419", "es-US", "es"), VoiceLanguages.chain("es-ES"))
    }

    @Test fun englishChain() {
        assertEquals(listOf("en-US", "en-GB", "en"), VoiceLanguages.chain("en-US"))
        assertEquals(listOf("en-AU", "en-US", "en-GB", "en"), VoiceLanguages.chain("en-AU"))
        assertEquals(listOf("en", "en-US", "en-GB"), VoiceLanguages.chain("en"))
    }

    @Test fun otherLanguagesKeepTheirTagAndBase() {
        assertEquals(listOf("pt-BR", "pt"), VoiceLanguages.chain("pt-BR"))
        assertEquals(listOf("fr"), VoiceLanguages.chain("fr"))
        assertEquals(listOf("es-NI", "es-419", "es-US", "es-ES", "es"), VoiceLanguages.chain("es_NI"))
        assertTrue(VoiceLanguages.chain("  ").isEmpty())
    }

    private val online = VoiceEnv(hasNetwork = true, standardAvailable = true, onDeviceAvailable = false)
    private val onlineWithDevice = online.copy(onDeviceAvailable = true)
    private val offline = VoiceEnv(hasNetwork = false, standardAvailable = true, onDeviceAvailable = true)

    @Test fun startsWithTheNormalRecognizerWhenThereIsNetwork() {
        val plan = VoicePlan.start(VoiceLanguages.chain("es-NI"), onlineWithDevice)
        assertEquals(VoiceAttempt("es-NI", VoiceEngine.STANDARD), plan.current)
    }

    @Test fun usesTheOnDeviceRecognizerOnlyWithoutNetwork() {
        assertEquals(VoiceEngine.ON_DEVICE, VoicePlan.start(listOf("es"), offline).current.engine)
        // Sin red pero sin reconocedor propio: el normal (dirá que falta internet).
        assertEquals(VoiceEngine.STANDARD, VoicePlan.start(listOf("es"), offline.copy(onDeviceAvailable = false)).current.engine)
        // Sin reconocedor normal pero con el del teléfono: ese.
        assertEquals(VoiceEngine.ON_DEVICE, VoicePlan.start(listOf("es"), online.copy(standardAvailable = false, onDeviceAvailable = true)).current.engine)
    }

    @Test fun languageErrorsWalkTheWholeChainThenGiveUp() {
        var plan = VoicePlan.start(VoiceLanguages.chain("es-NI"), online)
        val seen = mutableListOf(plan.current.tag)
        val errors = listOf(VoiceError.LANGUAGE_NOT_SUPPORTED, VoiceError.LANGUAGE_UNAVAILABLE, VoiceError.CLIENT, VoiceError.LANGUAGE_NOT_SUPPORTED)
        for (e in errors) { plan = plan.after(e, online)!!; seen += plan.current.tag }
        assertEquals(listOf("es-NI", "es-419", "es-US", "es-ES", "es"), seen)
        assertNull("con todo probado se muestra el error", plan.after(VoiceError.LANGUAGE_UNAVAILABLE, online))
        assertEquals(listOf("es-NI", "es-419", "es-US", "es-ES", "es"), plan.triedTags)
        // Nunca cambió a «sin conexión».
        assertTrue(plan.tried.all { it.engine == VoiceEngine.STANDARD })
    }

    @Test fun otherErrorsDoNotRetry() {
        val plan = VoicePlan.start(VoiceLanguages.chain("es-NI"), online)
        listOf(VoiceError.NO_MATCH, VoiceError.BUSY, VoiceError.PERMISSION, VoiceError.AUDIO, VoiceError.SERVER, VoiceError.UNKNOWN, VoiceError.TOO_MANY_REQUESTS, VoiceError.NO_SERVICE)
            .forEach { assertNull("$it", plan.after(it, online)) }
    }

    @Test fun aNetworkErrorFallsBackToTheOnDeviceRecognizerOnce() {
        val plan = VoicePlan.start(VoiceLanguages.chain("es-NI"), onlineWithDevice)
        val next = plan.after(VoiceError.NETWORK, onlineWithDevice)!!
        assertEquals(VoiceAttempt("es-NI", VoiceEngine.ON_DEVICE), next.current)
        assertNull("no vuelve a probar el mismo", next.after(VoiceError.NETWORK, onlineWithDevice))
        // Sin reconocedor propio, un error de red se muestra tal cual.
        assertNull(plan.after(VoiceError.NETWORK, online))
    }

    @Test fun onDeviceLanguageErrorsAlsoWalkTheChain() {
        var plan = VoicePlan.start(VoiceLanguages.chain("en-US"), offline)
        assertEquals(VoiceEngine.ON_DEVICE, plan.current.engine)
        plan = plan.after(VoiceError.LANGUAGE_UNAVAILABLE, offline)!!
        assertEquals(VoiceAttempt("en-GB", VoiceEngine.ON_DEVICE), plan.current)
        plan = plan.after(VoiceError.LANGUAGE_UNAVAILABLE, offline)!!
        assertEquals("en", plan.current.tag)
        assertNull(plan.after(VoiceError.LANGUAGE_UNAVAILABLE, offline))
        assertEquals(listOf("en-US", "en-GB", "en"), plan.triedTags)
    }

    @Test fun anEmptyChainStillHasSomethingToTry() {
        assertEquals("en", VoicePlan.start(emptyList(), online).current.tag)
        assertFalse(VoicePlan.start(emptyList(), online).triedTags.isEmpty())
    }
}
