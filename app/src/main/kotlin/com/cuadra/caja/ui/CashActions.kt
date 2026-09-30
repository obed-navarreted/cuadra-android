package com.cuadra.caja.ui

/** Lo que las pantallas le piden al ViewModel. Tiene cuerpos vacíos por omisión: la guardia de diseño usa `object : CashActions {}`. */
interface CashActions {
    fun setRange(choice: com.cuadra.caja.domain.RangeChoice) {}
    fun dismissMessage() {}
    fun openExpense() {}
    fun updateExpense(d: ExpenseDraft) {}
    fun closeExpense() {}
    fun openMovement(kind: String) {}
    fun updateMovement(d: MovementDraft) {}
    fun closeMovement() {}
    fun askVoid(t: VoidTarget) {}
    fun updateVoid(reason: String) {}
    fun closeVoid() {}
    fun showShift(show: Boolean) {}
    fun updateOpenFloat(text: String?) {}
    fun updateCount(d: CountDraft) {}
    fun saveExpense() {}
    fun saveMovement() {}
    fun confirmVoid() {}
    fun openCategories() {}
    fun closeCategories() {}
    fun startCategoryEdit(id: String?) {}
    fun updateCategoryName(name: String) {}
    fun cancelCategoryEdit() {}
    fun saveCategory() {}
    fun archiveCategory(id: String) {}
    fun openShift() {}
    fun closeShift() {}
}
