@file:OptIn(ExperimentalMaterial3Api::class)

package pl.contactpro.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    CONTACTS("Kontakty", Icons.Default.Person),
    RECENT("Ostatnie", Icons.Default.History),
    FAVORITES("Ulubione", Icons.Default.Star),
    GROUPS("Grupy", Icons.Default.Groups),
    MORE("Więcej", Icons.Default.MoreHoriz)
}

class MainActivity : FragmentActivity() {
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

private fun matches(c: ContactItem, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (c.name.contains(q, ignoreCase = true)) return true
    if (c.emails.any { it.contains(q, ignoreCase = true) }) return true
    val digits = q.filter { it.isDigit() }
    return digits.isNotEmpty() && c.phones.any { it.filter { ch -> ch.isDigit() }.contains(digits) }
}

@Composable
fun ContactProApp() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    // ----- Motyw -----
    var themeMode by remember { mutableStateOf(ThemeStore.load(context)) }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    currentPalette = if (isDark) DarkPalette else LightPalette
    LaunchedEffect(isDark) {
        val bars = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        ThemeStore.save(context, mode)
    }

    // ----- Blokada aplikacji -----
    var locked by remember { mutableStateOf(LockStore.isEnabled(context)) }
    var lockRev by remember { mutableIntStateOf(0) }
    var stoppedAt by remember { mutableLongStateOf(0L) }
    var pinDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> if (stoppedAt > 0 && LockStore.isEnabled(context) &&
                    SystemClock.elapsedRealtime() - stoppedAt > 30_000
                ) locked = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ----- Uprawnienia i dane -----
    var canRead by remember { mutableStateOf(granted(context, Manifest.permission.READ_CONTACTS)) }
    var canWrite by remember { mutableStateOf(granted(context, Manifest.permission.WRITE_CONTACTS)) }
    var canCallLog by remember { mutableStateOf(granted(context, Manifest.permission.READ_CALL_LOG)) }

    var data by remember { mutableStateOf(ContactsData()) }
    var loading by remember { mutableStateOf(canRead) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var callsLoading by remember { mutableStateOf(false) }

    // ----- Nawigacja -----
    var tab by rememberSaveable { mutableStateOf(Tab.CONTACTS) }
    var toolPage by rememberSaveable { mutableStateOf<ToolPage?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var qrContact by remember { mutableStateOf<ContactItem?>(null) }
    var myCard by remember { mutableStateOf(MyCardStore.load(context)) }
    var editCard by remember { mutableStateOf(false) }
    var showCardQr by remember { mutableStateOf(false) }
    var blockRev by remember { mutableIntStateOf(0) }
    var numberAction by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

    fun refreshPermissions() {
        canRead = granted(context, Manifest.permission.READ_CONTACTS)
        canWrite = granted(context, Manifest.permission.WRITE_CONTACTS)
        canCallLog = granted(context, Manifest.permission.READ_CALL_LOG)
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
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        blockRev++
        if (BlockStore.isRoleHeld(context)) toast(context, "Ochrona przed niechcianymi połączeniami włączona")
    }
    fun enableBlocking() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(android.app.role.RoleManager::class.java)
            val intent = rm?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            if (intent != null) roleLauncher.launch(intent) else toast(context, "Ten telefon nie obsługuje filtrowania połączeń")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importVcf(context, uri) }

    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        reloadKey++
        blockRev++
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

    LaunchedEffect(canCallLog, data) {
        if (canCallLog) {
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
    val groupById = remember(data.groups) {
        buildMap { data.groups.forEach { g -> g.ids.forEach { put(it, g.title) } } }
    }
    fun groupLabel(c: ContactItem): String? =
        c.groupIds.firstNotNullOfOrNull { groupById[it] }

    val groupMembers = remember(data, selectedGroup) {
        val ids = selectedGroup?.let { idsOf(it) }
        if (ids == null) data.contacts else data.contacts.filter { c -> c.groupIds.any { it in ids } }
    }
    val filtered = remember(groupMembers, query, favoritesOnly) {
        groupMembers.filter { (!favoritesOnly || it.starred) && matches(it, query) }
    }
    val favorites = remember(data) { data.contacts.filter { it.starred } }
    val duplicates = remember(data) { ContactsRepository.findDuplicates(data.contacts) }
    val missedBadge = remember(calls) { recentMissedCount(calls) }
    val detail = detailId?.let { id -> data.contacts.firstOrNull { it.id == id } }

    fun export(list: List<ContactItem>, name: String) {
        if (list.isEmpty()) {
            toast(context, "Brak kontaktów do eksportu")
            return
        }
        pendingExport = list
        exportLauncher.launch("$name-${LocalDate.now()}.vcf")
    }

    val scheme = if (isDark) darkColorScheme(
        primary = Accent, background = Bg, surface = Panel,
        onBackground = White, onSurface = White, onPrimary = Color.White,
        surfaceVariant = Panel2, onSurfaceVariant = Muted, outline = Panel2,
        surfaceContainerLow = Panel, surfaceContainer = Panel, surfaceContainerHigh = Panel
    ) else lightColorScheme(
        primary = Accent, background = Bg, surface = Panel,
        onBackground = White, onSurface = White, onPrimary = Color.White,
        surfaceVariant = Panel2, onSurfaceVariant = Muted, outline = Panel2,
        surfaceContainerLow = Panel, surfaceContainer = Panel, surfaceContainerHigh = Panel
    )

    MaterialTheme(colorScheme = scheme) {
        if (locked) {
            LockScreen(onUnlock = { locked = false })
            return@MaterialTheme
        }

        BackHandler(enabled = detail == null && toolPage != null) { toolPage = null }
        BackHandler(enabled = detail == null && toolPage == null && tab != Tab.CONTACTS) { tab = Tab.CONTACTS }
        BackHandler(enabled = detail == null && toolPage == null && tab == Tab.CONTACTS && (selectedGroup != null || favoritesOnly)) {
            selectedGroup = null; favoritesOnly = false
        }

        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (canRead) {
                    NavigationBar(containerColor = BarBg, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t; toolPage = null },
                                icon = {
                                    if (t == Tab.RECENT && missedBadge > 0) {
                                        BadgedBox(badge = { Badge(containerColor = Red) { Text("$missedBadge", color = Color.White) } }) {
                                            Icon(t.icon, t.label)
                                        }
                                    } else {
                                        Icon(t.icon, t.label)
                                    }
                                },
                                label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
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
                    FloatingActionButton(
                        onClick = { if (canWrite) showAdd = true else openInsertContact(context) },
                        containerColor = Accent, contentColor = Color.White, shape = CircleShape
                    ) { Icon(Icons.Default.Add, "Nowy kontakt", modifier = Modifier.size(30.dp)) }
                }
            }
        ) { padding ->
            if (!canRead) {
                PermissionScreen(Modifier.padding(padding)) { requestContacts() }
            } else when (tab) {
                Tab.CONTACTS -> ContactsScreen(
                    padding = padding,
                    loading = loading,
                    filtered = filtered,
                    query = query,
                    onQuery = { query = it },
                    favoritesOnly = favoritesOnly,
                    onFavoritesOnly = { favoritesOnly = it },
                    selectedGroup = selectedGroup,
                    onClearGroup = { selectedGroup = null },
                    onOpenGroups = { tab = Tab.GROUPS },
                    groupLabel = { groupLabel(it) },
                    onOpen = { detailId = it.id },
                    onStar = { c -> write { ctx -> ContactsRepository.setStarred(ctx, c.id, !c.starred) } },
                    isDark = isDark,
                    onToggleTheme = { setTheme(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) },
                    onNewGroup = { if (canWrite) showNewGroup = true else requestContacts() },
                    onDuplicates = { tab = Tab.MORE; toolPage = ToolPage.DUPLICATES },
                    onBackup = { tab = Tab.MORE; toolPage = ToolPage.BACKUP }
                )
                Tab.RECENT -> HistoryScreen(
                    padding = padding,
                    hasPermission = canCallLog,
                    loading = callsLoading,
                    calls = calls,
                    onBack = null,
                    onRequestPermission = { singlePermission.launch(Manifest.permission.READ_CALL_LOG) },
                    onOpenContact = { detailId = it.id },
                    isBlocked = { n -> blockRev.let { BlockStore.isBlocked(context, n) } },
                    onUnknownNumber = { numberAction = it }
                )
                Tab.FAVORITES -> FavoritesScreen(padding, favorites) { detailId = it.id }
                Tab.GROUPS -> GroupsScreen(
                    padding = padding,
                    groupTitles = groupTitles,
                    countOf = { title -> idsOf(title).let { ids -> data.contacts.count { c -> c.groupIds.any { it in ids } } } },
                    onOpenGroup = { title ->
                        selectedGroup = title
                        favoritesOnly = false
                        tab = Tab.CONTACTS
                    },
                    onNewGroup = { if (canWrite) showNewGroup = true else requestContacts() }
                )
                Tab.MORE -> when (toolPage) {
                    null -> ToolsScreen(
                        padding = padding,
                        blockingActive = remember(blockRev) { BlockStore.isRoleHeld(context) },
                        duplicateCount = duplicates.size,
                        onOpen = { toolPage = it }
                    )
                    ToolPage.BLOCKING -> BlockingScreen(
                        padding = padding,
                        revision = blockRev,
                        onChanged = { blockRev++ },
                        onBack = { toolPage = null },
                        onEnable = { enableBlocking() }
                    )
                    ToolPage.BACKUP -> BackupScreen(
                        padding = padding,
                        contactCount = data.contacts.size,
                        selectedGroup = selectedGroup,
                        groupCount = groupMembers.size,
                        onBack = { toolPage = null },
                        onExportAll = { export(data.contacts, "kontakty") },
                        onExportGroup = { export(groupMembers, "grupa-${selectedGroup.orEmpty().lowercase()}") },
                        onImport = {
                            importLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream", "*/*"))
                        }
                    )
                    ToolPage.DUPLICATES -> DuplicatesScreen(
                        padding = padding,
                        groups = duplicates,
                        onBack = { toolPage = null },
                        onMerge = { group ->
                            write("Połączono ${plural(group.size, "kontakt", "kontakty", "kontaktów")}") { ctx ->
                                ContactsRepository.merge(ctx, group)
                            }
                        },
                        onOpen = { detailId = it.id }
                    )
                    ToolPage.MY_CARD -> MyCardScreen(
                        padding = padding,
                        myCard = myCard,
                        onBack = { toolPage = null },
                        onEdit = { editCard = true },
                        onShowQr = { showCardQr = true }
                    )
                    ToolPage.SETTINGS -> {
                    val pinOn = remember(lockRev) { LockStore.isEnabled(context) }
                    SettingsScreen(
                        padding = padding,
                        onBack = { toolPage = null },
                        themeMode = themeMode,
                        onThemeMode = { setTheme(it) },
                        pinEnabled = pinOn,
                        biometricEnabled = remember(lockRev) { LockStore.biometricEnabled(context) },
                        biometricAvailable = remember(lockRev) { LockStore.biometricAvailable(context) },
                        onPinToggle = { on ->
                            if (on) pinDialog = true else {
                                LockStore.disable(context)
                                lockRev++
                                toast(context, "Blokada wyłączona")
                            }
                        },
                        onBiometricToggle = { on ->
                            if (on && activity != null) {
                                LockStore.showBiometric(activity) {
                                    LockStore.setBiometric(context, true)
                                    lockRev++
                                }
                            } else {
                                LockStore.setBiometric(context, false)
                                lockRev++
                            }
                        },
                        onChangePin = { pinDialog = true }
                    )
                    }
                }
            }
        }

        // ----- Karta kontaktu na pełnym ekranie -----
        if (detail != null) {
            BackHandler { detailId = null }
            ContactDetailScreen(
                contact = detail,
                groupTitles = groupTitles,
                isInGroup = { title -> detail.groupIds.any { it in idsOf(title) } },
                reloadKey = reloadKey,
                onBack = { detailId = null },
                onStar = { write { ctx -> ContactsRepository.setStarred(ctx, detail.id, !detail.starred) } },
                onToggleGroup = { title, member ->
                    write(if (member) "Dodano do grupy $title" else "Usunięto z grupy $title") { ctx ->
                        ContactsRepository.setGroupMembership(ctx, detail, title, idsOf(title), member)
                    }
                },
                onSaveNote = { note -> write("Zapisano notatkę") { ctx -> ContactsRepository.setNote(ctx, detail, note) } },
                onPhotoPicked = { uri -> write("Zmieniono zdjęcie") { ctx -> ContactsRepository.setPhoto(ctx, detail, uri) } },
                onDelete = {
                    val c = detail
                    detailId = null
                    write("Usunięto kontakt") { ctx -> ContactsRepository.deleteContact(ctx, c) }
                },
                onShowQr = { qrContact = detail }
            )
        } else if (detailId != null && !loading) {
            LaunchedEffect(detailId, data) { detailId = null }
        }

        // ----- Okna -----
        if (pinDialog) {
            PinSetupDialog(
                onDismiss = { pinDialog = false },
                onSet = { pin ->
                    LockStore.setPin(context, pin)
                    pinDialog = false
                    lockRev++
                    toast(context, "PIN zapisany – blokada włączona")
                }
            )
        }

        numberAction?.let { number ->
            val blocked = remember(number, blockRev) { BlockStore.isBlocked(context, number) }
            NumberActionsDialog(
                number = number,
                blocked = blocked,
                onDismiss = { numberAction = null },
                onAddContact = { numberAction = null; openInsertContact(context, number) },
                onToggleBlock = {
                    if (blocked) {
                        BlockStore.unblock(context, number)
                        toast(context, "Odblokowano $number")
                    } else {
                        BlockStore.addRule(context, number, false)
                        toast(
                            context,
                            if (BlockStore.isRoleHeld(context)) "Zablokowano $number"
                            else "Zablokowano $number – włącz ochronę w Więcej › Blokowanie"
                        )
                    }
                    blockRev++
                    numberAction = null
                }
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
        Spacer(Modifier.height(4.dp))
        Text("Twoje kontakty. Zawsze pod ręką.", color = Gold, fontSize = 15.sp)
        Spacer(Modifier.height(14.dp))
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
