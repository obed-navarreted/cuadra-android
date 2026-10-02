package com.cuadra.caja.ui.guard

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.cuadra.caja.domain.PromotionError
import com.cuadra.caja.domain.PromotionForm
import com.cuadra.caja.domain.PromotionRule
import com.cuadra.caja.domain.PromotionState
import com.cuadra.caja.ui.PromoNotice
import com.cuadra.caja.ui.PromotionDraft
import com.cuadra.caja.ui.PromotionRow
import com.cuadra.caja.ui.PromotionsActions
import com.cuadra.caja.ui.PromotionsUi
import com.cuadra.caja.ui.screens.NotificationPermissionContent
import com.cuadra.caja.ui.screens.PromotionsContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Productos › Promociones (lista y editor con búsqueda y escaneo), el acceso desde Productos y la explicación del permiso de notificaciones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class GuardPromotionsTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val full = GuardMatrix.FULL
    private val actions = object : PromotionsActions {}

    private val rows = listOf(
        PromotionRow(PromotionRule("pr1", Fixtures.NAME_200, 999, Fixtures.HUGE, Fixtures.products.map { it.id }.toSet()), PromotionState.ACTIVE, Fixtures.products.map { it.name }),
        PromotionRow(PromotionRule("pr2", "Cerveza 3 por C$ 100", 3, 10_000, setOf("p2"), active = false), PromotionState.PAUSED, listOf("Refresco")),
        PromotionRow(PromotionRule("pr3", Fixtures.LONG_WORD, 2, 1, setOf("p7"), startsOn = Fixtures.promoDay.plusDays(30), endsOn = Fixtures.promoDay.plusDays(60)), PromotionState.SCHEDULED, listOf(Fixtures.NAME_120)),
        PromotionRow(PromotionRule("pr4", "Vieja", 12, Fixtures.BIG, setOf("p8"), endsOn = Fixtures.promoDay.minusDays(1)), PromotionState.ENDED, emptyList()),
    )

    private val fixed = Fixtures.products.filter { it.pricing == "FIXED" }
    private val fullDraft = PromotionDraft(
        "pr1", PromotionForm(Fixtures.NAME_200.take(80), "999", "99999999.99", true, Fixtures.promoDay, Fixtures.promoDay.plusDays(400), fixed.map { it.id }), fixed,
        query = "ref", results = Fixtures.searchProducts.take(6), notice = PromoNotice.Already(Fixtures.NAME_200), withDates = true,
    )

    @Test fun promotionScreens() {
        val runner = GuardRunner(rule, "promotions")
        runner.run(
            listOf(
                GuardCase("Promociones: vacía", full) { PromotionsContent(PromotionsUi(), emptyList(), actions, {}, Fixtures.promoDay) },
                GuardCase("Promociones: lista con todos los estados", full) { PromotionsContent(PromotionsUi(), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Promoción: nueva (vacía)", full) { PromotionsContent(PromotionsUi(PromotionDraft()), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Promoción: nueva (teclado)", GuardMatrix.KEYBOARD) { PromotionsContent(PromotionsUi(PromotionDraft()), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Promoción: con errores", full) {
                    PromotionsContent(PromotionsUi(PromotionDraft(form = PromotionForm("", "1", "", startsOn = Fixtures.promoDay, endsOn = Fixtures.promoDay.minusDays(3)), errors = PromotionError.entries.toSet(), withDates = true,
                        notice = PromoNotice.UnknownCode(Fixtures.CODE_LONG))), rows, actions, {}, Fixtures.promoDay)
                },
                GuardCase("Promoción: editar con todo (nombres largos, búsqueda, aviso)", full) { PromotionsContent(PromotionsUi(fullDraft), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Promoción: editar con todo (teclado)", GuardMatrix.KEYBOARD) { PromotionsContent(PromotionsUi(fullDraft), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Promoción: sin descuento (aviso naranja) y producto no apto", full) {
                    PromotionsContent(PromotionsUi(PromotionDraft(form = PromotionForm("Agua", "3", "100", productIds = listOf("p7")), products = listOf(Fixtures.products[6]), notice = PromoNotice.NotEligible(Fixtures.NAME_120))), rows, actions, {}, Fixtures.promoDay)
                },
                GuardCase("Promoción: borrar", full) { PromotionsContent(PromotionsUi(fullDraft.copy(confirmDelete = true)), rows, actions, {}, Fixtures.promoDay) },
                GuardCase("Catálogo: con acceso a Promociones", full) {
                    com.cuadra.caja.ui.screens.InventoryContent(com.cuadra.caja.ui.InventoryUi(), Fixtures.stock, 3, null, true, "America/Managua", true, object : com.cuadra.caja.ui.InventoryActions {}, {}, onPromotions = {})
                },
                GuardCase("Permiso de notificaciones: explicación", full) { NotificationPermissionContent({}, {}) },
            ),
        )
        runner.assertClean()
    }
}
