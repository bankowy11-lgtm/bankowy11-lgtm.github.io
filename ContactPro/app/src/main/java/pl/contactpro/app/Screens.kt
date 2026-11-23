package pl.contactpro.app

import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Historia połączeń ----------

private fun callTime(millis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    return when {
        DateUtils.isToday(millis) -> "Dziś $time"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Wczoraj $time"
        else -> {
            val sameYear = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR) ==
                Calendar.getInstance().get(Calendar.YEAR)
            SimpleDateFormat(if (sameYear) "d MMM, HH:mm" else "d MMM yyyy", Locale("pl")).format(Date(millis))
        }
    }
}

private fun durationText(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60} min ${seconds % 60}s"
}

@Suppress("DEPRECATION")
private fun callStyle(type: Int): Triple<ImageVector, Color, String> = when (type) {
    CallLog.Calls.INCOMING_TYPE -> Triple(Icons.Default.CallReceived, Green, "Przychodzące")
    CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.Default.CallMade, Accent, "Wychodzące")
    CallLog.Calls.MISSED_TYPE -> Triple(Icons.Default.CallMissed, Red, "Nieodebrane")
    CallLog.Calls.REJECTED_TYPE -> Triple(Icons.Default.CallMissed, Red, "Odrzucone")
    CallLog.Calls.BLOCKED_TYPE -> Triple(Icons.Default.Block, Muted, "Zablokowane")
    CallLog.Calls.VOICEMAIL_TYPE -> Triple(Icons.Default.Voicemail, Muted, "Poczta głosowa")
    else -> Triple(Icons.Default.Call, Muted, "Połączenie")
}

@Composable
fun HistoryScreen(
    padding: PaddingValues,
    hasPermission: Boolean,
    loading: Boolean,
    calls: List<CallEntry>,
    onRequestPermission: () -> Unit,
    onOpenContact: (ContactItem) -> Unit,
    isBlocked: (String) -> Boolean,
    onUnknownNumber: (String) -> Unit
) {
    val context = LocalContext.current
    var missedOnly by remember { mutableStateOf(false) }
    val shown = if (missedOnly) calls.filter { it.isMissed } else calls

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle("Historia", "Ostatnie połączenia") }
        if (!hasPermission) {
            item {
                PanelCard {
                    Icon(Icons.Default.History, null, tint = Accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Pokaż historię połączeń", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Aby wyświetlić ostatnie połączenia i szybko oddzwonić, aplikacja potrzebuje dostępu do rejestru połączeń. Dane zostają w telefonie.",
                        color = Muted, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Zezwól", color = Color.White)
                    }
                }
            }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { GroupChip("Wszystkie", !missedOnly) { missedOnly = false } }
                item {
                    val missed = calls.count { it.isMissed }
                    GroupChip(if (missed > 0) "Nieodebrane ($missed)" else "Nieodebrane", missedOnly) { missedOnly = true }
                }
            }
        }
        when {
            loading && calls.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            shown.isEmpty() -> item { EmptyState(if (missedOnly) "Brak nieodebranych połączeń." else "Historia połączeń jest pusta.") }
            else -> items(shown, key = { it.id }) { call ->
                val (icon, color, label) = callStyle(call.type)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
                        .clickable {
                            if (call.contact != null) onOpenContact(call.contact)
                            else if (call.number.isNotBlank()) onUnknownNumber(call.number)
                        }
                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(call.displayName, call.contact?.photo, 48)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            call.displayName + if (call.count > 1) " (${call.count})" else "",
                            color = if (call.isMissed) Red else White,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, label, tint = color, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            val extra = listOfNotNull(
                                callTime(call.date),
                                durationText(call.duration).takeIf { it.isNotEmpty() }
                            ).joinToString(" • ")
                            Text(extra, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (call.contact == null && call.number.isNotBlank()) {
                            if (isBlocked(call.number)) {
                                Text("Zablokowany • dotknij, aby odblokować", color = Red, fontSize = 11.sp)
                            } else {
                                Text("Dotknij, aby dodać lub zablokować", color = Accent, fontSize = 11.sp)
                            }
                        }
                    }
                    val hasNumber = call.number.isNotBlank()
                    ActionPill(Icons.Default.Sms, "SMS", AccentSoft, Accent, hasNumber) { sms(context, call.number) }
                    Spacer(Modifier.width(6.dp))
                    ActionPill(Icons.Default.Call, "Oddzwoń", GreenSoft, Green, hasNumber) { dial(context, call.number) }
                }
            }
        }
    }
}

// ---------- Urodziny ----------

@Composable
fun BirthdaysScreen(
    padding: PaddingValues,
    birthdays: List<Pair<ContactItem, Int>>,
    notificationsAllowed: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenContact: (ContactItem) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle("Urodziny", "Nie zapomnij o nikim") }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (notificationsAllowed) GreenSoft else PinkSoft)
                    .then(if (notificationsAllowed) Modifier else Modifier.clickable(onClick = onEnableNotifications))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, null, tint = if (notificationsAllowed) Green else Pink)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (notificationsAllowed) "Przypomnienie przyjdzie o 9:00 w dniu urodzin."
                    else "Dotknij, aby włączyć przypomnienia o urodzinach.",
                    color = White, fontSize = 14.sp
                )
            }
        }
        if (birthdays.isEmpty()) {
            item {
                EmptyState("Żaden kontakt nie ma zapisanych urodzin.\nOtwórz kontakt i dotknij „Dodaj urodziny”.")
            }
        } else {
            items(birthdays, key = { it.first.id }) { (c, days) ->
                val b = c.birthday ?: return@items
                val age = b.turningAge(today)
                val isToday = days == 0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(if (isToday) PinkSoft else Panel)
                        .clickable { onOpenContact(c) }
                        .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(c, 50)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            b.label().take(5) + (age?.let { " • kończy ${plural(it, "rok", "lata", "lat")}" } ?: ""),
                            color = Muted, fontSize = 13.sp
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) Pink else if (days <= 7) PinkSoft else Panel2)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            whenText(days), color = if (isToday) Color.White else if (days <= 7) Pink else Muted,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    ActionPill(Icons.Default.Cake, "Złóż życzenia", PinkSoft, Pink, c.phone.isNotBlank()) {
                        sms(context, c.phone, "Wszystkiego najlepszego z okazji urodzin! 🎉")
                    }
                }
            }
        }
    }
}

// ---------- Więcej: wizytówka i kopia zapasowa ----------

@Composable
fun MoreScreen(
    padding: PaddingValues,
    myCard: MyCard,
    contactCount: Int,
    selectedGroup: String?,
    groupCount: Int,
    blockingActive: Boolean,
    blockedCount: Int,
    onOpenBlocking: () -> Unit,
    onEditCard: () -> Unit,
    onShowCardQr: () -> Unit,
    onExportAll: () -> Unit,
    onExportGroup: () -> Unit,
    onImport: () -> Unit
) {
    val qr = remember(myCard) {
        if (myCard.isEmpty) null
        else runCatching { qrBitmap(buildVCard(myCard.name, listOf(myCard.phone), listOf(myCard.email), myCard.company), 480) }.getOrNull()
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Więcej", "Wizytówka, blokowanie i kopia zapasowa") }

        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
                    .clickable(onClick = onOpenBlocking).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Block, null, tint = Red, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Blokowanie połączeń", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        (if (blockingActive) "Włączone" else "Wyłączone") + " • " +
                            plural(blockedCount, "zablokowany numer", "zablokowane numery", "zablokowanych numerów"),
                        color = if (blockingActive) Green else Muted, fontSize = 13.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, null, tint = Accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Moja wizytówka", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEditCard) { Icon(Icons.Default.Edit, "Edytuj", tint = Muted) }
                }
                if (myCard.isEmpty || qr == null) {
                    Text(
                        "Uzupełnij swoje dane, a aplikacja zrobi z nich kod QR. Druga osoba skanuje go aparatem i od razu zapisuje Twój kontakt.",
                        color = Muted, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onEditCard, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Utwórz wizytówkę", color = Color.White)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            qr.asImageBitmap(), "Kod QR wizytówki",
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White).clickable(onClick = onShowCardQr).padding(6.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(myCard.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (myCard.company.isNotBlank()) Text(myCard.company, color = Muted, fontSize = 13.sp)
                            if (myCard.phone.isNotBlank()) Text(myCard.phone, color = Muted, fontSize = 13.sp)
                            if (myCard.email.isNotBlank()) Text(myCard.email, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onShowCardQr, contentPadding = PaddingValues(0.dp)) {
                                Text("Pokaż na pełnym ekranie", color = Accent)
                            }
                        }
                    }
                }
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Backup, null, tint = Green, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Kopia zapasowa", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Zapisz kontakty do pliku .vcf – możesz go trzymać na Dysku Google, wysłać mailem albo przenieść na nowy telefon.",
                    color = Muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.Default.Upload, "Eksportuj wszystkie kontakty", plural(contactCount, "kontakt", "kontakty", "kontaktów"), onExportAll)
                if (selectedGroup != null) {
                    MenuRow(Icons.Default.Upload, "Eksportuj grupę „$selectedGroup”", plural(groupCount, "kontakt", "kontakty", "kontaktów"), onExportGroup)
                }
                MenuRow(Icons.Default.Download, "Przywróć z pliku .vcf", "Kontakty zaimportuje systemowa aplikacja", onImport)
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Contact Pro ${BuildConfigInfo.VERSION}", color = White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Twoje kontakty nie opuszczają telefonu – aplikacja nie ma serwera ani reklam.",
                    color = Muted, fontSize = 13.sp
                )
            }
        }
    }
}

object BuildConfigInfo {
    const val VERSION = "1.4"
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .background(Bg.copy(alpha = 0.5f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun MyCardDialog(initial: MyCard, onDismiss: () -> Unit, onSave: (MyCard) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var mail by remember { mutableStateOf(initial.email) }
    var company by remember { mutableStateOf(initial.company) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Moja wizytówka", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(name, { name = it }, "Imię i nazwisko", KeyboardType.Text, KeyboardCapitalization.Words)
                FormField(phone, { phone = it }, "Telefon", KeyboardType.Phone)
                FormField(mail, { mail = it }, "E-mail", KeyboardType.Email)
                FormField(company, { company = it }, "Firma / stanowisko", KeyboardType.Text, KeyboardCapitalization.Sentences)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(MyCard(name, phone, mail, company)) }) { Text("ZAPISZ", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANULUJ", color = Muted) } }
    )
}
