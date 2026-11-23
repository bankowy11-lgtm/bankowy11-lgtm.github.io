package pl.contactpro.app

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class MyCard(val name: String = "", val phone: String = "", val email: String = "", val company: String = "") {
    val isEmpty get() = name.isBlank() && phone.isBlank() && email.isBlank()
}

object MyCardStore {
    private const val PREFS = "my_card"

    fun load(context: Context): MyCard {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MyCard(
            p.getString("name", "").orEmpty(),
            p.getString("phone", "").orEmpty(),
            p.getString("email", "").orEmpty(),
            p.getString("company", "").orEmpty()
        )
    }

    fun save(context: Context, card: MyCard) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", card.name.trim())
            .putString("phone", card.phone.trim())
            .putString("email", card.email.trim())
            .putString("company", card.company.trim())
            .apply()
    }
}

private fun esc(s: String) = s.trim()
    .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

fun buildVCard(name: String, phones: List<String>, emails: List<String>, company: String = ""): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val family = if (parts.size > 1) parts.last() else ""
    val given = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.firstOrNull().orEmpty()
    return buildList {
        add("BEGIN:VCARD")
        add("VERSION:3.0")
        add("N:${esc(family)};${esc(given)};;;")
        add("FN:${esc(name)}")
        phones.filter { it.isNotBlank() }.forEach { add("TEL;TYPE=CELL:${it.trim()}") }
        emails.filter { it.isNotBlank() }.forEach { add("EMAIL:${it.trim()}") }
        if (company.isNotBlank()) add("ORG:${esc(company)}")
        add("END:VCARD")
    }.joinToString("\r\n")
}

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1
    )
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val w = m.width
    val h = m.height
    val pixels = IntArray(w * h) { i ->
        if (m.get(i % w, i / w)) 0xFF0A1020.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
