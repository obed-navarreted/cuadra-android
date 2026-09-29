package com.cuadra.caja

import com.cuadra.caja.core.model.Currency
import com.cuadra.caja.core.model.Money
import com.cuadra.caja.domain.AmountEntry
import com.cuadra.caja.domain.Barcodes
import com.cuadra.caja.domain.MessageKind
import com.cuadra.caja.domain.MessageTemplates
import com.cuadra.caja.domain.PhoneNumbers
import com.cuadra.caja.domain.PhoneResult
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La lógica pura se prueba en la JVM del computador, pero Android usa otro motor de expresiones regulares (ICU) y otra librería de formato:
 * algo que compila en escritorio puede fallar en el teléfono (así pasó con `\{(\w+)}`). Estas pruebas corren en el dispositivo.
 */
class DomainOnDeviceTest {
    @Test fun templatesRenderWithTheDeviceRegexEngine() {
        val text = MessageTemplates.render(MessageTemplates.default(MessageKind.REMINDER, "es"), mapOf("cliente" to "doña Karla", "saldo" to "C$ 100.00", "negocio" to "La Esquina", "dias" to "16", "desde" to " (desde hace 16 días)"))
        assertEquals("Hola, doña Karla. Le recordamos con cariño que tiene un saldo de C$ 100.00 en La Esquina (desde hace 16 días). Cualquier abono es bienvenido. ¡Gracias!", text)
    }

    @Test fun everyDefaultTemplateRendersInBothLanguages() {
        for (locale in listOf("es", "en")) for (kind in MessageKind.entries) {
            val out = MessageTemplates.render(MessageTemplates.default(kind, locale), emptyMap())
            assertEquals(true, out.isNotBlank())
        }
    }

    @Test fun moneyFormatsWithTheDeviceLocaleData() {
        assertEquals("C$ 1,234.50", Money(123450).format(Currency.of("NIO"), Locale.forLanguageTag("es-NI")))
        assertEquals("C$ 1,234.50", Money(123450).format(Currency.of("NIO"), Locale.forLanguageTag("en-US")))
    }

    @Test fun parsersAndNormalizersWorkOnDevice() {
        assertEquals(Money(8550), Money.parse("85,50", 2))
        assertEquals(PhoneResult.Valid("50588551234"), PhoneNumbers.normalize("8855 1234", "NI"))
        assertEquals(listOf("012345678905", "0012345678905"), Barcodes.forms("012345678905"))
        assertEquals(6750L, AmountEntry().digit('0', 2).dot(2).digit('7', 2).digit('5', 2).times().digit('9', 2).digit('0', 2).totalMinor(2))
    }
}
