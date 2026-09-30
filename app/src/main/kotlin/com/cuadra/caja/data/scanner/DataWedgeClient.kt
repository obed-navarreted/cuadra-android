package com.cuadra.caja.data.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Zebra DataWedge por intents: recibe las lecturas (difusión `DataWedge.SCAN_ACTION`, registrada solo mientras la app está a la vista) y crea el perfil
 * «Cuentiva» con la API de DataWedge. Todo va en try/catch: sin DataWedge (teléfonos normales) no pasa nada.
 */
class DataWedgeClient(private val context: Context, private val hub: ScanHub, private val scope: CoroutineScope) {
    private var registered = false
    private var job: Job? = null
    private val results = ArrayList<DataWedge.StepResult>()

    fun isPresent(): Boolean = try {
        context.packageManager.getPackageInfo(DataWedge.PACKAGE, 0); true
    } catch (_: Exception) { false }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            runCatching {
                val read = DataWedge.readScan(intent.getStringExtra(DataWedge.EXTRA_DATA), intent.getStringExtra(DataWedge.EXTRA_LABEL_TYPE)) ?: return
                hub.submit(read.data, ScanSource.DATAWEDGE, DataWedge.friendlyLabel(read.labelType))
            }
        }
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            runCatching {
                val info = intent.getBundleExtra("RESULT_INFO")?.let { b -> b.keySet().joinToString(", ") { "$it=${b.get(it)}" } } ?: intent.getStringExtra("RESULT_INFO")
                val r = DataWedge.parseResult(intent.getStringExtra("COMMAND"), intent.getStringExtra("RESULT"), intent.getStringExtra(DataWedge.EXTRA_COMMAND_ID), info) ?: return
                if (r.id?.startsWith("cuentiva-") != true) return
                results += r
                when (val o = DataWedge.summarize(results, DataWedge.setConfigSteps(context.packageName).size)) {
                    SetupOutcome.Ok -> { job?.cancel(); hub.setSetup(SetupState.OK) }
                    is SetupOutcome.Failed -> { job?.cancel(); hub.setSetup(SetupState.FAILED, o.detail) }
                    SetupOutcome.Waiting -> Unit
                }
            }
        }
    }

    /** Empieza a escuchar (al ponerse la app a la vista). Exportado: DataWedge es otra app y difunde hacia nosotros. */
    fun attach() {
        if (registered) return
        runCatching {
            ContextCompat.registerReceiver(context, scanReceiver, IntentFilter(DataWedge.SCAN_ACTION), ContextCompat.RECEIVER_EXPORTED)
            ContextCompat.registerReceiver(
                context, resultReceiver, IntentFilter(DataWedge.RESULT_ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }, ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
    }

    fun detach() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(scanReceiver) }
        runCatching { context.unregisterReceiver(resultReceiver) }
    }

    /** Crea o actualiza el perfil «Cuentiva». El resultado llega por `hub.setup` (respuestas de DataWedge, o «sin respuesta» a los pocos segundos). */
    fun configure() {
        if (!isPresent()) { hub.setSetup(SetupState.ABSENT); return }
        if (!registered) attach()
        job?.cancel()
        results.clear()
        hub.setSetup(SetupState.RUNNING)
        job = scope.launch {
            try {
                for (step in DataWedge.setConfigSteps(context.packageName)) {
                    send(Intent(DataWedge.API_ACTION).putExtra(DataWedge.EXTRA_SET_CONFIG, toBundle(step.config)).putExtra(DataWedge.EXTRA_COMMAND_ID, step.id))
                    delay(400)
                }
                send(Intent(DataWedge.API_ACTION).putExtra(DataWedge.EXTRA_SWITCH_PROFILE, DataWedge.PROFILE))
                delay(4000)
                hub.setSetup(SetupState.NO_ANSWER)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                hub.setSetup(SetupState.FAILED, e.message)
            }
        }
    }

    private fun send(intent: Intent) {
        intent.setPackage(DataWedge.PACKAGE).putExtra(DataWedge.EXTRA_SEND_RESULT, "true")
        context.sendBroadcast(intent)
    }

    companion object {
        /** Mapas → `Bundle`, listas de mapas → arreglo de `Bundle`, listas de texto → arreglo de texto. */
        fun toBundle(map: Map<String, Any>): Bundle = Bundle().also { b ->
            for ((k, v) in map) when (v) {
                is String -> b.putString(k, v)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") b.putBundle(k, toBundle(v as Map<String, Any>))
                is List<*> -> if (v.firstOrNull() is Map<*, *>) @Suppress("UNCHECKED_CAST") b.putParcelableArray(k, v.map { toBundle(it as Map<String, Any>) }.toTypedArray())
                else b.putStringArray(k, v.map { it.toString() }.toTypedArray())
                else -> b.putString(k, v.toString())
            }
        }
    }
}
