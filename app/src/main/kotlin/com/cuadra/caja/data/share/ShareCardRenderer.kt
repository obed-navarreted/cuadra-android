package com.cuadra.caja.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.cuadra.caja.R
import com.cuadra.caja.domain.ShareCard
import java.io.File

/**
 * Dibuja la tarjeta (estado de cuenta, recordatorio, comprobante) como una imagen PNG lista para compartir. Se dibuja a mano con Canvas:
 * el resultado es idéntico en cualquier teléfono y no depende de la pantalla ni del tamaño de letra del sistema.
 */
object ShareCardRenderer {
    private const val W = 1080
    private const val PAD = 64
    private const val ROW = 84

    fun render(context: Context, card: ShareCard): File {
        val base = ResourcesCompat.getFont(context, R.font.manrope) ?: Typeface.DEFAULT
        // Manrope es una fuente variable: el peso se pide como variación del mismo archivo (API 26+, el mínimo de la app).
        fun Paint.weight(w: Int) { typeface = base; fontVariationSettings = "'wght' $w" }
        val ink = Color.parseColor("#1A1916")
        val bg = Color.parseColor("#F4F1EA")
        val muted = Color.parseColor("#5E594F")
        val line = Color.parseColor("#E6E1D6")
        val accent = Color.parseColor("#A8460F")

        val header = 250
        val footer = 200
        val height = header + card.lines.size * ROW + footer + PAD
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = ink
        c.drawRoundRect(RectF(PAD.toFloat(), PAD.toFloat(), (W - PAD).toFloat(), (header + 20).toFloat()), 44f, 44f, p)
        p.color = Color.parseColor("#C9C3B6"); p.textSize = 38f; p.weight(600)
        c.drawText(TextUtils.ellipsize(card.business, android.text.TextPaint(p), (W - 2 * PAD - 100).toFloat(), TextUtils.TruncateAt.END).toString(), (PAD + 48).toFloat(), 150f, p)
        p.color = bg; p.textSize = 76f; p.weight(800)
        c.drawText(card.title, (PAD + 48).toFloat(), 240f, p)

        var y = header + 90f
        card.subtitle?.let {
            p.color = ink; p.textSize = 46f; p.weight(700)
            c.drawText(TextUtils.ellipsize(it, android.text.TextPaint(p), (W - 2 * PAD).toFloat(), TextUtils.TruncateAt.END).toString(), PAD.toFloat(), y, p)
        }
        y += 30f
        for (l in card.lines) {
            y += ROW
            p.color = line; p.strokeWidth = 2f
            c.drawLine(PAD.toFloat(), y - ROW + 26f, (W - PAD).toFloat(), y - ROW + 26f, p)
            p.color = if (l.emphasis) ink else muted; p.textSize = 40f; p.weight(if (l.emphasis) 800 else 500)
            val right = l.right
            val rightW = p.measureText(right)
            c.drawText(right, W - PAD - rightW, y - 12f, p)
            val maxLeft = W - 2 * PAD - rightW - 40f
            c.drawText(TextUtils.ellipsize(l.left, android.text.TextPaint(p), maxLeft, TextUtils.TruncateAt.END).toString(), PAD.toFloat(), y - 12f, p)
        }
        y += 90f
        p.color = muted; p.textSize = 40f; p.weight(700)
        c.drawText(card.totalLabel, PAD.toFloat(), y, p)
        p.color = accent; p.textSize = 78f; p.weight(800)
        c.drawText(card.totalValue, W - PAD - p.measureText(card.totalValue), y + 6f, p)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // Un archivo por envío; los viejos se limpian para no acumular imágenes con nombres de clientes en el teléfono.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.delete() }
        val file = File(dir, "cuadra-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
