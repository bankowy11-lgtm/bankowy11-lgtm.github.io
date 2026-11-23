@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pl.contactpro.app

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

internal fun letterOf(name: String): String {
    val ch = name.trimStart().firstOrNull() ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().toString() else "#"
}

@Composable
fun ContactsScreen(
    padding: PaddingValues,
    totalCount: Int,
    loading: Boolean,
    favorites: List<ContactItem>,
    filtered: List<ContactItem>,
    upcoming: List<Pair<ContactItem, Int>>,
    query: String,
    onQuery: (String) -> Unit,
    selectedGroup: String?,
    onSelectGroup: (String?) -> Unit,
    groupTitles: List<String>,
    onOpen: (ContactItem) -> Unit,
    onStar: (ContactItem) -> Unit,
    onNewGroup: () -> Unit,
    onOpenBirthdays: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val showBdays = upcoming.isNotEmpty() && query.isBlank() && selectedGroup == null
    val showFavs = favorites.isNotEmpty() && query.isBlank() && selectedGroup == null
    // Liczba elementów listy przed kontaktami – potrzebna do skakania po literach.
    val headerCount = 3 + (if (showBdays) 1 else 0) + (if (showFavs) 2 else 0) + 2

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
    val letterIndex: Map<String, Int> = remember(rows, headerCount) {
        buildMap { rows.forEachIndexed { i, r -> if (r is String) put(r, headerCount + i) } }
    }
    val showAlphabet = !loading && query.isBlank() && filtered.size > 12

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = if (showAlphabet) 30.dp else 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Header(totalCount) }
            item {
                Text(
                    selectedGroup ?: "Wszystkie kontakty",
                    color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item { SearchField(query, onQuery) }

            if (showBdays) item { BirthdayBanner(upcoming, onOpenBirthdays) }

            if (showFavs) {
                item { SectionHeader("Ulubione", plural(favorites.size, "osoba", "osoby", "osób")) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(favorites, key = { it.id }) { c ->
                            Column(
                                Modifier.width(74.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpen(c) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ContactAvatar(c, 60)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    c.name.substringBefore(" "), color = White, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Lista kontaktów", plural(filtered.size, "wynik", "wyniki", "wyników")) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GroupChip("Wszystkie", selectedGroup == null) { onSelectGroup(null) } }
                    items(groupTitles) { title ->
                        GroupChip(title, selectedGroup == title) {
                            onSelectGroup(if (selectedGroup == title) null else title)
                        }
                    }
                    item { GroupChip("+ Grupa", false, outlinedAccent = true, onClick = onNewGroup) }
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
                            selectedGroup != null -> "Grupa „$selectedGroup” jest pusta.\nOtwórz kontakt i dodaj go do grupy."
                            else -> "Nie ma jeszcze kontaktów.\nDodaj pierwszy przyciskiem „Nowy kontakt”."
                        }
                    )
                }
                else -> items(rows, key = { r -> if (r is ContactItem) r.id else "letter-$r" }) { r ->
                    if (r is ContactItem) {
                        ContactRow(
                            contact = r,
                            onClick = { onOpen(r) },
                            onStar = { onStar(r) },
                            onSms = { sms(context, r.phone) },
                            onCall = { dial(context, r.phone) }
                        )
                    } else {
                        Text(
                            r as String, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp)
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
            Modifier.padding(end = 3.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Panel.copy(alpha = 0.85f))
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
                .padding(vertical = 8.dp, horizontal = 5.dp),
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
private fun Header(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ContactPhone, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("CONTACT PRO", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text("Twoje kontakty pod ręką", color = Muted, fontSize = 13.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(AccentSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("$count", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SearchField(value: String, onQuery: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Szukaj kontaktu lub numeru…", color = Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Accent) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Close, "Wyczyść", tint = Muted)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
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
private fun BirthdayBanner(upcoming: List<Pair<ContactItem, Int>>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PinkSoft)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Cake, null, tint = Pink, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Nadchodzące urodziny", color = White, fontWeight = FontWeight.Bold)
            Text(
                upcoming.take(3).joinToString(" • ") { (c, d) -> "${c.name.substringBefore(" ")} ${whenText(d).lowercase()}" },
                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun ContactRow(
    contact: ContactItem,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onSms: () -> Unit,
    onCall: () -> Unit
) {
    val hasPhone = contact.phone.isNotBlank()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel)
            .clickable(onClick = onClick).padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(contact, 54)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                contact.phone.ifBlank { "Brak numeru telefonu" }, color = Muted, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onStar, modifier = Modifier.size(40.dp)) {
            Icon(
                if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                if (contact.starred) "Usuń z ulubionych" else "Dodaj do ulubionych",
                tint = if (contact.starred) Gold else Muted
            )
        }
        ActionPill(Icons.Default.Sms, "SMS", AccentSoft, Accent, hasPhone, onSms)
        Spacer(Modifier.width(6.dp))
        ActionPill(Icons.Default.Call, "Zadzwoń", GreenSoft, Green, hasPhone, onCall)
    }
}

// ---------- Szczegóły kontaktu ----------

internal fun pickDate(context: android.content.Context, initial: Birthday?, onPicked: (Birthday) -> Unit) {
    val today = LocalDate.now()
    DatePickerDialog(
        context,
        { _, y, m, d -> onPicked(Birthday(m + 1, d, y)) },
        initial?.year ?: (today.year - 30),
        (initial?.month ?: today.monthValue) - 1,
        initial?.day ?: today.dayOfMonth
    ).show()
}

@Composable
fun ContactDetailsSheet(
    contact: ContactItem,
    groupTitles: List<String>,
    isInGroup: (String) -> Boolean,
    onDismiss: () -> Unit,
    onStar: () -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    onSetBirthday: (Birthday) -> Unit,
    onShowQr: () -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactAvatar(contact, 96)
            Spacer(Modifier.height(12.dp))
            Text(contact.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val hasPhone = contact.phone.isNotBlank()
                BigAction(Icons.Default.Call, "Zadzwoń", GreenSoft, Green, hasPhone) { dial(context, contact.phone) }
                BigAction(Icons.Default.Sms, "SMS", AccentSoft, Accent, hasPhone) { sms(context, contact.phone) }
                BigAction(
                    if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                    "Ulubiony", GoldSoft, Gold, true, onStar
                )
                BigAction(Icons.Default.Edit, "Edytuj", Panel2, White, true) { editContact(context, contact) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onShowQr, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.QrCode2, null, tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Kod QR", color = White)
                }
                OutlinedButton(
                    onClick = { shareContact(context, contact) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Udostępnij", color = White)
                }
            }

            Spacer(Modifier.height(14.dp))
            contact.phones.forEach { number ->
                InfoRow(Icons.Default.Call, number, "Telefon") {
                    IconButton(onClick = { sms(context, number) }) { Icon(Icons.Default.Sms, "SMS", tint = Accent) }
                    IconButton(onClick = { dial(context, number) }) { Icon(Icons.Default.Call, "Zadzwoń", tint = Green) }
                }
            }
            contact.emails.forEach { address ->
                InfoRow(Icons.Default.Email, address, "E-mail") {
                    IconButton(onClick = { email(context, address) }) { Icon(Icons.Default.Email, "Napisz", tint = Accent) }
                }
            }
            val b = contact.birthday
            if (b != null) {
                val age = b.turningAge(today)
                val days = b.daysUntil(today)
                InfoRow(
                    Icons.Default.Cake, b.label(),
                    "Urodziny • ${whenText(days)}" + (age?.let { " • kończy ${plural(it, "rok", "lata", "lat")}" } ?: ""),
                    onClick = { pickDate(context, b, onSetBirthday) }
                ) {
                    IconButton(onClick = { pickDate(context, b, onSetBirthday) }) {
                        Icon(Icons.Default.Edit, "Zmień datę", tint = Muted)
                    }
                }
            } else {
                InfoRow(Icons.Default.Cake, "Dodaj urodziny", "Przypomnimy Ci o nich rano", onClick = {
                    pickDate(context, null, onSetBirthday)
                }) {
                    IconButton(onClick = { pickDate(context, null, onSetBirthday) }) {
                        Icon(Icons.Default.Add, "Dodaj", tint = Pink)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Grupy", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text(
                "Dotknij, aby dodać lub usunąć kontakt z grupy.", color = Muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupTitles.forEach { title ->
                    val member = isInGroup(title)
                    GroupChip(if (member) "✓ $title" else title, member) { onToggleGroup(title, !member) }
                }
            }
        }
    }
}

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
                Text(
                    "Zeskanuj aparatem telefonu, aby zapisać kontakt.",
                    color = Muted, fontSize = 13.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ZAMKNIJ", color = Accent) } }
    )
}

// ---------- Nowy kontakt i nowa grupa ----------

@Composable
fun AddContactSheet(
    groupTitles: List<String>,
    initialGroup: String?,
    onDismiss: () -> Unit,
    onSystemForm: () -> Unit,
    onSave: (String, String, String, Set<String>, Boolean, Birthday?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(setOfNotNull(initialGroup)) }
    var starred by remember { mutableStateOf(false) }
    var birthday by remember { mutableStateOf<Birthday?>(null) }

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

            InfoRow(
                Icons.Default.Cake,
                birthday?.label() ?: "Urodziny (opcjonalnie)",
                if (birthday == null) "Dotknij, aby wybrać datę" else "Dotknij, aby zmienić",
                onClick = { pickDate(context, birthday) { birthday = it } }
            ) {
                if (birthday != null) IconButton(onClick = { birthday = null }) {
                    Icon(Icons.Default.Close, "Usuń datę", tint = Muted)
                }
            }

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
                onClick = { onSave(name, phone, mail, groups, starred, birthday) },
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
