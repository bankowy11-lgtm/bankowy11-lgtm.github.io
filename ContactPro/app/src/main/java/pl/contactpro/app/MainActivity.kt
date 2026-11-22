@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pl.contactpro.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val Bg = Color(0xFF0A1020)
private val Panel = Color(0xFF151F33)
private val Panel2 = Color(0xFF223049)
private val Accent = Color(0xFF1E8FFF)
private val AccentSoft = Color(0xFF1B3557)
private val Muted = Color(0xFF9AA9BF)
private val White = Color(0xFFF4F7FC)
private val Green = Color(0xFF2ED47A)
private val GreenSoft = Color(0xFF173B2C)
private val Gold = Color(0xFFFFC23D)

private val AvatarColors = listOf(
    Color(0xFF8E6AD8), Color(0xFF1F8E9E), Color(0xFF9B7240), Color(0xFFB86B86),
    Color(0xFF3D7BD9), Color(0xFF2E9E6B), Color(0xFFC0703A)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent { ContactProApp() }
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun ContactProApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var canRead by remember { mutableStateOf(granted(context, Manifest.permission.READ_CONTACTS)) }
    var canWrite by remember { mutableStateOf(granted(context, Manifest.permission.WRITE_CONTACTS)) }
    var data by remember { mutableStateOf(ContactsData()) }
    var loading by remember { mutableStateOf(canRead) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        canRead = granted(context, Manifest.permission.READ_CONTACTS)
        canWrite = granted(context, Manifest.permission.WRITE_CONTACTS)
    }

    // Odśwież po powrocie do aplikacji (np. po edycji w systemowym formularzu).
    LifecycleResumeEffect(Unit) {
        canRead = granted(context, Manifest.permission.READ_CONTACTS)
        canWrite = granted(context, Manifest.permission.WRITE_CONTACTS)
        reloadKey++
        onPauseOrDispose { }
    }

    LaunchedEffect(canRead, reloadKey) {
        if (canRead) {
            data = withContext(Dispatchers.IO) {
                runCatching { ContactsRepository.load(context) }.getOrElse { data }
            }
            loading = false
        }
    }

    fun requestPermissions() = permissionLauncher.launch(
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    )

    fun write(successMessage: String? = null, block: (Context) -> Unit) {
        if (!canWrite) {
            toast(context, "Zezwól aplikacji na zmianę kontaktów")
            requestPermissions()
            return
        }
        scope.launch {
            val error = withContext(Dispatchers.IO) { runCatching { block(context) }.exceptionOrNull() }
            if (error != null) toast(context, "Nie udało się: ${error.message ?: "błąd"}")
            else successMessage?.let { toast(context, it) }
            reloadKey++
        }
    }

    val groupTitles = remember(data.groups) {
        (ContactsRepository.DEFAULT_GROUPS + data.groups.map { it.title }).distinctBy { it.lowercase() }
    }
    fun idsOf(title: String) =
        data.groups.firstOrNull { it.title.equals(title, true) }?.ids ?: emptySet()

    val filtered = remember(data, query, selectedGroup) {
        val ids = selectedGroup?.let { idsOf(it) }
        data.contacts.filter { c ->
            (ids == null || c.groupIds.any { it in ids }) && matches(c, query)
        }
    }
    val favorites = remember(data) { data.contacts.filter { it.starred } }
    val selected = selectedId?.let { id -> data.contacts.firstOrNull { it.id == id } }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Panel,
            onBackground = White, onSurface = White, onPrimary = White,
            surfaceVariant = Panel2, onSurfaceVariant = Muted
        )
    ) {
        Scaffold(
            containerColor = Bg,
            floatingActionButton = {
                if (canRead) {
                    ExtendedFloatingActionButton(
                        onClick = { if (canWrite) showAdd = true else openInsertContact(context) },
                        containerColor = Accent,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.PersonAdd, null) },
                        text = { Text("Nowy kontakt", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        ) { padding ->
            if (!canRead) {
                PermissionScreen(Modifier.padding(padding), onGrant = { requestPermissions() })
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Header(data.contacts.size) }
                    item {
                        Text(
                            selectedGroup ?: "Wszystkie kontakty",
                            color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item { SearchField(query, onQuery = { query = it }) }

                    if (favorites.isNotEmpty() && query.isBlank() && selectedGroup == null) {
                        item {
                            SectionHeader("Ulubione", plural(favorites.size, "osoba", "osoby", "osób"))
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(favorites, key = { it.id }) { c ->
                                    Column(
                                        Modifier.width(74.dp).clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedId = c.id }.padding(vertical = 4.dp),
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

                    item {
                        SectionHeader("Lista kontaktów", plural(filtered.size, "wynik", "wyniki", "wyników"))
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                GroupChip("Wszystkie", selectedGroup == null) { selectedGroup = null }
                            }
                            items(groupTitles) { title ->
                                GroupChip(title, selectedGroup == title) {
                                    selectedGroup = if (selectedGroup == title) null else title
                                }
                            }
                            item {
                                GroupChip("+ Grupa", false, outlinedAccent = true) {
                                    if (canWrite) showNewGroup = true else requestPermissions()
                                }
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
                                    selectedGroup != null -> "Grupa „$selectedGroup” jest pusta.\nOtwórz kontakt i dodaj go do grupy."
                                    else -> "Nie ma jeszcze kontaktów.\nDodaj pierwszy przyciskiem „Nowy kontakt”."
                                }
                            )
                        }
                        else -> items(filtered, key = { it.id }) { c ->
                            ContactRow(
                                contact = c,
                                onClick = { selectedId = c.id },
                                onStar = { write { ctx -> ContactsRepository.setStarred(ctx, c.id, !c.starred) } },
                                onSms = { sms(context, c.phone) },
                                onCall = { dial(context, c.phone) }
                            )
                        }
                    }
                }
            }
        }

        if (selected != null) {
            ContactDetailsSheet(
                contact = selected,
                groupTitles = groupTitles,
                isInGroup = { title -> selected.groupIds.any { it in idsOf(title) } },
                onDismiss = { selectedId = null },
                onCall = { dial(context, it) },
                onSms = { sms(context, it) },
                onEmail = { email(context, it) },
                onStar = { write { ctx -> ContactsRepository.setStarred(ctx, selected.id, !selected.starred) } },
                onEdit = { editContact(context, selected) },
                onToggleGroup = { title, member ->
                    write(if (member) "Dodano do grupy $title" else "Usunięto z grupy $title") { ctx ->
                        ContactsRepository.setGroupMembership(ctx, selected, title, idsOf(title), member)
                    }
                }
            )
        }

        if (showAdd) {
            AddContactSheet(
                groupTitles = groupTitles,
                initialGroup = selectedGroup,
                onDismiss = { showAdd = false },
                onSystemForm = { showAdd = false; openInsertContact(context) },
                onSave = { name, phone, mail, groups, star ->
                    showAdd = false
                    write("Zapisano kontakt") { ctx ->
                        ContactsRepository.addContact(ctx, name, phone, mail, groups, star)
                    }
                }
            )
        }

        if (showNewGroup) {
            NewGroupDialog(
                onDismiss = { showNewGroup = false },
                onCreate = { title ->
                    showNewGroup = false
                    write("Utworzono grupę $title") { ctx -> ContactsRepository.createGroup(ctx, title) }
                    selectedGroup = title
                }
            )
        }
    }
}

private fun matches(c: ContactItem, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (c.name.contains(q, ignoreCase = true)) return true
    if (c.emails.any { it.contains(q, ignoreCase = true) }) return true
    val digits = q.filter { it.isDigit() }
    return digits.isNotEmpty() && c.phones.any { it.filter { ch -> ch.isDigit() }.contains(digits) }
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

// ---------- Elementy ekranu ----------

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
private fun SectionHeader(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(trailing, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, outlinedAccent: Boolean = false, onClick: () -> Unit) {
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

@Composable
private fun ActionPill(icon: ImageVector, label: String, bg: Color, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 46.dp, height = 40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (enabled) bg else Panel2.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (enabled) tint else Muted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

private fun initials(name: String): String {
    val parts = name.split(' ', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
private fun ContactAvatar(contact: ContactItem, size: Int) {
    val context = LocalContext.current
    var bitmap by remember(contact.photo) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(contact.photo) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                contact.photo?.let { uri ->
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
            bmp.asImageBitmap(), contentDescription = contact.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape)
                .background(AvatarColors[abs(contact.name.hashCode() % AvatarColors.size)]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials(contact.name), color = Color.White,
                fontSize = (size / 2.6).sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionScreen(modifier: Modifier, onGrant: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Groups, null, tint = Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text("Witaj w Contact Pro", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Aby wyświetlać, dodawać i grupować kontakty, aplikacja potrzebuje dostępu do książki adresowej telefonu. Nie wysyłamy kontaktów na żaden serwer.",
            color = Muted, fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
            Text("Zezwól na dostęp do kontaktów", color = Color.White)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 14.sp)
    }
}

// ---------- Szczegóły kontaktu ----------

@Composable
private fun ContactDetailsSheet(
    contact: ContactItem,
    groupTitles: List<String>,
    isInGroup: (String) -> Boolean,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEmail: (String) -> Unit,
    onStar: () -> Unit,
    onEdit: () -> Unit,
    onToggleGroup: (String, Boolean) -> Unit
) {
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
                BigAction(Icons.Default.Call, "Zadzwoń", GreenSoft, Green, hasPhone) { onCall(contact.phone) }
                BigAction(Icons.Default.Sms, "SMS", AccentSoft, Accent, hasPhone) { onSms(contact.phone) }
                BigAction(
                    if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                    "Ulubiony", Color(0xFF3A3220), Gold, true, onStar
                )
                BigAction(Icons.Default.Edit, "Edytuj", Panel2, White, true, onEdit)
            }

            Spacer(Modifier.height(20.dp))
            if (contact.phones.isEmpty() && contact.emails.isEmpty()) {
                Text("Brak numeru telefonu i adresu e-mail.", color = Muted, fontSize = 13.sp)
            }
            contact.phones.forEach { number ->
                InfoRow(Icons.Default.Call, number, "Telefon") {
                    IconButton(onClick = { onSms(number) }) { Icon(Icons.Default.Sms, "SMS", tint = Accent) }
                    IconButton(onClick = { onCall(number) }) { Icon(Icons.Default.Call, "Zadzwoń", tint = Green) }
                }
            }
            contact.emails.forEach { address ->
                InfoRow(Icons.Default.Email, address, "E-mail") {
                    IconButton(onClick = { onEmail(address) }) { Icon(Icons.Default.Email, "Napisz", tint = Accent) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Grupy", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
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
private fun BigAction(icon: ImageVector, label: String, bg: Color, tint: Color, enabled: Boolean, onClick: () -> Unit) {
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
private fun InfoRow(icon: ImageVector, value: String, label: String, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Bg.copy(alpha = 0.5f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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

// ---------- Nowy kontakt i nowa grupa ----------

@Composable
private fun AddContactSheet(
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
private fun FormField(
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

@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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

// ---------- Akcje systemowe ----------

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    safeStart(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
}

private fun sms(context: Context, phone: String) {
    if (phone.isBlank()) return
    safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")))
}

private fun email(context: Context, address: String) {
    safeStart(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
}

private fun editContact(context: Context, contact: ContactItem) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(ContactsRepository.lookupUri(contact), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        putExtra("finishActivityOnSaveCompleted", true)
    }
    safeStart(context, intent)
}

private fun openInsertContact(context: Context) {
    safeStart(context, Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE))
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(context, "Brak aplikacji do obsługi tej akcji")
    }
}
