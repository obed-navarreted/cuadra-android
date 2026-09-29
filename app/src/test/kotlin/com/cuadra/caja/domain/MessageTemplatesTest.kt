package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplatesTest {
    private val vars = mapOf("negocio" to "Pulpería La Esquina", "cliente" to "doña Karla", "fecha" to "28/09", "monto" to "C$ 142.50", "detalle" to "· Queso ×2", "saldo" to "C$ 142.50", "dias" to "16", "desde" to " (desde hace 16 días)", "pagado_linea" to "Pagó C$ 200.00. ")

    @Test fun rendersAllTheVariables() {
        val text = MessageTemplates.render(MessageTemplates.default(MessageKind.CREDIT_NEW, "es"), vars)
        assertTrue(text.contains("Hola, doña Karla. Le saluda Pulpería La Esquina."))
        assertTrue(text.contains("le fiamos C$ 142.50"))
        assertTrue(text.contains("Pagó C$ 200.00. Gracias por su compra."))
        assertFalse(text.contains("{"))
    }

    @Test fun anEmptyVariableLeavesNoHole() {
        val text = MessageTemplates.render("Hola\n{pagado_linea}\n\n\n\nAdiós", mapOf("pagado_linea" to ""))
        assertEquals("Hola\n\nAdiós", text)
    }

    @Test fun anUnknownVariableIsLeftVisible() {
        assertEquals("Hola {nombre}", MessageTemplates.render("Hola {nombre}", vars))
    }

    @Test fun everyKindExistsInBothLanguagesWithNoStrayVariables() {
        val known = vars.keys
        for (locale in listOf("es", "en")) for (kind in MessageKind.entries) {
            val t = MessageTemplates.default(kind, locale)
            val used = Regex("\\{(\\w+)}").findAll(t).map { it.groupValues[1] }.toSet()
            assertTrue("$kind/$locale usa una variable desconocida: ${used - known}", known.containsAll(used))
        }
    }

    @Test fun englishTemplatesAreReallyInEnglish() {
        assertTrue(MessageTemplates.default(MessageKind.REMINDER, "en").startsWith("Hi {cliente}"))
        assertTrue(MessageTemplates.default(MessageKind.REMINDER, "es").startsWith("Hola, {cliente}"))
        assertEquals(MessageTemplates.default(MessageKind.REMINDER, "es"), MessageTemplates.default(MessageKind.REMINDER, "fr"))   // idioma desconocido → español
    }

    @Test fun theWhatsAppLinkEncodesSpacesAsPercent20AndAccents() {
        val link = WhatsAppLinks.chat("50588551234", "Hola, doña Karla\nSaldo: C$ 100")
        assertEquals("https://wa.me/50588551234?text=Hola%2C%20do%C3%B1a%20Karla%0ASaldo%3A%20C%24%20100", link)
        assertEquals("50588551234@s.whatsapp.net", WhatsAppLinks.jid("50588551234"))
    }
}
