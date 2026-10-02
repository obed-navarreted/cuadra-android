package com.cuadra.caja.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cuadra.caja.R
import com.cuadra.caja.domain.RefreshTrigger
import com.cuadra.caja.ui.theme.CuadraColors
import kotlinx.coroutines.delay

const val TAG_REFRESHING = "refreshing"

/**
 * Pide refrescar al abrir la pantalla (SHOWN) y cada vez que la app vuelve al frente con ella abierta (RESUMED). Cada pantalla que se vuelve a abrir entra
 * de nuevo en la composición, así que se pide otra vez: nunca se queda una lista vieja.
 */
@Composable
fun RefreshOnShow(onRefresh: (RefreshTrigger) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onRefresh)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        latest(if (shown) RefreshTrigger.RESUMED else RefreshTrigger.SHOWN)
        shown = true
    }
}

/** Refresca cada `millis` mientras la pantalla está a la vista y la app al frente («Por cobrar en caja»). */
@Composable
fun RefreshEvery(millis: Long, onTick: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(onTick)
    LaunchedEffect(lifecycle, millis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(millis)
                latest()
            }
        }
    }
}

/**
 * Una lista que se refresca deslizando hacia abajo. `refreshing`: lo que ya estaba sigue a la vista con el indicador pequeño arriba (no se vacía).
 * `offline`: arriba, «Sin conexión: se muestra lo guardado en este teléfono» (no es un error).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, offline: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = if (refreshing) modifier.testTag(TAG_REFRESHING) else modifier) {
        if (offline) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.refresh_offline), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    color = CuadraColors.Orange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.weight(1f)) { content() }
            }
        } else content()
    }
}

/**
 * En una hoja (donde deslizar para refrescar no es natural): «Actualizar», o la barra fina con «Actualizando…» mientras se refresca. Lo de antes sigue a la
 * vista.
 */
@Composable
fun RefreshRow(refreshing: Boolean, onRefresh: () -> Unit) {
    if (refreshing) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().testTag(TAG_REFRESHING)) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = CuadraColors.Ink, trackColor = CuadraColors.Line)
            Text(stringResource(R.string.refresh_running), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else LinkAction(stringResource(R.string.refresh_now), onRefresh)
}

/**
 * Lo de siempre para una pantalla con lista: refresca al abrirla y al volver la app al frente, se puede deslizar para refrescar, muestra el indicador
 * pequeño mientras tanto y «Sin conexión» si no se pudo. `everyMillis`: además, cada tanto mientras está a la vista. `busy`: otra carga en curso de la
 * pantalla (p. ej. un filtro) que también muestra el indicador.
 */
@Composable
fun Refreshing(
    refresher: com.cuadra.caja.ui.ScreenRefresh, modifier: Modifier = Modifier.fillMaxSize(), everyMillis: Long? = null, busy: Boolean = false,
    /** false: la pantalla ya explica a su manera que no hay conexión. */
    showOffline: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val refreshing by refresher.refreshing.collectAsState()
    val offline by refresher.offline.collectAsState()
    RefreshOnShow { refresher.request(it) }
    if (everyMillis != null) RefreshEvery(everyMillis) { refresher.request(RefreshTrigger.PERIODIC) }
    RefreshBox(refreshing || busy, { refresher.request(RefreshTrigger.PULLED) }, modifier, offline && showOffline, content)
}
