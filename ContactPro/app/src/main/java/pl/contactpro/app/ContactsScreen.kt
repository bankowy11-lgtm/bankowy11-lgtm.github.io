@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pl.contactpro.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal fun letterOf(name: String): String {
    val ch = name.trimStart().firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}

@Composable
fun ContactsScreen(
    padding: PaddingValues,
    loading: Boolean,
    filtered: List<ContactItem>,
    query: String,
    onQuery: (String) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnly: (Boolean) -> Unit,
    selectedGroup: String?,
    onClearGroup: () -> Unit,
    onOpenGroups: () -> Unit,
    groupLabel: (ContactItem) -> String?,
    onOpen: (ContactItem) -> Unit,
    onStar: (ContactItem) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNewGroup: () -> Unit,
    onDuplicates: () -> Unit,
    onBackup: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerCount = 3

    val rows: List<Any> = remember(filtered) {
        buildList {
            var last: String? = null
            filtered.forEach { c ->
                val l = letterOf(c.name)
                if (l != last) { add(l); last = l }
                add(c)
            }
        }
    }
    val letterIndex: Map<String, Int> = remember(rows) {
        buildMap { rows.forEachIndexed { i, r -> if (r is String) put(r, headerCount + i) } }
    }
    val showAlphabet = !loading && query.isBlank() && filtered.size > 12

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = if (showAlphabet) 28.dp else 16.dp, bottom = 100.dp)
        ) {
            item {
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedGroup ?: "Kontakty", color = White, fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, "Zmień motyw", tint = White)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Więcej", tint = White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Panel) {
                            DropdownMenuItem(text = { Text("Nowa grupa", color = White) }, onClick = { menu = false; onNewGroup() })
                            DropdownMenuItem(text = { Text("Wykrywanie duplikatów", color = White) }, onClick = { menu = false; onDuplicates() })
                            DropdownMenuItem(text = { Text("Kopia zapasowa", color = White) }, onClick = { menu = false; onBackup() })
                        }
                    }
                }
            }
            item { SearchField(query, onQuery) }
            item {
                LazyRow(
                    Modifier.padding(top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        TabChip("Wszystkie", null, !favoritesOnly && selectedGroup == null) {
                            onFavoritesOnly(false); onClearGroup()
                        }
                    }
                    item {
                        TabChip("Ulubione", Icons.Default.Star, favoritesOnly, iconTint = Gold) { onFavoritesOnly(!favoritesOnly) }
                    }
                    if (selectedGroup != null) {
                        item { TabChip("$selectedGroup  ✕", Icons.Default.Groups, true, onClick = onClearGroup) }
                    } else {
                        item { TabChip("Grupy", Icons.Default.Groups, false, onClick = onOpenGroups) }
                    }
                }
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                filtered.isEmpty() -> item {
                    EmptyState(
                        when {
                            query.isNotBlank() -> "Nie znaleziono kontaktów dla „$query”."
                            favoritesOnly -> "Nie masz jeszcze ulubionych.\nDotknij gwiazdki przy kontakcie."
                            selectedGroup != null -> "Grupa „$selectedGroup” jest pusta.\nOtwórz kontakt i dodaj go do grupy."
                            else -> "Nie ma jeszcze kontaktów.\nDodaj pierwszy przyciskiem +."
                        }
                    )
                }
                else -> items(rows, key = { r -> if (r is ContactItem) r.id else "letter-$r" }) { r ->
                    if (r is ContactItem) {
                        ContactRow(r, groupLabel(r), onClick = { onOpen(r) }, onStar = { onStar(r) })
                    } else {
                        Text(
                            r as String, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }

        if (showAlphabet && letterIndex.size > 1) {
            AlphabetBar(
                letters = letterIndex.keys.toList(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { letter ->
                letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Accent else Panel
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (selected) Color.White else (iconTint ?: Accent), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label, color = if (selected) Color.White else White,
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AlphabetBar(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    var active by remember { mutableStateOf<String?>(null) }
    var heightPx by remember { mutableIntStateOf(1) }
    fun pick(y: Float) {
        val i = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != active) {
            active = l
            onLetter(l)
        }
    }
    Box(modifier) {
        Column(
            Modifier.padding(end = 2.dp)
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pick(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            pick(change.position.y)
                            change.consume()
                        }
                        active = null
                    }
                }
                .padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach {
                Text(
                    it, color = if (it == active) Accent else Muted,
                    fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        active?.let { letter ->
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = (-76).dp)
                    .size(60.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(letter, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onQuery: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Szukaj kontaktów…", color = Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Close, "Wyczyść", tint = Muted)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Panel2,
            unfocusedContainerColor = Panel,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = White,
            unfocusedTextColor = White,
            cursorColor = Accent
        )
    )
}

@Composable
private fun ContactRow(contact: ContactItem, subtitle: String?, onClick: () -> Unit, onStar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(contact, 52)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle ?: contact.phone.ifBlank { "Brak numeru" }, color = Muted, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onStar) {
            Icon(
                if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                if (contact.starred) "Usuń z ulubionych" else "Dodaj do ulubionych",
                tint = if (contact.starred) Gold else Muted.copy(alpha = 0.6f)
            )
        }
    }
}

// ---------- Ulubione ----------

@Composable
fun FavoritesScreen(padding: PaddingValues, favorites: List<ContactItem>, onOpen: (ContactItem) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
        ScreenTitle("Ulubione", plural(favorites.size, "osoba", "osoby", "osób"))
        if (favorites.isEmpty()) {
            EmptyState("Nie masz jeszcze ulubionych.\nDotknij gwiazdki przy kontakcie, aby go tu dodać.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(favorites, key = { it.id }) { c ->
                Column(
                    Modifier.clip(RoundedCornerShape(22.dp)).background(Panel)
                        .clickable { onOpen(c) }.padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactAvatar(c, 68)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallRound(Icons.Default.Call, Green, c.phone.isNotBlank()) { dial(context, c.phone) }
                        SmallRound(Icons.Default.Sms, Accent, c.phone.isNotBlank()) { sms(context, c.phone) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (enabled) color else Panel2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// ---------- Okna ----------

@Composable
fun QrDialog(title: String, vcard: String, onDismiss: () -> Unit) {
    val bitmap = remember(vcard) { runCatching { qrBitmap(vcard) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(), "Kod QR",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)
                    )
                } else {
                    Text("Nie udało się wygenerować kodu.", color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                Text("Zeskanuj aparatem telefonu, aby zapisać kontakt.", color = Muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ZAMKNIJ", color = Accent) } }
    )
}

@Composable
fun AddContactSheet(
    groupTitles: List<String>,
    initialGroup: String?,
    onDismiss: () -> Unit,
    onSystemForm: () -> Unit,
    onSave: (String, String, String, Set<String>, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(setOfNotNull(initialGroup)) }
    var starred by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nowy kontakt", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            FormField(name, { name = it }, "Imię i nazwisko", KeyboardType.Text, KeyboardCapitalization.Words)
            FormField(phone, { phone = it }, "Numer telefonu", KeyboardType.Phone)
            FormField(mail, { mail = it }, "E-mail (opcjonalnie)", KeyboardType.Email)

            Text("Grupy", color = White, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupTitles.forEach { title ->
                    val on = title in groups
                    GroupChip(if (on) "✓ $title" else title, on) {
                        groups = if (on) groups - title else groups + title
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = Gold)
                Spacer(Modifier.width(10.dp))
                Text("Dodaj do ulubionych", color = White, modifier = Modifier.weight(1f))
                Switch(
                    checked = starred, onCheckedChange = { starred = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }

            Button(
                onClick = { onSave(name, phone, mail, groups, starred) },
                enabled = name.isNotBlank() || phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Zapisz kontakt", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onSystemForm, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Więcej pól – otwórz formularz systemowy", color = Accent)
            }
        }
    }
}

@Composable
fun NewGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Nowa grupa", color = White, fontWeight = FontWeight.Bold) },
        text = {
            FormField(title, { title = it }, "Nazwa grupy", KeyboardType.Text, KeyboardCapitalization.Sentences)
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) {
                Text("UTWÓRZ", color = Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANULUJ", color = Muted) } }
    )
}
