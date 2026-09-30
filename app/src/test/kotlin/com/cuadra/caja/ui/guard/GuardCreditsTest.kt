package com.cuadra.caja.ui.guard

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.ui.CashActions
import com.cuadra.caja.ui.CashUi
import com.cuadra.caja.ui.CountDraft
import com.cuadra.caja.ui.CreditFilter
import com.cuadra.caja.ui.CreditsActions
import com.cuadra.caja.ui.CreditsUi
import com.cuadra.caja.ui.CustomerDraft
import com.cuadra.caja.ui.ExpenseDraft
import com.cuadra.caja.ui.LedgerMode
import com.cuadra.caja.ui.ManualDraft
import com.cuadra.caja.ui.MovementDraft
import com.cuadra.caja.ui.PayTarget
import com.cuadra.caja.ui.ShareRequest
import com.cuadra.caja.ui.VoidTarget
import com.cuadra.caja.R
import com.cuadra.caja.ui.screens.CreditsContent
import com.cuadra.caja.ui.screens.CustomerEditorDialog
import com.cuadra.caja.ui.screens.ExpenseDialog
import com.cuadra.caja.ui.screens.ExpensesContent
import com.cuadra.caja.ui.screens.LinkDialog
import com.cuadra.caja.ui.screens.ManualDialog
import com.cuadra.caja.ui.screens.MovementDialog
import com.cuadra.caja.ui.screens.PayDialog
import com.cuadra.caja.ui.screens.ShiftContent
import com.cuadra.caja.ui.screens.WriteOffDialog
import com.cuadra.caja.ui.screens.Counter
import com.cuadra.caja.ui.common.Sheet
import com.cuadra.caja.ui.common.Text
import com.cuadra.caja.ui.common.CuadraButton
import androidx.compose.runtime.Composable
import com.cuadra.caja.ui.CategoryManagerUi
import com.cuadra.caja.ui.ErrorMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardCreditsTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun creditsExpensesShift() {
        val runner = GuardRunner(rule, "credits")
        runner.run(
            listOf(
                GuardCase("Fiados: lista", GuardMatrix.FULL) { Credits(CreditsUi(), Fixtures.ledgerRows) },
                GuardCase("Fiados: lista vacía", GuardMatrix.FULL) { Credits(CreditsUi(), emptyList()) },
                GuardCase("Fiados: clientes", GuardMatrix.FULL) { Credits(CreditsUi(mode = LedgerMode.CUSTOMERS, query = "Ma"), Fixtures.ledgerRows) },
                GuardCase("Fiados: pagados", GuardMatrix.FULL) { Credits(CreditsUi(filter = CreditFilter.PAID), Fixtures.ledgerRows) },
                GuardCase("Fiados: detalle de cliente", GuardMatrix.FULL) { Credits(CreditsUi(openCustomerId = "c1"), Fixtures.ledgerRows, detail = Fixtures.detail, manage = true) },
                GuardCase("Fiados: detalle sin permisos", GuardMatrix.FULL) { Credits(CreditsUi(openCustomerId = "c1"), Fixtures.ledgerRows, detail = Fixtures.detail.copy(customer = Fixtures.customers[1]), manage = false) },
                GuardCase("Fiados: abonar", GuardMatrix.FULL) { PayDialog(PayTarget(null, "c1", Fixtures.PERSON_LONG, Fixtures.HUGE, Fixtures.PHONE), object : CreditsActions {}) },
                GuardCase("Fiados: abonar (teclado)", GuardMatrix.KEYBOARD) { PayDialog(PayTarget(null, "c1", Fixtures.PERSON_LONG, Fixtures.HUGE, Fixtures.PHONE), object : CreditsActions {}) },
                GuardCase("Fiados: saldar", GuardMatrix.FULL) { PayDialog(PayTarget("k1", null, Fixtures.NAME_200, Fixtures.BIG, null, settleAll = true), object : CreditsActions {}) },
                GuardCase("Fiados: fiado manual", GuardMatrix.FULL) { ManualDialog(ManualDraft(Fixtures.NAME_120, Fixtures.PHONE_30, "99999999.99", Fixtures.NAME_200), object : CreditsActions {}) },
                GuardCase("Fiados: fiado manual (teclado)", GuardMatrix.KEYBOARD) { ManualDialog(ManualDraft(Fixtures.NAME_120, Fixtures.PHONE_30, "99999999.99", Fixtures.NAME_200), object : CreditsActions {}) },
                GuardCase("Fiados: editar cliente", GuardMatrix.FULL) { CustomerEditorDialog(CustomerDraft("c1", Fixtures.PERSON_LONG, Fixtures.PHONE_30, Fixtures.NAME_200, "99999999.99"), object : CreditsActions {}) },
                GuardCase("Fiados: editar cliente (teclado)", GuardMatrix.KEYBOARD) { CustomerEditorDialog(CustomerDraft("c1", Fixtures.PERSON_LONG, Fixtures.PHONE_30, Fixtures.NAME_200, "99999999.99"), object : CreditsActions {}) },
                GuardCase("Fiados: editar cliente sin saldo (con Archivar)", GuardMatrix.FULL) { CustomerEditorDialog(CustomerDraft("c1", Fixtures.PERSON_LONG, Fixtures.PHONE_30, Fixtures.NAME_200, "99999999.99", 0), object : CreditsActions {}) },
                GuardCase("Fiados: editar cliente sin saldo (teclado)", GuardMatrix.KEYBOARD) { CustomerEditorDialog(CustomerDraft("c1", Fixtures.PERSON_LONG, Fixtures.PHONE_30, Fixtures.NAME_200, "99999999.99", 0), object : CreditsActions {}) },
                GuardCase("Fiados: archivar cliente", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.ArchiveCustomerDialog(object : CreditsActions {}) },
                GuardCase("Fiados: anular abono con motivo", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.VoidPaymentDialog(object : CreditsActions {}) },
                GuardCase("Fiados: anular abono con motivo (teclado)", GuardMatrix.KEYBOARD) { com.cuadra.caja.ui.screens.VoidPaymentDialog(object : CreditsActions {}) },
                GuardCase("Fiados: umbral de vencido de 365 días", GuardMatrix.FULL) { Credits(CreditsUi(overdueDays = 365), Fixtures.ledgerRows) },
                GuardCase("Fiados: vincular", GuardMatrix.FULL) { LinkDialog(Fixtures.customers + Fixtures.customers, object : CreditsActions {}) },
                GuardCase("Fiados: condonar", GuardMatrix.FULL) { WriteOffDialog(object : CreditsActions {}) },
                GuardCase("Fiados: condonar (teclado)", GuardMatrix.KEYBOARD) { WriteOffDialog(object : CreditsActions {}) },
                GuardCase("Gastos: lista", GuardMatrix.FULL) { Expenses(CashUi(), true, true) },
                GuardCase("Gastos: cajero sin turnos", GuardMatrix.FULL) { Expenses(CashUi(), false, false) },
                GuardCase("Gastos: ayer", GuardMatrix.FULL) { Expenses(CashUi(range = com.cuadra.caja.domain.RangeChoice(com.cuadra.caja.domain.RangePreset.YESTERDAY)), true, false) },
                GuardCase("Gastos: mes pasado", GuardMatrix.FULL) { Expenses(CashUi(range = com.cuadra.caja.domain.RangeChoice(com.cuadra.caja.domain.RangePreset.LAST_MONTH)), false, false) },
                GuardCase("Gastos: vacío", GuardMatrix.FULL) { Expenses(CashUi(), true, true, rows = emptyList(), shift = null) },
                GuardCase("Gastos: nuevo gasto", GuardMatrix.FULL) { ExpenseDialog(ExpenseDraft("99999999.99", Fixtures.NAME_120, "e5"), Fixtures.categories, true, object : CashActions {}) },
                GuardCase("Gastos: nuevo gasto (teclado)", GuardMatrix.KEYBOARD) { ExpenseDialog(ExpenseDraft("99999999.99", Fixtures.NAME_120, "e5"), Fixtures.categories, true, object : CashActions {}) },
                GuardCase("Gastos: nuevo gasto de cajero", GuardMatrix.FULL) { ExpenseDialog(ExpenseDraft("12", "", null), Fixtures.categories, false, object : CashActions {}) },
                GuardCase("Gastos: administrar categorías", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.CategoryManagerDialog(CategoryManagerUi(), Fixtures.categories + Fixtures.categories, object : CashActions {}) },
                GuardCase("Gastos: renombrar categoría", GuardMatrix.FULL) { com.cuadra.caja.ui.screens.CategoryManagerDialog(CategoryManagerUi(editingId = "e5", name = Fixtures.NAME_60), Fixtures.categories, object : CashActions {}) },
                GuardCase("Gastos: renombrar categoría (teclado)", GuardMatrix.KEYBOARD) { com.cuadra.caja.ui.screens.CategoryManagerDialog(CategoryManagerUi(adding = true, name = Fixtures.NAME_60, error = ErrorMessage(R.string.error_offline)), Fixtures.categories, object : CashActions {}) },
                GuardCase("Gastos: con el enlace de categorías", GuardMatrix.FULL) { Expenses(CashUi(categoryManager = CategoryManagerUi()), true, true) },
                GuardCase("Gastos: entrada", GuardMatrix.FULL) { MovementDialog(MovementDraft("DEPOSIT", "99999999.99", Fixtures.NAME_120), object : CashActions {}) },
                GuardCase("Gastos: entrada (teclado)", GuardMatrix.KEYBOARD) { MovementDialog(MovementDraft("DEPOSIT", "99999999.99", Fixtures.NAME_120), object : CashActions {}) },
                GuardCase("Gastos: retiro", GuardMatrix.FULL) { MovementDialog(MovementDraft("WITHDRAWAL", "12", ""), object : CashActions {}) },
                GuardCase("Gastos: anular", GuardMatrix.FULL) { Expenses(CashUi(voidTarget = VoidTarget("x1", false, Fixtures.NAME_200)), true, true) },
                GuardCase("Turno: abrir", GuardMatrix.FULL) { Shift(CashUi(openFloat = "99999999.99"), shift = null, suggested = Fixtures.BIG) },
                GuardCase("Turno: abrir (teclado)", GuardMatrix.KEYBOARD) { Shift(CashUi(openFloat = "99999999.99"), shift = null, suggested = Fixtures.BIG) },
                GuardCase("Turno: abrir (obligatorio)", GuardMatrix.FULL) { Shift(CashUi(), shift = null, required = true) },
                GuardCase("Turno: abierto, contando", GuardMatrix.FULL) { Shift(CashUi(count = CountDraft(counted = "99999999.99", note = Fixtures.NAME_200)), shift = Fixtures.shift) },
                GuardCase("Turno: abierto, contando (teclado)", GuardMatrix.KEYBOARD) { Shift(CashUi(count = CountDraft(counted = "99999999.99", note = Fixtures.NAME_200)), shift = Fixtures.shift) },
                GuardCase("Turno: abierto, nota obligatoria", GuardMatrix.FULL) { Shift(CashUi(count = CountDraft(counted = "1", note = ""), noteRequired = true), shift = Fixtures.shift) },
                GuardCase("Turno: cerrado", GuardMatrix.FULL) { Shift(CashUi(closedShift = Fixtures.closedShift(1, -Fixtures.BIG)), shift = null) },
                GuardCase("Turno: mensaje", GuardMatrix.FULL) { Shift(CashUi(messageRes = R.string.shift_no_register), shift = null) },
                GuardCase("Turno: contador de billetes", GuardMatrix.FULL) { Counter(CountDraft(counts = mapOf(100_000L to 12345, 50_000L to 1, 20_000L to 0), showCounter = true), "NIO", object : CashActions {}) },
                GuardCase("Turno: contador de billetes (teclado)", GuardMatrix.KEYBOARD) { Counter(CountDraft(counts = mapOf(100_000L to 12345, 50_000L to 1, 20_000L to 0), showCounter = true), "NIO", object : CashActions {}) },
            ),
        )
        runner.assertClean()
    }

    @Composable private fun Credits(ui: CreditsUi, rows: List<com.cuadra.caja.ui.LedgerRow>, detail: com.cuadra.caja.ui.CustomerDetailUi? = null, manage: Boolean = true) =
        CreditsContent(ui, rows, Fixtures.customers, Fixtures.totals, detail, manage, object : CreditsActions {})

    @Composable private fun Expenses(ui: CashUi, manage: Boolean, shifts: Boolean, rows: List<com.cuadra.caja.ui.CashRow> = Fixtures.cashRows, shift: com.cuadra.caja.data.local.ShiftEntity? = Fixtures.shift) =
        ExpensesContent(ui, Fixtures.expenseTotals, rows, manage, shift, Fixtures.categories, Fixtures.calendar, shifts, object : CashActions {}, {}, Fixtures.NOW)

    @Composable private fun Shift(ui: CashUi, shift: com.cuadra.caja.data.local.ShiftEntity?, required: Boolean = false, suggested: Long = 0) =
        ShiftContent(ui, shift, Fixtures.breakdown, Fixtures.recentShifts, "NIO", suggested, "America/Managua", required, object : CashActions {}, {})
}
