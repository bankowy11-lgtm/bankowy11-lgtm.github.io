package pl.contactpro.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF080D18)
private val Panel = Color(0xFF151E30)
private val Panel2 = Color(0xFF1C2940)
private val Accent = Color(0xFF16B9F5)
private val Muted = Color(0xFF9AA9BF)
private val White = Color(0xFFF4F7FC)
private val Green = Color(0xFF18C783)

data class ContactItem(
    val id: Long,
    val name: String,
    val phone: String,
    val photo: Uri?,
    val starred: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ContactProApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ContactItem?>(null) }
    var loading by remember { mutableStateOf(hasPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true
            contacts = withContext(Dispatchers.IO) { loadContacts(context) }
            loading = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Panel,
            onBackground = White,
            onSurface = White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (!hasPermission) {
                PermissionScreen { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopHeader(contacts.size)
                    SearchField(query, onQuery = { query = it }, onClear = { query = "" })
                    if (contacts.isNotEmpty()) {
                        FavoriteStrip(
                            contacts.filter { it.starred }.take(8),
                            onClick = { selected = it }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (favoritesOnly) "ULUBIONE" else "WSZYSTKIE KONTAKTY",
                            color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${contacts.size} kontaktów", color = Muted, fontSize = 12.sp)
                    }
                    val filtered = contacts.filter {
                        (!favoritesOnly || it.starred) &&
                        (it.name.contains(query, true) || it.phone.contains(query, true))
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Accent)
                        }
                        filtered.isEmpty() -> EmptyState(query.isNotBlank())
                        else -> LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { contact ->
                                ContactRow(contact, onClick = { selected = contact }, onCall = { dial(context, contact.phone) })
                            }
                        }
                    }
                    BottomBar(
                        favoritesOnly = favoritesOnly,
                        onContacts = { favoritesOnly = false },
                        onFavorites = { favoritesOnly = true },
                        onAdd = { openInsertContact(context) }
                    )
                }
            }
            selected?.let { contact ->
                ContactDetails(contact, onDismiss = { selected = null }, onCall = { dial(context, contact.phone) })
            }
        }
    }
}

@Composable
private fun TopHeader(count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("CONTACT", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("Pro", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Twoja książka adresowa", color = Muted, fontSize = 13.sp)
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Panel2),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", color = Accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun SearchField(value: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        placeholder = { Text("Szukaj osoby lub numeru…", color = Muted) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Accent) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Wyczyść", tint = Muted)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Panel2,
            focusedTextColor = White,
            unfocusedTextColor = White,
            cursorColor = Accent
        )
    )
}

@Composable
private fun FavoriteStrip(items: List<ContactItem>, onClick: (ContactItem) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFFC857), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ulubione", color = White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { contact ->
                Column(
                    Modifier.width(76.dp).clickable { onClick(contact) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactAvatar(contact, 58)
                    Spacer(Modifier.height(7.dp))
                    Text(contact.name.substringBefore(" "), color = White, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactItem, onClick: () -> Unit, onCall: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(contact, 54)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.phone.ifBlank { "Brak numeru telefonu" }, color = Muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (contact.starred) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFFC857), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onCall, enabled = contact.phone.isNotBlank()) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Call, "Zadzwoń", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun ContactAvatar(contact: ContactItem, size: Int) {
    var bitmap by remember(contact.photo) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(contact.photo) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                contact.photo?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = contact.name,
            modifier = Modifier.size(size.dp).clip(CircleShape))
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Panel2),
            contentAlignment = Alignment.Center
        ) {
            Text(contact.name.firstOrNull()?.uppercase() ?: "?",
                color = Accent, fontSize = (size / 2.5).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomBar(
    favoritesOnly: Boolean,
    onContacts: () -> Unit,
    onFavorites: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0D1422)).navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationItem("Kontakty", Icons.Default.Groups, !favoritesOnly, onContacts)
        NavigationItem("Ulubione", Icons.Default.Favorite, favoritesOnly, onFavorites)
        IconButton(onClick = onAdd, modifier = Modifier.size(54.dp).clip(CircleShape).background(Accent)) {
            Icon(Icons.Default.PersonAdd, "Dodaj kontakt", tint = Color.White)
        }
        NavigationItem("Historia", Icons.Default.History, false) { }
        NavigationItem("Więcej", Icons.Default.MoreHoriz, false) { }
    }
}

@Composable
private fun NavigationItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) Accent else Muted, modifier = Modifier.size(21.dp))
        Text(label, color = if (selected) Accent else Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Groups, null, tint = Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text("Witaj w Contact Pro", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Aby wyświetlić Twoje kontakty i zdjęcia, aplikacja potrzebuje dostępu do książki adresowej telefonu. Nie wysyłamy kontaktów na żaden serwer.",
            color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
            Text("Zezwól na dostęp do kontaktów", color = Color.White)
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(if (searching) "Nie znaleziono kontaktów." else "Nie ma jeszcze kontaktów do wyświetlenia.",
            color = Muted)
    }
}

@Composable
private fun ContactDetails(contact: ContactItem, onDismiss: () -> Unit, onCall: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = White,
        textContentColor = Muted,
        title = { Text(contact.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ContactAvatar(contact, 92)
                Spacer(Modifier.height(12.dp))
                Text(contact.phone.ifBlank { "Brak numeru telefonu" }, color = White)
                Spacer(Modifier.height(8.dp))
                Text("Dane kontaktu pochodzą z książki adresowej Androida.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onCall, enabled = contact.phone.isNotBlank()) { Text("ZADZWOŃ", color = Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ZAMKNIJ", color = Accent) } }
    )
}

private fun loadContacts(context: Context): List<ContactItem> {
    val resolver = context.contentResolver
    val result = mutableListOf<ContactItem>()
    val phoneMap = mutableMapOf<Long, String>()

    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            if (!phoneMap.containsKey(id)) phoneMap[id] = cursor.getString(numberCol) ?: ""
        }
    }

    resolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED
        ),
        null, null,
        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
        val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val photoCol = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
        val starCol = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Bez nazwy"
            val photo = cursor.getString(photoCol)?.let { Uri.parse(it) }
            val starred = cursor.getInt(starCol) == 1
            result.add(ContactItem(id, name, phoneMap[id] ?: "", photo, starred))
        }
    }
    return result
}

private fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
    safeStart(context, intent)
}

private fun openInsertContact(context: Context) {
    val intent = Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE)
    safeStart(context, intent)
}

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Brak aplikacji do obsługi tej akcji", android.widget.Toast.LENGTH_SHORT).show()
    }
}
