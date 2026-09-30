package com.cuadra.caja.domain

import com.cuadra.caja.core.model.Money
import com.cuadra.caja.data.local.ProductEntity
import com.cuadra.caja.data.remote.ProductInputDto

/** Cómo se cobra un producto. Son los valores que entiende el servidor (`pricing`). */
object Pricing {
    const val FIXED = "FIXED"
    const val BY_WEIGHT = "BY_WEIGHT"
    const val OPEN = "OPEN"
    val ALL = listOf(FIXED, BY_WEIGHT, OPEN)

    /** Unidades que tiene sentido elegir cuando se vende por peso o medida. */
    val WEIGHT_UNITS = listOf("LB", "KG", "L", "M")
}

/** Lo que la persona escribió en el editor, sin interpretar (texto tal cual). */
data class ProductForm(
    val name: String = "",
    val barcode: String = "",
    val shortCode: String = "",
    val variant: String = "",
    val categoryId: String? = null,
    val pricing: String = Pricing.FIXED,
    val unit: String = "LB",
    val price: String = "",
    val cost: String = "",
    val isQuick: Boolean = false,
    val trackStock: Boolean = false,
    val minStock: String = "",
)

/** Qué está mal en el formulario; la pantalla marca el campo y dice el motivo. */
enum class ProductError { NAME_REQUIRED, PRICE_REQUIRED, PRICE_INVALID, COST_INVALID, MIN_STOCK_INVALID, BARCODE_IN_USE }

sealed interface ProductEditorResult {
    data class Valid(val input: ProductInputDto) : ProductEditorResult
    data class Invalid(val errors: Set<ProductError>) : ProductEditorResult
}

/**
 * Del formulario del editor al cuerpo que se envía al servidor (que reemplaza el producto completo).
 *  · Nombre obligatorio. Precio obligatorio si es fijo o por peso; en «precio abierto» es solo un sugerido (vacío = 0).
 *  · Costo vacío = null, NUNCA 0 (0 significaría «regalado» y falsearía la ganancia). Código y código corto vacíos = null.
 *  · La unidad solo importa por peso; en los demás modos se conserva la que ya tenía el producto (o «UNIT»).
 *  · Lo que el editor no muestra (posición rápida, color, activo) viene del producto existente.
 *  · `barcodeOwner`: nombre del OTRO producto que ya tiene ese código, si lo hay (lo averigua quien llama, con el catálogo local).
 */
object ProductEditor {
    const val MAX_NAME = 200
    const val MAX_BARCODE = 64
    const val MAX_SHORT_CODE = 16
    const val MAX_VARIANT = 120

    fun build(old: ProductEntity?, f: ProductForm, decimals: Int, barcodeOwner: String? = null): ProductEditorResult {
        val errors = linkedSetOf<ProductError>()
        val name = f.name.trim()
        if (name.isEmpty()) errors += ProductError.NAME_REQUIRED

        val pricing = if (f.pricing in Pricing.ALL) f.pricing else Pricing.FIXED
        val priceText = f.price.trim()
        val price: Long = when {
            priceText.isEmpty() -> if (pricing == Pricing.OPEN) 0L else { errors += ProductError.PRICE_REQUIRED; 0L }
            else -> Money.parse(priceText, decimals)?.minor ?: run { errors += ProductError.PRICE_INVALID; 0L }
        }
        val costText = f.cost.trim()
        val cost: Long? = if (costText.isEmpty()) null else Money.parse(costText, decimals)?.minor ?: run { errors += ProductError.COST_INVALID; null }
        val minText = f.minStock.trim()
        val min: Long? = if (minText.isEmpty()) null else Money3.parse(minText, 3) ?: run { errors += ProductError.MIN_STOCK_INVALID; null }
        val barcode = f.barcode.trim().ifEmpty { null }
        if (barcode != null && barcodeOwner != null) errors += ProductError.BARCODE_IN_USE
        if (errors.isNotEmpty()) return ProductEditorResult.Invalid(errors)

        val unit = when {
            pricing == Pricing.BY_WEIGHT -> f.unit.takeIf { it in Pricing.WEIGHT_UNITS } ?: "LB"
            old != null && old.pricing != Pricing.BY_WEIGHT -> old.unit
            else -> "UNIT"
        }
        return ProductEditorResult.Valid(ProductInputDto(
            barcode = barcode?.take(MAX_BARCODE), shortCode = f.shortCode.trim().ifEmpty { null }?.take(MAX_SHORT_CODE), name = name.take(MAX_NAME),
            variant = f.variant.trim().ifEmpty { null }, categoryId = f.categoryId, unit = unit, pricing = pricing, priceMinor = price, costMinor = cost,
            isQuick = f.isQuick, quickPosition = old?.quickPosition, color = old?.color, trackStock = f.trackStock, minStockMilli = min, active = old?.active ?: true,
        ))
    }

    /** El formulario que muestra un producto existente (todo lo que el servidor guarda de él y el editor permite cambiar). */
    fun formOf(p: ProductEntity, decimals: Int): ProductForm = ProductForm(
        name = p.name, barcode = p.barcode.orEmpty(), shortCode = p.shortCode.orEmpty(), variant = p.variant.orEmpty(), categoryId = p.categoryId,
        pricing = if (p.pricing in Pricing.ALL) p.pricing else Pricing.FIXED, unit = p.unit.takeIf { it in Pricing.WEIGHT_UNITS } ?: "LB",
        price = if (p.pricing == Pricing.OPEN && p.priceMinor == 0L) "" else CashTender.toText(p.priceMinor, decimals),
        cost = p.costMinor?.let { CashTender.toText(it, decimals) }.orEmpty(), isQuick = p.isQuick, trackStock = p.trackStock,
        minStock = p.minStockMilli?.let { java.math.BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() }.orEmpty(),
    )
}
