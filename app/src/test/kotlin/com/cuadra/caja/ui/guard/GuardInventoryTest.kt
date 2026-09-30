package com.cuadra.caja.ui.guard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.data.repo.StockFilter
import com.cuadra.caja.domain.Pricing
import com.cuadra.caja.domain.ProductError
import com.cuadra.caja.domain.ProductForm
import com.cuadra.caja.ui.CountDraft2
import com.cuadra.caja.ui.HistoryState
import com.cuadra.caja.ui.InventoryActions
import com.cuadra.caja.ui.InventoryUi
import com.cuadra.caja.ui.LineDraft
import com.cuadra.caja.ui.PayDraft
import com.cuadra.caja.ui.ProductEditorDraft
import com.cuadra.caja.ui.PurchaseDraft
import com.cuadra.caja.ui.PurchasesActions
import com.cuadra.caja.ui.PurchasesTab
import com.cuadra.caja.ui.PurchasesUi
import com.cuadra.caja.ui.StockAction
import com.cuadra.caja.ui.SupplierDraft
import com.cuadra.caja.ui.VoidDraft
import com.cuadra.caja.ui.screens.ConfirmActiveDialog
import com.cuadra.caja.ui.screens.CountDialog
import com.cuadra.caja.ui.screens.DetailSheet
import com.cuadra.caja.ui.screens.EditorDialog
import com.cuadra.caja.ui.screens.InventoryContent
import com.cuadra.caja.ui.screens.LineDialog
import com.cuadra.caja.ui.screens.PayDialog as SupplierPayDialog
import com.cuadra.caja.ui.screens.PickDialog
import com.cuadra.caja.ui.screens.PurchasesContent
import com.cuadra.caja.ui.screens.SupplierDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardInventoryTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL

    @Test fun inventoryAndPurchases() {
        val runner = GuardRunner(rule, "inventory")
        runner.run(
            listOf(
                GuardCase("Inventario: lista", full) { Inventory(InventoryUi()) },
                GuardCase("Inventario: filtro en revisión", full) { Inventory(InventoryUi(filter = StockFilter.REVIEW, query = Fixtures.NAME_60), review = 12_345) },
                GuardCase("Inventario: vacío", full) { Inventory(InventoryUi(), items = emptyList()) },
                GuardCase("Catálogo: lista", full) { Inventory(InventoryUi(), catalogOnly = true) },
                GuardCase("Producto: detalle", full) { Detail(catalogOnly = false, canDelete = true, history = Fixtures.history) },
                GuardCase("Producto: detalle sin historial", full) { Detail(catalogOnly = false, canDelete = false, history = HistoryState.Offline) },
                GuardCase("Producto: detalle de catálogo", full) { Detail(catalogOnly = true, canDelete = true, history = HistoryState.Error) },
                GuardCase("Producto: nuevo (vacío)", full) { Editor(ProductEditorDraft()) },
                GuardCase("Producto: nuevo (teclado)", GuardMatrix.KEYBOARD) { Editor(ProductEditorDraft()) },
                GuardCase("Producto: nuevo con errores", full) { Editor(ProductEditorDraft(errors = setOf(ProductError.NAME_REQUIRED, ProductError.PRICE_REQUIRED, ProductError.COST_INVALID))) },
                GuardCase("Producto: nuevo, precio abierto", full) { Editor(ProductEditorDraft(form = ProductForm(name = Fixtures.NAME_120, pricing = Pricing.OPEN))) },
                GuardCase("Producto: nuevo, precio abierto (teclado)", GuardMatrix.KEYBOARD) { Editor(ProductEditorDraft(form = ProductForm(name = Fixtures.NAME_120, pricing = Pricing.OPEN, price = "99999999.99"))) },
                GuardCase("Producto: nuevo, por peso con código repetido", full) {
                    Editor(ProductEditorDraft(form = ProductForm(name = Fixtures.NAME_200, barcode = Fixtures.CODE_LONG, shortCode = "1234567890123456", pricing = Pricing.BY_WEIGHT, unit = "KG", price = "99999999.99"), barcodeOwner = Fixtures.NAME_120))
                },
                GuardCase("Producto: nuevo, código repetido (teclado)", GuardMatrix.KEYBOARD) {
                    Editor(ProductEditorDraft(form = ProductForm(name = Fixtures.NAME_60, barcode = Fixtures.CODE_LONG, pricing = Pricing.BY_WEIGHT, price = "1"), barcodeOwner = Fixtures.NAME_120, errors = setOf(ProductError.BARCODE_IN_USE)))
                },
                GuardCase("Producto: nueva categoría", full) { Editor(ProductEditorDraft(form = ProductForm(name = "Pan", price = "5"), newCategory = Fixtures.NAME_60)) },
                GuardCase("Producto: nueva categoría (teclado)", GuardMatrix.KEYBOARD) { Editor(ProductEditorDraft(form = ProductForm(name = "Pan", price = "5"), newCategory = Fixtures.NAME_60)) },
                GuardCase("Producto: editar", full) { Editor(editFull) },
                GuardCase("Producto: editar (teclado)", GuardMatrix.KEYBOARD) { Editor(editFull) },
                GuardCase("Producto: editar (catálogo)", full) { Editor(ProductEditorDraft(), catalogOnly = true) },
                GuardCase("Producto: editar, precio abierto (catálogo, teclado)", GuardMatrix.KEYBOARD) { Editor(ProductEditorDraft("p1", ProductForm(name = Fixtures.NAME_200, pricing = Pricing.OPEN, price = "12345.67", isQuick = true)), catalogOnly = true) },
                GuardCase("Producto: contar", full) { CountDialog(CountDraft2("p1", StockAction.COUNT, false, "12345.678", Fixtures.NAME_200), object : InventoryActions {}) },
                GuardCase("Producto: contar (teclado)", GuardMatrix.KEYBOARD) { CountDialog(CountDraft2("p1", StockAction.COUNT, false, "12345.678", Fixtures.NAME_200), object : InventoryActions {}) },
                GuardCase("Producto: conteo inicial", full) { CountDialog(CountDraft2("p1", StockAction.COUNT, true, "", ""), object : InventoryActions {}) },
                GuardCase("Producto: dar de baja", full) { ConfirmActiveDialog(false, object : InventoryActions {}) },
                GuardCase("Compras: lista", full) { Purchases(PurchasesUi()) },
                GuardCase("Compras: solo lo que se debe", full) { Purchases(PurchasesUi(onlyOwed = true, supplierFilter = "sp1")) },
                GuardCase("Compras: vacío", full) { Purchases(PurchasesUi(), purchases = emptyList()) },
                GuardCase("Compras: proveedores", full) { Purchases(PurchasesUi(tab = PurchasesTab.SUPPLIERS)) },
                GuardCase("Compras: nueva compra", full) {
                    Purchases(PurchasesUi(draft = PurchaseDraft(supplierId = null, supplierName = Fixtures.NAME_120, lines = Fixtures.purchaseLines, paid = "99999999.99", note = Fixtures.NAME_200, error = true)))
                },
                GuardCase("Compras: nueva compra (teclado)", GuardMatrix.KEYBOARD) {
                    Purchases(PurchasesUi(draft = PurchaseDraft(supplierId = null, supplierName = Fixtures.NAME_120, lines = Fixtures.purchaseLines, paid = "99999999.99", note = Fixtures.NAME_200, error = true)))
                },
                GuardCase("Compras: nueva compra sin líneas", full) { Purchases(PurchasesUi(draft = PurchaseDraft(supplierId = "sp1"))) },
                GuardCase("Compras: detalle", full) { Purchases(PurchasesUi(detailId = "pu1")) },
                GuardCase("Compras: detalle anulada", full) { Purchases(PurchasesUi(detailId = "pu4")) },
                GuardCase("Compras: pagar", full) { SupplierPayDialog(PayDraft("pu1", null, Fixtures.NAME_120, Fixtures.HUGE, "99999999.99", "BANK"), object : PurchasesActions {}) },
                GuardCase("Compras: pagar (teclado)", GuardMatrix.KEYBOARD) { SupplierPayDialog(PayDraft("pu1", null, Fixtures.NAME_120, Fixtures.HUGE, "99999999.99", "BANK"), object : PurchasesActions {}) },
                GuardCase("Compras: anular", full) { Purchases(PurchasesUi(void = VoidDraft("pu1", null, Fixtures.NAME_200))) },
                GuardCase("Compras: proveedor", full) { SupplierDialog(SupplierDraft("sp1", Fixtures.NAME_120, Fixtures.PHONE_30, Fixtures.NAME_200, true), object : PurchasesActions {}) },
                GuardCase("Compras: proveedor (teclado)", GuardMatrix.KEYBOARD) { SupplierDialog(SupplierDraft("sp1", Fixtures.NAME_120, Fixtures.PHONE_30, Fixtures.NAME_200, true), object : PurchasesActions {}) },
                GuardCase("Compras: elegir producto", full) { PickDialog(PurchaseDraft(picking = true, pickQuery = Fixtures.NAME_60), Fixtures.products, object : PurchasesActions {}) },
                GuardCase("Compras: elegir producto (teclado)", GuardMatrix.KEYBOARD) { PickDialog(PurchaseDraft(picking = true, pickQuery = Fixtures.NAME_60), Fixtures.products, object : PurchasesActions {}) },
                GuardCase("Compras: línea", full) { LineDialog(LineDraft(null, Fixtures.NAME_200, "12345.678", "99999999.99"), true, object : PurchasesActions {}) },
                GuardCase("Compras: línea (teclado)", GuardMatrix.KEYBOARD) { LineDialog(LineDraft(null, Fixtures.NAME_200, "12345.678", "99999999.99"), true, object : PurchasesActions {}) },
            ),
        )
        runner.assertClean()
    }

    private val editFull = ProductEditorDraft(
        "p1",
        ProductForm(Fixtures.NAME_200, Fixtures.CODE_LONG, "1234567890123456", Fixtures.NAME_120, "c3", Pricing.BY_WEIGHT, "KG", "99999999.99", "1234567.89", true, true, "12345.678"),
        wasTracked = true,
    )

    @Composable private fun Editor(d: ProductEditorDraft, catalogOnly: Boolean = false) = EditorDialog(d, Fixtures.productCategories, catalogOnly, object : InventoryActions {})

    @Composable private fun Inventory(ui: InventoryUi, items: List<com.cuadra.caja.data.local.ProductStock> = Fixtures.stock, catalogOnly: Boolean = false, review: Int = 3) =
        InventoryContent(ui, items, review, null, true, "America/Managua", catalogOnly, object : InventoryActions {}, {})

    @Composable private fun Detail(catalogOnly: Boolean, canDelete: Boolean, history: HistoryState?) =
        DetailSheet(Fixtures.productDetail, history, canDelete, "America/Managua", catalogOnly, object : InventoryActions {})

    @Composable private fun Purchases(ui: PurchasesUi, purchases: List<com.cuadra.caja.data.local.PurchaseRow> = Fixtures.purchases) =
        PurchasesContent(ui, purchases, Fixtures.suppliers, Fixtures.balances, Fixtures.HUGE, Fixtures.purchaseItems, Fixtures.purchasePayments, Fixtures.products, "America/Managua", object : PurchasesActions {}, {})
}
