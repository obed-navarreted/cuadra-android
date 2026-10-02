package com.cuadra.caja.data.printer

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cuadra.caja.domain.printing.EndSpacing
import com.cuadra.caja.domain.printing.PrintCharset
import com.cuadra.caja.domain.printing.PrinterLink
import com.cuadra.caja.domain.printing.PrinterSettings
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.printerDataStore by preferencesDataStore("printer")

/** Ajustes de la impresora, guardados en ESTE teléfono (DataStore `printer`). Todo apagado por omisión. */
class PrinterPrefs(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.printerDataStore

    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val link = stringPreferencesKey("link")
        val deviceKey = stringPreferencesKey("device_key")
        val deviceName = stringPreferencesKey("device_name")
        val widthMm = intPreferencesKey("width_mm")
        val auto = booleanPreferencesKey("auto_print")
        val copies = intPreferencesKey("copies")
        val charset = stringPreferencesKey("charset")
        val address = stringPreferencesKey("address")
        val phone = stringPreferencesKey("phone")
        val taxId = stringPreferencesKey("tax_id")
        val footer = stringPreferencesKey("footer")
        val endSpacing = stringPreferencesKey("end_spacing")
        val hasCutter = booleanPreferencesKey("has_cutter")
    }

    private fun Preferences.read() = PrinterSettings(
        enabled = this[K.enabled] ?: false, link = PrinterLink.parse(this[K.link]), deviceKey = this[K.deviceKey], deviceName = this[K.deviceName],
        widthMm = this[K.widthMm] ?: 58, autoPrint = this[K.auto] ?: true, copies = (this[K.copies] ?: 1).coerceIn(1, 2), charset = PrintCharset.parse(this[K.charset]),
        address = this[K.address].orEmpty(), phone = this[K.phone].orEmpty(), taxId = this[K.taxId].orEmpty(), footer = this[K.footer].orEmpty(),
        endSpacing = EndSpacing.parse(this[K.endSpacing]), hasCutter = this[K.hasCutter] ?: true,
    )

    private fun MutablePreferences.write(s: PrinterSettings) {
        this[K.enabled] = s.enabled; this[K.link] = s.link.key; this[K.widthMm] = s.widthMm; this[K.auto] = s.autoPrint; this[K.copies] = s.copies
        this[K.charset] = s.charset.key; this[K.address] = s.address; this[K.phone] = s.phone; this[K.taxId] = s.taxId; this[K.footer] = s.footer
        this[K.endSpacing] = s.endSpacing.key; this[K.hasCutter] = s.hasCutter
        if (s.deviceKey != null) this[K.deviceKey] = s.deviceKey else remove(K.deviceKey)
        if (s.deviceName != null) this[K.deviceName] = s.deviceName else remove(K.deviceName)
    }

    val settings: StateFlow<PrinterSettings> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it.read() }
        .stateIn(scope, SharingStarted.Eagerly, PrinterSettings())

    /** Cambia los ajustes leyendo el valor guardado (no el último visto): dos cambios seguidos no se pisan. */
    fun update(change: (PrinterSettings) -> PrinterSettings) {
        scope.launch { store.edit { p -> p.write(change(p.read())) } }
    }
}
