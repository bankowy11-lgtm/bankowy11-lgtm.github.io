package pl.contactpro.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class Palette(
    val dark: Boolean,
    val bg: Color, val panel: Color, val panel2: Color, val barBg: Color,
    val accent: Color, val accentSoft: Color, val muted: Color, val text: Color,
    val green: Color, val greenSoft: Color, val gold: Color, val goldSoft: Color,
    val red: Color, val pink: Color, val pinkSoft: Color
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0A1020), panel = Color(0xFF151F33), panel2 = Color(0xFF223049), barBg = Color(0xFF0D1526),
    accent = Color(0xFF1E8FFF), accentSoft = Color(0xFF1B3557), muted = Color(0xFF9AA9BF), text = Color(0xFFF4F7FC),
    green = Color(0xFF2ED47A), greenSoft = Color(0xFF173B2C), gold = Color(0xFFFFC23D), goldSoft = Color(0xFF3A3220),
    red = Color(0xFFFF5A5F), pink = Color(0xFFFF7AB6), pinkSoft = Color(0xFF3D1F33)
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF2F5FA), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFE2E8F1), barBg = Color(0xFFFFFFFF),
    accent = Color(0xFF1A73E8), accentSoft = Color(0xFFDCEAFF), muted = Color(0xFF5D6B80), text = Color(0xFF111827),
    green = Color(0xFF15A04A), greenSoft = Color(0xFFDDF5E6), gold = Color(0xFFE0A100), goldSoft = Color(0xFFFFF1CC),
    red = Color(0xFFE5484D), pink = Color(0xFFD63F8C), pinkSoft = Color(0xFFFBE2EE)
)

/** Aktualna paleta – odczyt w kompozycji automatycznie odświeża ekran po zmianie motywu. */
var currentPalette by mutableStateOf(DarkPalette)

internal val Bg get() = currentPalette.bg
internal val Panel get() = currentPalette.panel
internal val Panel2 get() = currentPalette.panel2
internal val BarBg get() = currentPalette.barBg
internal val Accent get() = currentPalette.accent
internal val AccentSoft get() = currentPalette.accentSoft
internal val Muted get() = currentPalette.muted
internal val White get() = currentPalette.text
internal val Green get() = currentPalette.green
internal val GreenSoft get() = currentPalette.greenSoft
internal val Gold get() = currentPalette.gold
internal val GoldSoft get() = currentPalette.goldSoft
internal val Red get() = currentPalette.red
internal val Pink get() = currentPalette.pink
internal val PinkSoft get() = currentPalette.pinkSoft

enum class ThemeMode(val label: String) { SYSTEM("Systemowy"), LIGHT("Jasny"), DARK("Ciemny") }

object ThemeStore {
    fun load(context: Context): ThemeMode =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", mode.name).apply()
    }
}

private val AvatarColors = listOf(
    Color(0xFF8E6AD8), Color(0xFF1F8E9E), Color(0xFF9B7240), Color(0xFFB86B86),
    Color(0xFF3D7BD9), Color(0xFF2E9E6B), Color(0xFFC0703A)
)

internal fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

internal fun initials(name: String): String {
    val parts = name.split(' ', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
internal fun Avatar(name: String, photo: Uri?, size: Int) {
    val context = LocalContext.current
    var bitmap by remember(photo) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photo) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                photo?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape)
                .background(AvatarColors[abs(name.hashCode() % AvatarColors.size)]),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(name), color = Color.White, fontSize = (size / 2.6).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ContactAvatar(contact: ContactItem, size: Int) = Avatar(contact.name, contact.photo, size)

@Composable
internal fun SectionHeader(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(trailing, color = Muted, fontSize = 14.sp)
    }
}

@Composable
internal fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
internal fun GroupChip(label: String, selected: Boolean, outlinedAccent: Boolean = false, onClick: () -> Unit) {
    val border = when {
        selected -> null
        outlinedAccent -> BorderStroke(1.dp, Accent)
        else -> BorderStroke(1.dp, Panel2)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Accent else Color.Transparent,
        border = border
    ) {
        Text(
            label,
            color = when {
                selected -> Color.White
                outlinedAccent -> Accent
                else -> White
            },
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

@Composable
internal fun ActionPill(icon: ImageVector, label: String, bg: Color, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 46.dp, height = 40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (enabled) bg else Panel2.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (enabled) tint else Muted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun BigAction(icon: ImageVector, label: String, bg: Color, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (enabled) bg else Panel2.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (enabled) tint else Muted.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (enabled) White else Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(Bg.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(value, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Muted, fontSize = 11.sp)
        }
        actions()
    }
}

@Composable
internal fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp),
        content = content
    )
}

@Composable
internal fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 14.sp)
    }
}

@Composable
internal fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Panel2,
            focusedLabelColor = Accent, unfocusedLabelColor = Muted,
            focusedTextColor = White, unfocusedTextColor = White, cursorColor = Accent
        )
    )
}

// ---------- Akcje systemowe ----------

internal fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

internal fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(context, "Brak aplikacji do obsługi tej akcji")
    } catch (_: SecurityException) {
        toast(context, "Brak uprawnień do tej akcji")
    }
}

internal fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
}

internal fun sms(context: Context, phone: String, body: String? = null) {
    if (phone.isBlank()) return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
    if (body != null) intent.putExtra("sms_body", body)
    safeStart(context, intent)
}

internal fun email(context: Context, address: String) {
    safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
}

internal fun editContact(context: Context, contact: ContactItem) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(ContactsRepository.lookupUri(contact), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        putExtra("finishActivityOnSaveCompleted", true)
    }
    safeStart(context, intent)
}

internal fun openInsertContact(context: Context, phone: String? = null) {
    val intent = Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE)
    if (phone != null) intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone)
    safeStart(context, intent)
}

internal fun shareContact(context: Context, contact: ContactItem) {
    val key = contact.lookupKey
    val intent = if (key != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
            putExtra(Intent.EXTRA_STREAM, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${contact.name}\n${contact.phones.joinToString("\n")}")
        }
    }
    safeStart(context, Intent.createChooser(intent, "Udostępnij kontakt"))
}

internal fun importVcf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/x-vcard")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    safeStart(context, intent)
}

// ---------- Dodatki do wersji 2.0 ----------

internal val Purple = Color(0xFF8B5CF6)
internal val Orange = Color(0xFFF2850F)
internal val Teal = Color(0xFF14B8A6)
internal val WhatsAppGreen = Color(0xFF25D366)

/** Kolor i ikona kafelka grupy. */
internal fun groupStyle(title: String): Pair<Color, ImageVector> = when (title.lowercase()) {
    "rodzina" -> Color(0xFFE8385F) to androidx.compose.material.icons.Icons.Default.Home
    "znajomi" -> Color(0xFF2EAA4F) to androidx.compose.material.icons.Icons.Default.Groups
    "praca" -> Color(0xFF2D6FE0) to androidx.compose.material.icons.Icons.Default.Work
    "klienci" -> Color(0xFFF07C12) to androidx.compose.material.icons.Icons.Default.People
    else -> {
        val colors = listOf(Color(0xFF8B5CF6), Color(0xFF0EA5A5), Color(0xFFDB2777), Color(0xFF65A30D), Color(0xFF0284C7))
        colors[abs(title.lowercase().hashCode() % colors.size)] to androidx.compose.material.icons.Icons.Default.Folder
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("Contact Pro", text))
    if (android.os.Build.VERSION.SDK_INT < 33) toast(context, "Skopiowano")
}

internal fun whatsapp(context: Context, phone: String) {
    if (phone.isBlank()) return
    val trimmed = phone.trim()
    val digits = trimmed.filter { it.isDigit() }
    val intl = when {
        trimmed.startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        digits.length == 9 -> "48$digits"
        else -> digits
    }
    safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl")))
}

internal fun openMap(context: Context, address: String) {
    safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")))
}

@Composable
internal fun SubHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Wróć", tint = White)
        }
        Column {
            Text(title, color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 14.sp)
        }
    }
}

/** Wiersz menu z kolorowym kwadratem ikony – jak w „Narzędziach”. */
@Composable
internal fun ToolRow(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Icon(androidx.compose.material.icons.Icons.Default.ChevronRight, null, tint = Muted)
    }
}
