package pl.contactpro.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val APP_VERSION = "2.1"

enum class ToolPage { BLOCKING, BACKUP, DUPLICATES, MY_CARD, SETTINGS }

// ---------- Grupy ----------

@Composable
fun GroupsScreen(
    padding: PaddingValues,
    groupTitles: List<String>,
    countOf: (String) -> Int,
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle("Grupy", "Dotknij grupy, aby zobaczyć kontakty") }
        items(groupTitles, key = { it }) { title ->
            val (color, icon) = groupStyle(title)
            val count = countOf(title)
            Column(
                Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))))
                    .clickable { onOpenGroup(title) }.padding(16.dp)
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(Modifier.weight(1f))
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(plural(count, "kontakt", "kontakty", "kontaktów"), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(22.dp))
                    .border(2.dp, Panel2, RoundedCornerShape(22.dp))
                    .clickable(onClick = onNewGroup).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(36.dp))
                Text("Nowa grupa", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- Narzędzia ----------

@Composable
fun ToolsScreen(padding: PaddingValues, blockingActive: Boolean, duplicateCount: Int, onOpen: (ToolPage) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenTitle("Więcej", "Narzędzia i ustawienia") }
        item {
            ToolRow(Icons.Default.Block, Color(0xFFE5484D), "Blokowanie połączeń",
                if (blockingActive) "Ochrona włączona" else "Zablokuj niechciane numery") { onOpen(ToolPage.BLOCKING) }
        }
        item { ToolRow(Icons.Default.Backup, Color(0xFF16A34A), "Backup i przywracanie", "Zapisz swoje kontakty") { onOpen(ToolPage.BACKUP) } }
        item {
            ToolRow(Icons.Default.PersonSearch, Orange, "Wykrywanie duplikatów",
                if (duplicateCount > 0) "Znaleziono: $duplicateCount – połącz podobne" else "Połącz podobne kontakty") { onOpen(ToolPage.DUPLICATES) }
        }
        item { ToolRow(Icons.Default.QrCode2, Purple, "Wizytówki QR", "Udostępnij swój kontakt") { onOpen(ToolPage.MY_CARD) } }
        item { ToolRow(Icons.Default.Settings, Color(0xFF64748B), "Ustawienia", "Motyw, blokada PIN / biometria") { onOpen(ToolPage.SETTINGS) } }
    }
}

@Composable
fun BackupScreen(
    padding: PaddingValues,
    contactCount: Int,
    selectedGroup: String?,
    groupCount: Int,
    onBack: () -> Unit,
    onExportAll: () -> Unit,
    onExportGroup: () -> Unit,
    onImport: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubHeader("Backup", "Kopia zapasowa i przywracanie", onBack) }
        item {
            Text(
                "Zapisz kontakty do pliku .vcf – możesz go trzymać na Dysku Google, wysłać mailem albo przenieść na nowy telefon. Kontakty na koncie Google synchronizują się też automatycznie.",
                color = Muted, fontSize = 14.sp
            )
        }
        item {
            ToolRow(Icons.Default.Upload, Accent, "Eksportuj wszystkie", plural(contactCount, "kontakt", "kontakty", "kontaktów"), onExportAll)
        }
        if (selectedGroup != null) {
            item {
                ToolRow(Icons.Default.Upload, groupStyle(selectedGroup).first, "Eksportuj grupę „$selectedGroup”",
                    plural(groupCount, "kontakt", "kontakty", "kontaktów"), onExportGroup)
            }
        }
        item { ToolRow(Icons.Default.Download, Color(0xFF16A34A), "Przywróć z pliku .vcf", "Import przez systemową aplikację Kontakty", onImport) }
    }
}

@Composable
fun DuplicatesScreen(
    padding: PaddingValues,
    groups: List<List<ContactItem>>,
    onBack: () -> Unit,
    onMerge: (List<ContactItem>) -> Unit,
    onOpen: (ContactItem) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubHeader("Duplikaty", "Ten sam numer albo ta sama nazwa", onBack) }
        if (groups.isEmpty()) {
            item { EmptyState("Nie znaleziono duplikatów. 🎉") }
        }
        items(groups, key = { g -> g.joinToString("-") { it.id.toString() } }) { group ->
            PanelCard {
                group.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(c) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(c, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.phones.joinToString(", ").ifBlank { "Brak numeru" }, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onMerge(group) },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Połącz w jeden kontakt", color = Color.White) }
            }
        }
    }
}

@Composable
fun MyCardScreen(
    padding: PaddingValues,
    myCard: MyCard,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShowQr: () -> Unit
) {
    val qr = remember(myCard) {
        if (myCard.isEmpty) null
        else runCatching { qrBitmap(buildVCard(myCard.name, listOf(myCard.phone), listOf(myCard.email), myCard.company), 600) }.getOrNull()
    }
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth()) { SubHeader("Wizytówka QR", "Pokaż kod – druga osoba go zeskanuje", onBack) }
        Spacer(Modifier.height(12.dp))
        if (qr == null) {
            Text(
                "Uzupełnij swoje dane, a aplikacja zrobi z nich kod QR. Druga osoba skanuje go aparatem i od razu zapisuje Twój kontakt.",
                color = Muted, fontSize = 15.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                Text("Utwórz wizytówkę", color = Color.White)
            }
        } else {
            Image(
                qr.asImageBitmap(), "Kod QR wizytówki",
                modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                    .background(Color.White).clickable(onClick = onShowQr).padding(14.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(myCard.name, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (myCard.company.isNotBlank()) Text(myCard.company, color = Muted, fontSize = 15.sp)
            if (myCard.phone.isNotBlank()) Text(myCard.phone, color = Muted, fontSize = 15.sp)
            if (myCard.email.isNotBlank()) Text(myCard.email, color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Edit, null, tint = Accent)
                Spacer(Modifier.width(8.dp))
                Text("Edytuj dane", color = White)
            }
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

// ---------- Ustawienia ----------

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    pinEnabled: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    onPinToggle: (Boolean) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onChangePin: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SubHeader("Ustawienia", null, onBack) }
        item {
            PanelCard {
                SettingTitle(Icons.Default.Palette, Accent, "Motyw")
                Spacer(Modifier.height(4.dp))
                Text("Systemowy dopasowuje się do ustawień telefonu.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode -> GroupChip(mode.label, themeMode == mode) { onThemeMode(mode) } }
                }
            }
        }
        item {
            PanelCard {
                SettingTitle(Icons.Default.Lock, Purple, "Blokada aplikacji")
                Spacer(Modifier.height(4.dp))
                Text("Chroń kontakty PIN-em lub odciskiem palca. Aplikacja zablokuje się po 30 s w tle.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                SwitchRow("Blokada PIN", if (pinEnabled) "Włączona" else "Wyłączona", pinEnabled, true, onPinToggle)
                SwitchRow(
                    "Odblokowanie biometrią",
                    if (!biometricAvailable) "Brak czytnika lub odcisku w telefonie" else "Odcisk palca lub twarz",
                    biometricEnabled && pinEnabled, pinEnabled && biometricAvailable, onBiometricToggle
                )
                if (pinEnabled) {
                    TextButton(onClick = onChangePin) { Text("Zmień PIN", color = Accent) }
                }
            }
        }
        item {
            PanelCard {
                SettingTitle(Icons.Default.Info, Muted, "Contact Pro $APP_VERSION")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Twoje kontakty nie opuszczają telefonu – aplikacja nie ma serwera ani reklam.",
                    color = Muted, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, color: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) White else Muted, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent)
        )
    }
}
