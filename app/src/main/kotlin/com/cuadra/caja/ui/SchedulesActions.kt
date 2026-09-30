package com.cuadra.caja.ui

import com.cuadra.caja.data.remote.ScheduleDto
import com.cuadra.caja.domain.ScheduleDraft

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : SchedulesActions {}`. */
interface SchedulesActions {
    fun dismissMessage() {}
    fun openNew() {}
    fun edit(s: ScheduleDto) {}
    fun updateEditor(d: ScheduleDraft) {}
    fun closeEditor() {}
    fun applyTemplate(title: String, body: String) {}
    fun save() {}
    fun toggle(s: ScheduleDto) {}
    fun duplicate(s: ScheduleDto, suffix: String) {}
    fun askDelete(id: String) {}
    fun cancelDelete() {}
    fun confirmDelete() {}
    fun openHistory(s: ScheduleDto) {}
    fun closeHistory() {}
    fun load() {}
}
