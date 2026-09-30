package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditorTest {
    @Test fun unknownVariablesAreListedOnceAndKnownOnesAreNot() {
        assertEquals(listOf("nombre", "otra"), TemplateEditor.unknownVariables("Hola {nombre} {cliente} {nombre} {otra} {saldo}"))
        assertTrue(TemplateEditor.unknownVariables("Hola {cliente}, debes {saldo} en {negocio}").isEmpty())
    }

    @Test fun everyVariableTheServerDocumentsIsKnown() {
        assertEquals(setOf("negocio", "cliente", "fecha", "monto", "detalle", "saldo", "pagado_linea", "dias", "desde"), TemplateEditor.VARIABLES.toSet())
    }

    @Test fun previewUsesTheSameRendererAsTheSender() {
        val sample = TemplateEditor.sample("Mi Tienda", "29 sep 2026") { "C$ ${it / 100}" }
        val p = TemplateEditor.preview("Hola {cliente}, en {negocio} debes {saldo}. {pagado_linea}\n\n\n\nGracias {x}", sample)
        assertEquals("Hola Marta, en Mi Tienda debes C$ 1402.\n\nGracias {x}", p)
    }

    @Test fun effectiveTextIsTheStoredOneOrTheFactoryOne() {
        assertEquals("propio", TemplateEditor.effective("propio", MessageKind.REMINDER, "es"))
        assertEquals(MessageTemplates.default(MessageKind.REMINDER, "es"), TemplateEditor.effective(null, MessageKind.REMINDER, "es"))
        assertEquals(MessageTemplates.default(MessageKind.REMINDER, "en"), TemplateEditor.effective("  ", MessageKind.REMINDER, "en"))
    }

    @Test fun canSaveOnlyWhenTheTextIsNewAndWithinLimits() {
        val factory = MessageTemplates.default(MessageKind.PAYMENT, "es")
        assertFalse(TemplateEditor.canSave(factory, null, MessageKind.PAYMENT, "es"))          // sin cambios
        assertTrue(TemplateEditor.canSave("$factory!", null, MessageKind.PAYMENT, "es"))
        assertFalse(TemplateEditor.canSave("propio", "propio", MessageKind.PAYMENT, "es"))     // ya guardado
        assertTrue(TemplateEditor.canSave("otro", "propio", MessageKind.PAYMENT, "es"))
        assertFalse(TemplateEditor.canSave("   ", "propio", MessageKind.PAYMENT, "es"))
        assertFalse(TemplateEditor.canSave("x".repeat(TemplateEditor.BODY_MAX + 1), null, MessageKind.PAYMENT, "es"))
        assertTrue(TemplateEditor.canSave("x".repeat(TemplateEditor.BODY_MAX), null, MessageKind.PAYMENT, "es"))
    }
}
