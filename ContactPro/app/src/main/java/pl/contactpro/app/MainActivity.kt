@file:OptIn(ExperimentalMaterial3Api::class)

package pl.contactpro.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    CONTACTS("Kontakty", Icons.Default.Groups),
    HISTORY("Historia", Icons.Default.History),
    BIRTHDAYS("Urodziny", Icons.Default.Cake),
    MORE("Więcej", Icons.Default.MoreHoriz)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val startTab = if (intent?.getStringExtra(EXTRA_TAB) == "birthdays") Tab.BIRTHDAYS else Tab.CONTACTS
        setContent { ContactProApp(startTab) }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun notificationsGranted(context: Context) =
    Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)

private fun matches(c: ContactItem, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (c.name.contains(q, ignoreCase = true)) return true
    if (c.emails.any { it.contains(q, ignoreCase = true) }) return true
    val digits = q.filter { it.isDigit() }
    return digits.isNotEmpty() && c.phones.any { it.filter { ch -> ch.isDigit() }.contains(digits) }
}

@Composable
fun ContactProApp(startTab: Tab) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var canRead by remember { mutableStateOf(granted(context, Manifest.permission.READ_CONTACTS)) }
    var canWrite by remember { mutableStateOf(granted(context, Manifest.permission.WRITE_CONTACTS)) }
    var canCallLog by remember { mutableStateOf(granted(context, Manifest.permission.READ_CALL_LOG)) }
    var canNotify by remember { mutableStateOf(notificationsGranted(context)) }

    var data by remember { mutableStateOf(ContactsData()) }
    var loading by remember { mutableStateOf(canRead) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var callsLoading by remember { mutableStateOf(false) }

    var tab by rememberSaveable { mutableStateOf(startTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var qrContact by remember { mutableStateOf<ContactItem?>(null) }
    var myCard by remember { mutableStateOf(MyCardStore.load(context)) }
    var editCard by remember { mutableStateOf(false) }
    var showCardQr by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

    fun refreshPermissions() {
        canRead = granted(context, Manifest.permission.READ_CONTACTS)
        canWrite = granted(context, Manifest.permission.WRITE_CONTACTS)
        canCallLog = granted(context, Manifest.permission.READ_CALL_LOG)
        canNotify = notificationsGranted(context)
    }

    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }
    val singlePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    fun requestContacts() = contactsPermission.launch(
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) {
            val list = pendingExport
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ContactsRepository.exportVcf(context, list, uri) }
                }
                result.onSuccess { toast(context, "Zapisano ${plural(it, "kontakt", "kontakty", "kontaktów")}") }
                    .onFailure { toast(context, "Nie udało się zapisać: ${it.message}") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importVcf(context, uri) }

    LifecycleResumeEffect(Unit) {
        refreshPermissions()
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

    LaunchedEffect(canRead) {
        if (canRead) runCatching { BirthdayNotifier.schedule(context) }
    }

    LaunchedEffect(tab, canCallLog, data) {
        if (tab == Tab.HISTORY && canCallLog) {
            callsLoading = true
            calls = withContext(Dispatchers.IO) {
                runCatching { ContactsRepository.loadCallLog(context, data.contacts) }.getOrElse { calls }
            }
            callsLoading = false
        }
    }

    fun write(successMessage: String? = null, block: (Context) -> Unit) {
        if (!canWrite) {
            toast(context, "Zezwól aplikacji na zmianę kontaktów")
            requestContacts()
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

    val groupMembers = remember(data, selectedGroup) {
        val ids = selectedGroup?.let { idsOf(it) }
        if (ids == null) data.contacts else data.contacts.filter { c -> c.groupIds.any { it in ids } }
    }
    val filtered = remember(groupMembers, query) { groupMembers.filter { matches(it, query) } }
    val favorites = remember(data) { data.contacts.filter { it.starred } }
    val birthdays = remember(data) {
        val today = LocalDate.now()
        data.contacts.mapNotNull { c -> c.birthday?.let { c to it.daysUntil(today) } }.sortedBy { it.second }
    }
    val upcoming = remember(birthdays) { birthdays.filter { it.second <= 7 } }
    val todayCount = upcoming.count { it.second == 0 }
    val selected = selectedId?.let { id -> data.contacts.firstOrNull { it.id == id } }

    fun export(list: List<ContactItem>, name: String) {
        if (list.isEmpty()) {
            toast(context, "Brak kontaktów do eksportu")
            return
        }
        pendingExport = list
        exportLauncher.launch("$name-${LocalDate.now()}.vcf")
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Panel,
            onBackground = White, onSurface = White, onPrimary = Color.White,
            surfaceVariant = Panel2, onSurfaceVariant = Muted, outline = Panel2
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (canRead) {
                    NavigationBar(containerColor = BarBg) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = {
                                    if (t == Tab.BIRTHDAYS && todayCount > 0) {
                                        BadgedBox(badge = { Badge(containerColor = Pink) { Text("$todayCount") } }) {
                                            Icon(t.icon, t.label)
                                        }
                                    } else {
                                        Icon(t.icon, t.label)
                                    }
                                },
                                label = { Text(t.label, fontSize = 12.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent, selectedTextColor = Accent,
                                    indicatorColor = AccentSoft,
                                    unselectedIconColor = Muted, unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (canRead && tab == Tab.CONTACTS) {
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
                PermissionScreen(Modifier.padding(padding)) { requestContacts() }
            } else when (tab) {
                Tab.CONTACTS -> ContactsScreen(
                    padding = padding,
                    totalCount = data.contacts.size,
                    loading = loading,
                    favorites = favorites,
                    filtered = filtered,
                    upcoming = upcoming,
                    query = query,
                    onQuery = { query = it },
                    selectedGroup = selectedGroup,
                    onSelectGroup = { selectedGroup = it },
                    groupTitles = groupTitles,
                    onOpen = { selectedId = it.id },
                    onStar = { c -> write { ctx -> ContactsRepository.setStarred(ctx, c.id, !c.starred) } },
                    onNewGroup = { if (canWrite) showNewGroup = true else requestContacts() },
                    onOpenBirthdays = { tab = Tab.BIRTHDAYS }
                )
                Tab.HISTORY -> HistoryScreen(
                    padding = padding,
                    hasPermission = canCallLog,
                    loading = callsLoading,
                    calls = calls,
                    onRequestPermission = { singlePermission.launch(Manifest.permission.READ_CALL_LOG) },
                    onOpenContact = { selectedId = it.id }
                )
                Tab.BIRTHDAYS -> BirthdaysScreen(
                    padding = padding,
                    birthdays = birthdays,
                    notificationsAllowed = canNotify,
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) singlePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenContact = { selectedId = it.id }
                )
                Tab.MORE -> MoreScreen(
                    padding = padding,
                    myCard = myCard,
                    contactCount = data.contacts.size,
                    selectedGroup = selectedGroup,
                    groupCount = groupMembers.size,
                    onEditCard = { editCard = true },
                    onShowCardQr = { showCardQr = true },
                    onExportAll = { export(data.contacts, "kontakty") },
                    onExportGroup = { export(groupMembers, "grupa-${selectedGroup.orEmpty().lowercase()}") },
                    onImport = { importLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream", "*/*")) }
                )
            }
        }

        if (selected != null) {
            ContactDetailsSheet(
                contact = selected,
                groupTitles = groupTitles,
                isInGroup = { title -> selected.groupIds.any { it in idsOf(title) } },
                onDismiss = { selectedId = null },
                onStar = { write { ctx -> ContactsRepository.setStarred(ctx, selected.id, !selected.starred) } },
                onToggleGroup = { title, member ->
                    write(if (member) "Dodano do grupy $title" else "Usunięto z grupy $title") { ctx ->
                        ContactsRepository.setGroupMembership(ctx, selected, title, idsOf(title), member)
                    }
                },
                onSetBirthday = { b ->
                    write("Zapisano urodziny") { ctx -> ContactsRepository.setBirthday(ctx, selected, b) }
                },
                onShowQr = { qrContact = selected }
            )
        }

        qrContact?.let { c ->
            QrDialog(c.name, buildVCard(c.name, c.phones, c.emails)) { qrContact = null }
        }

        if (showCardQr && !myCard.isEmpty) {
            QrDialog(
                "Moja wizytówka",
                buildVCard(myCard.name, listOf(myCard.phone), listOf(myCard.email), myCard.company)
            ) { showCardQr = false }
        }

        if (editCard) {
            MyCardDialog(
                initial = myCard,
                onDismiss = { editCard = false },
                onSave = { card ->
                    MyCardStore.save(context, card)
                    myCard = MyCardStore.load(context)
                    editCard = false
                }
            )
        }

        if (showAdd) {
            AddContactSheet(
                groupTitles = groupTitles,
                initialGroup = selectedGroup,
                onDismiss = { showAdd = false },
                onSystemForm = { showAdd = false; openInsertContact(context) },
                onSave = { name, phone, mail, groups, star, birthday ->
                    showAdd = false
                    write("Zapisano kontakt") { ctx ->
                        ContactsRepository.addContact(ctx, name, phone, mail, groups, star, birthday)
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
