package com.cuadra.caja.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cuadra.caja.domain.FontSizeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

private val Context.displayDataStore by preferencesDataStore("display")

/** Preferencias de pantalla de ESTE teléfono (no viajan con el negocio ni con la persona): el tamaño de letra, si al terminar una venta se ofrece enviar el comprobante por WhatsApp y si la calculadora pide descripción (los dos apagados por omisión). */
class DisplayPrefs(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.displayDataStore
    private val key = stringPreferencesKey("font_size")

    private fun Preferences.choice() = FontSizeChoice.parse(this[key])

    // Se lee una vez de forma síncrona (un archivo pequeño) para que la primera pantalla ya nazca con la letra elegida, sin saltos.
    private val initial: FontSizeChoice = runCatching { runBlocking { store.data.first().choice() } }.getOrDefault(FontSizeChoice.AUTO)

    val fontSize: StateFlow<FontSizeChoice> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.choice() }.stateIn(scope, SharingStarted.Eagerly, initial)

    fun setFontSize(choice: FontSizeChoice) { scope.launch { store.edit { it[key] = choice.name } } }

    private val offerKey = booleanPreferencesKey("offer_whatsapp_receipt")

    private fun Preferences.offer() = this[offerKey] == true

    private val initialOffer: Boolean = runCatching { runBlocking { store.data.first().offer() } }.getOrDefault(false)

    /**
     * ¿Ofrecer enviar el comprobante por WhatsApp al terminar la venta? APAGADO por omisión: apagado, la pantalla «Venta cobrada» no muestra el botón de WhatsApp
     * y no se abre ningún diálogo de compartir solo (tampoco tras un fiado; el mensaje del fiado sigue en el detalle del fiado).
     */
    val offerWhatsApp: StateFlow<Boolean> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.offer() }.stateIn(scope, SharingStarted.Eagerly, initialOffer)

    fun setOfferWhatsApp(on: Boolean) { scope.launch { store.edit { it[offerKey] = on } } }

    private val askDescriptionKey = booleanPreferencesKey("ask_description")

    private fun Preferences.askDescription() = this[askDescriptionKey] == true

    private val initialAskDescription: Boolean = runCatching { runBlocking { store.data.first().askDescription() } }.getOrDefault(false)

    /**
     * «Pedir descripción al agregar»: APAGADO por omisión (la calculadora de la caja no muestra el campo de descripción; se escribe después tocando la línea
     * en el recibo). Encendido: el campo vuelve junto al monto, como antes.
     */
    val askDescription: StateFlow<Boolean> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.askDescription() }.stateIn(scope, SharingStarted.Eagerly, initialAskDescription)

    fun setAskDescription(on: Boolean) { scope.launch { store.edit { it[askDescriptionKey] = on } } }
}
