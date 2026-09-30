package com.cuadra.caja.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/** Módulos del negocio (los que dejó encendidos o apagados); vacío = todos por omisión. Lo provee la pantalla principal; ver `ModuleVisibility`. */
val LocalModules = staticCompositionLocalOf<Map<String, Boolean>> { emptyMap() }
