package com.cuadra.caja.domain

import java.net.URLEncoder

enum class MessageKind { CREDIT_NEW, PAYMENT, PAID_OFF, REMINDER, STATEMENT, TICKET }

/**
 * Mensajes de WhatsApp. Cada negocio puede editar los suyos; si no hay uno guardado se usa el de aquí, en el idioma de quien lo envía.
 * Variables: {negocio} {cliente} {fecha} {monto} {detalle} {saldo} {pagado_linea} {dias} {desde}.
 */
object MessageTemplates {
    private val ES = mapOf(
        MessageKind.CREDIT_NEW to "Hola, {cliente}. Le saluda {negocio}.\nHoy {fecha} le fiamos {monto}:\n{detalle}\n{pagado_linea}Gracias por su compra.",
        MessageKind.PAYMENT to "Hola, {cliente}. Recibimos su abono de {monto} el {fecha}.\nSu saldo pendiente es {saldo}. ¡Gracias! — {negocio}",
        MessageKind.PAID_OFF to "Hola, {cliente}. Recibimos su pago de {monto} el {fecha}: ya no debe nada en {negocio}. ¡Gracias!",
        MessageKind.REMINDER to "Hola, {cliente}. Le recordamos con cariño que tiene un saldo de {saldo} en {negocio}{desde}. Cualquier abono es bienvenido. ¡Gracias!",
        MessageKind.STATEMENT to "Hola, {cliente}. Le comparto su estado de cuenta en {negocio}:\n{detalle}\nSaldo pendiente: {saldo}.",
        MessageKind.TICKET to "{negocio} — {fecha}\n{detalle}\nTotal: {monto}\n¡Gracias por su compra!",
    )
    private val EN = mapOf(
        MessageKind.CREDIT_NEW to "Hi {cliente}, this is {negocio}.\nToday ({fecha}) we put {monto} on your credit account:\n{detalle}\n{pagado_linea}Thank you!",
        MessageKind.PAYMENT to "Hi {cliente}, we received your payment of {monto} on {fecha}.\nYour remaining balance is {saldo}. Thank you! — {negocio}",
        MessageKind.PAID_OFF to "Hi {cliente}, we received your payment of {monto} on {fecha}: you no longer owe anything at {negocio}. Thank you!",
        MessageKind.REMINDER to "Hi {cliente}, a friendly reminder that you have a balance of {saldo} at {negocio}{desde}. Any payment is welcome. Thanks!",
        MessageKind.STATEMENT to "Hi {cliente}, here is your account statement at {negocio}:\n{detalle}\nBalance due: {saldo}.",
        MessageKind.TICKET to "{negocio} — {fecha}\n{detalle}\nTotal: {monto}\nThank you!",
    )

    fun default(kind: MessageKind, locale: String): String = (if (locale == "en") EN else ES).getValue(kind)

    /**
     * Sustituye las variables. Una variable desconocida se deja tal cual (así se ve en la vista previa que está mal escrita) y las líneas
     * vacías sobrantes se colapsan, para que una variable sin valor (ej. {pagado_linea}) no deje huecos.
     */
    fun render(template: String, vars: Map<String, String>): String {
        var out = Regex("\\{(\\w+)\\}").replace(template) { m -> vars[m.groupValues[1]] ?: m.value }
        out = out.replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n")
        return out.trim()
    }
}

/** Enlaces para abrir un chat de WhatsApp con el mensaje ya escrito. */
object WhatsAppLinks {
    /** `https://wa.me/<número>?text=<mensaje>`; el espacio va como %20 (no "+"), que es lo que WhatsApp espera. */
    fun chat(phoneDigits: String, text: String): String = "https://wa.me/$phoneDigits?text=" + encode(text)

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** Identificador de chat que WhatsApp acepta en el extra `jid` al compartir una imagen hacia un número. */
    fun jid(phoneDigits: String): String = "$phoneDigits@s.whatsapp.net"
}

/** Por dónde sale un mensaje de texto de WhatsApp. Nunca se envía solo: siempre abre algo y la persona toca «Enviar». */
enum class ShareRoute {
    /** Chat de ese número (`wa.me/<E.164>?text=…`) con el mensaje ya escrito. */
    WHATSAPP_CHAT,
    /** Sin número: WhatsApp abre su selector de contactos con el mensaje listo. */
    WHATSAPP_PICKER,
    /** WhatsApp no está instalado: la hoja de compartir del sistema (correo, SMS, otra app…). */
    SYSTEM_SHARE,
}

object WhatsAppRoutes {
    fun forText(phoneDigits: String?, whatsAppInstalled: Boolean): ShareRoute = when {
        !whatsAppInstalled -> ShareRoute.SYSTEM_SHARE
        phoneDigits != null -> ShareRoute.WHATSAPP_CHAT
        else -> ShareRoute.WHATSAPP_PICKER
    }
}
