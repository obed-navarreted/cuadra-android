package com.cuadra.caja.domain

/** Qué equipo es (para encender solo lo que le sirve). Puro: las pruebas lo comprueban sin Android. */
object DeviceProfile {
    /** Modelos Zebra conocidos por su nombre (TC77, TC52, MC33, EC30, ET40…): solo se usan si el fabricante no vino informado. */
    private val ZEBRA_MODEL = Regex("^(TC|MC|EC|ET|CC|WT|PS|VC|L10)\\d{2,4}[A-Za-z0-9-]*$", RegexOption.IGNORE_CASE)

    /**
     * ¿Es un Zebra (o Symbol, su marca anterior) con lector láser? Sí si el fabricante dice Zebra o Symbol (sin importar mayúsculas), si el paquete
     * de DataWedge está instalado, o si el fabricante no vino y el modelo parece uno de Zebra («TC77»). Un Samsung con modelo «TC…» sigue siendo Samsung.
     */
    fun isZebra(manufacturer: String?, model: String?, hasDataWedge: Boolean): Boolean {
        if (hasDataWedge) return true
        val maker = manufacturer?.trim()?.lowercase().orEmpty()
        if (maker.contains("zebra") || maker.contains("symbol")) return true
        return maker.isEmpty() && model?.trim()?.let { ZEBRA_MODEL.matches(it) } == true
    }
}
