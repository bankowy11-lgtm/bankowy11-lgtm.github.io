@file:OptIn(ExperimentalLayoutApi::class)

package pl.contactpro.app

import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun activityText(call: CallEntry): Pair<String, String> {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(call.date))
    val day = when {
        DateUtils.isToday(call.date) -> "Dzisiaj"
        DateUtils.isToday(call.date + DateUtils.DAY_IN_MILLIS) -> "Wczoraj"
        else -> SimpleDateFormat("d MMMM yyyy", Locale("pl")).format(Date(call.date))
    }
    val kind = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> "Połączenie przychodzące"
        CallLog.Calls.OUTGOING_TYPE -> "Połączenie wychodzące"
        CallLog.Calls.MISSED_TYPE -> "Połączenie nieodebrane"
        CallLog.Calls.REJECTED_TYPE -> "Połączenie odrzucone"
        else -> "Połączenie"
    }
    return "$day, $time" to kind
}

@Composable
fun ContactDetailScreen(
    contact: ContactItem,
    groupTitles: List<String>,
    isInGroup: (String) -> Boolean,
    reloadKey: Int,
    onBack: () -> Unit,
    onStar: () -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    onSaveNote: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit
) {
    val context = LocalContext.current
    var details by remember(contact.id) { mutableStateOf(ContactDetails()) }
    var menu by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }

    LaunchedEffect(contact, reloadKey) {
        details = withContext(Dispatchers.IO) {
            runCatching { ContactsRepository.loadDetails(context, contact) }.getOrDefault(details)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoPicked(uri)
    }

    val phones = details.phones.ifEmpty { contact.phones.map { LabeledValue(it, "Telefon") } }
    val emails = details.emails.ifEmpty { contact.emails.map { LabeledValue(it, "E-mail") } }
    val mainPhone = phones.firstOrNull()?.value.orEmpty()
    val mainEmail = emails.firstOrNull()?.value.orEmpty()
    val memberOf = groupTitles.filter(isInGroup)

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć", tint = White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onStar) {
                Icon(
                    if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                    "Ulubiony", tint = if (contact.starred) Gold else White
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Więcej", tint = White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Panel) {
                    MenuItem(Icons.Default.Edit, "Edytuj") { menu = false; editContact(context, contact) }
                    MenuItem(Icons.Default.Share, "Udostępnij") { menu = false; shareContact(context, contact) }
                    MenuItem(Icons.Default.QrCode2, "Kod QR") { menu = false; onShowQr() }
                    MenuItem(Icons.Default.Delete, "Usuń kontakt", Red) { menu = false; confirmDelete = true }
                }
            }
        }

        // Zdjęcie z przyciskiem aparatu
        Box(Modifier.padding(top = 4.dp)) {
            Box(Modifier.clip(CircleShape).border(4.dp, Accent.copy(alpha = 0.5f), CircleShape).padding(4.dp)) {
                key(reloadKey) { Avatar(contact.name, contact.photoFull ?: contact.photo, 140) }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(42.dp).clip(CircleShape).background(Panel)
                    .border(2.dp, Bg, CircleShape)
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, "Zmień zdjęcie", tint = White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(contact.name, color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { showGroups = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Groups, null, tint = Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (memberOf.isEmpty()) "Dodaj do grupy" else memberOf.joinToString(", "),
                color = if (memberOf.isEmpty()) Accent else Muted, fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Default.Call, "Połącz", Green, mainPhone.isNotBlank()) { dial(context, mainPhone) }
            RoundAction(Icons.Default.Sms, "SMS", Accent, mainPhone.isNotBlank()) { sms(context, mainPhone) }
            RoundAction(Icons.AutoMirrored.Filled.Chat, "WhatsApp", WhatsAppGreen, mainPhone.isNotBlank()) { whatsapp(context, mainPhone) }
            RoundAction(Icons.Default.Email, "E-mail", Purple, mainEmail.isNotBlank()) { email(context, mainEmail) }
        }

        Spacer(Modifier.height(20.dp))
        if (phones.isNotEmpty() || emails.isNotEmpty() || details.addresses.isNotEmpty()) {
            DetailCard {
                phones.forEachIndexed { i, p ->
                    if (i > 0) LineDivider()
                    DetailLine(Icons.Default.Call, Accent, p.value, p.label, onClick = { dial(context, p.value) }) {
                        IconButton(onClick = { sms(context, p.value) }) { Icon(Icons.Default.Sms, "SMS", tint = Muted) }
                        IconButton(onClick = { copyToClipboard(context, p.value) }) { Icon(Icons.Default.ContentCopy, "Kopiuj", tint = Muted) }
                    }
                }
                emails.forEachIndexed { i, e ->
                    if (i > 0 || phones.isNotEmpty()) LineDivider()
                    DetailLine(Icons.Default.Email, Accent, e.value, e.label, onClick = { email(context, e.value) }) {
                        IconButton(onClick = { copyToClipboard(context, e.value) }) { Icon(Icons.Default.ContentCopy, "Kopiuj", tint = Muted) }
                    }
                }
                details.addresses.forEachIndexed { i, a ->
                    if (i > 0 || phones.isNotEmpty() || emails.isNotEmpty()) LineDivider()
                    DetailLine(Icons.Default.LocationOn, Accent, a.value, a.label, onClick = { openMap(context, a.value) }) {
                        IconButton(onClick = { openMap(context, a.value) }) { Icon(Icons.Default.LocationOn, "Mapa", tint = Accent) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        DetailCard {
            DetailLine(
                Icons.Default.Description, Gold, "Notatki",
                details.note.ifBlank { "Dotknij, aby dodać notatkę" },
                onClick = { editNote = true }, multiline = true
            ) {
                IconButton(onClick = { editNote = true }) { Icon(Icons.Default.Edit, "Edytuj notatkę", tint = Muted) }
            }
        }
        Spacer(Modifier.height(12.dp))

        details.lastCall?.let { call ->
            val (whenText, kind) = activityText(call)
            DetailCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Schedule, null, tint = Muted, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Ostatnia aktywność", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(whenText, color = Muted, fontSize = 14.sp)
                        Text(kind, color = if (call.isMissed) Red else Green, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (editNote) {
        var text by remember { mutableStateOf(details.note) }
        AlertDialog(
            onDismissRequest = { editNote = false },
            containerColor = Panel,
            title = { Text("Notatka", color = White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("np. Ważna osoba ❤️", color = Muted) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Panel2,
                        focusedTextColor = White, unfocusedTextColor = White, cursorColor = Accent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { editNote = false; onSaveNote(text) }) { Text("ZAPISZ", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("ANULUJ", color = Muted) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Panel,
            title = { Text("Usunąć kontakt?", color = White, fontWeight = FontWeight.Bold) },
            text = { Text("„${contact.name}” zostanie usunięty z telefonu i z konta Google.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("USUŃ", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ANULUJ", color = Muted) } }
        )
    }

    if (showGroups) {
        AlertDialog(
            onDismissRequest = { showGroups = false },
            containerColor = Panel,
            title = { Text("Grupy", color = White, fontWeight = FontWeight.Bold) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupTitles.forEach { title ->
                        val member = isInGroup(title)
                        GroupChip(if (member) "✓ $title" else title, member) { onToggleGroup(title, !member) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroups = false }) { Text("GOTOWE", color = Accent) } }
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = tint ?: White) },
        leadingIcon = { Icon(icon, null, tint = tint ?: Muted) },
        onClick = onClick
    )
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(if (enabled) color else Panel2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (enabled) White else Muted, fontSize = 14.sp)
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel), content = content)
}

@Composable
private fun LineDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(Panel2))
}

@Composable
private fun DetailLine(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    onClick: () -> Unit,
    multiline: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(value, color = White, fontSize = 16.sp, maxLines = if (multiline) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(label, color = Muted, fontSize = 13.sp, maxLines = if (multiline) 4 else 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
