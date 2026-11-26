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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
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

private enum class CallFilter(val label: String) {
    ALL("Wszystkie"), INCOMING("Przychodzące"), MISSED("Nieodebrane"), OUTGOING("Wychodzące")
}

private fun dayLabel(millis: Long): String = when {
    DateUtils.isToday(millis) -> "Dzisiaj"
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Wczoraj"
    else -> {
        val sameYear = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR) ==
            Calendar.getInstance().get(Calendar.YEAR)
        SimpleDateFormat(if (sameYear) "EEEE, d MMMM" else "d MMMM yyyy", Locale("pl")).format(Date(millis))
            .replaceFirstChar { it.uppercase() }
    }
}

/** Liczba nieodebranych połączeń z ostatnich 24 godzin – do plakietki na zakładce. */
fun recentMissedCount(calls: List<CallEntry>): Int {
    val since = System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS
    return calls.filter { it.isMissed && it.date >= since }.sumOf { it.count }
}

@Composable
fun HistoryScreen(
    padding: PaddingValues,
    hasPermission: Boolean,
    loading: Boolean,
    calls: List<CallEntry>,
    onBack: (() -> Unit)?,
    onRequestPermission: () -> Unit,
    onOpenContact: (ContactItem) -> Unit,
    isBlocked: (String) -> Boolean,
    onUnknownNumber: (String) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    val shown = remember(calls, filter) {
        when (filter) {
            CallFilter.ALL -> calls
            CallFilter.INCOMING -> calls.filter { it.type == CallLog.Calls.INCOMING_TYPE }
            CallFilter.MISSED -> calls.filter { it.isMissed }
            CallFilter.OUTGOING -> calls.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
        }
    }
    val rows: List<Any> = remember(shown) {
        buildList {
            var last: String? = null
            shown.forEach { c ->
                val d = dayLabel(c.date)
                if (d != last) { add(d); last = d }
                add(c)
            }
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            if (onBack != null) SubHeader("Ostatnie", "Kto do Ciebie dzwonił", onBack)
            else ScreenTitle("Ostatnie", "Kto do Ciebie dzwonił")
        }
        if (!hasPermission) {
            item {
                PanelCard {
                    Icon(Icons.Default.History, null, tint = Accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Pokaż ostatnie połączenia", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Aby zobaczyć, kto do Ciebie dzwonił, i szybko oddzwonić, aplikacja potrzebuje dostępu do rejestru połączeń. Dane zostają w telefonie.",
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                items(CallFilter.entries) { f ->
                    val label = if (f == CallFilter.MISSED) {
                        val missed = calls.count { it.isMissed }
                        if (missed > 0) "${f.label} ($missed)" else f.label
                    } else f.label
                    GroupChip(label, filter == f) { filter = f }
                }
            }
        }
        when {
            loading && calls.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            shown.isEmpty() -> item {
                EmptyState(
                    when (filter) {
                        CallFilter.ALL -> "Historia połączeń jest pusta."
                        CallFilter.INCOMING -> "Brak połączeń przychodzących."
                        CallFilter.MISSED -> "Brak nieodebranych połączeń. 👍"
                        CallFilter.OUTGOING -> "Brak połączeń wychodzących."
                    }
                )
            }
            else -> items(rows, key = { r -> if (r is CallEntry) r.id else "day-$r" }) { r ->
                if (r !is CallEntry) {
                    Text(
                        r as String, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp)
                    )
                    return@items
                }
                val call = r
                val (icon, color, label) = callStyle(call.type)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
                        .clickable {
                            if (call.contact != null) onOpenContact(call.contact)
                            else if (call.number.isNotBlank()) onUnknownNumber(call.number)
                        }
                        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(call.displayName, call.contact?.photo, 46)
                    Spacer(Modifier.width(12.dp))
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
                                label,
                                timeFormat.format(Date(call.date)),
                                durationText(call.duration).takeIf { it.isNotEmpty() }
                            ).joinToString(" • ")
                            Text(extra, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (call.contact == null && call.number.isNotBlank()) {
                            if (isBlocked(call.number)) {
                                Text("Zablokowany • dotknij, aby odblokować", color = Red, fontSize = 11.sp)
                            } else if (call.cachedName.isNullOrBlank()) {
                                Text("Nieznany numer • dotknij, aby dodać lub zablokować", color = Accent, fontSize = 11.sp)
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
