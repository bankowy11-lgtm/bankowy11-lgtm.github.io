package pl.contactpro.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Dane ----------

data class BlockRule(val value: String, val prefix: Boolean)

data class BlockedCall(val number: String, val time: Long, val reason: String)

data class BlockSettings(val hidden: Boolean, val unknown: Boolean, val foreign: Boolean)

object BlockStore {
    private const val PREFS = "blocking"
    private const val HOME_CC = "+48"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Zostawia cyfry i ewentualny „+” na początku; „00” zamienia na „+”. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    private fun variants(n: String): Set<String> = buildSet {
        add(n)
        if (n.startsWith(HOME_CC)) add(n.removePrefix(HOME_CC))
        if (!n.startsWith("+") && n.isNotEmpty()) add(HOME_CC + n)
    }.filter { it.isNotEmpty() }.toSet()

    fun rules(context: Context): List<BlockRule> =
        prefs(context).getStringSet("rules", emptySet()).orEmpty()
            .map { BlockRule(it.drop(2), it.startsWith("p:")) }
            .sortedBy { it.value }

    fun addRule(context: Context, raw: String, prefix: Boolean): Boolean {
        val value = normalize(raw)
        if (value.isEmpty() || value == "+") return false
        val set = prefs(context).getStringSet("rules", emptySet()).orEmpty().toMutableSet()
        set.add((if (prefix) "p:" else "n:") + value)
        prefs(context).edit().putStringSet("rules", set).apply()
        return true
    }

    fun removeRule(context: Context, rule: BlockRule) {
        val set = prefs(context).getStringSet("rules", emptySet()).orEmpty().toMutableSet()
        set.remove((if (rule.prefix) "p:" else "n:") + rule.value)
        prefs(context).edit().putStringSet("rules", set).apply()
    }

    fun unblock(context: Context, number: String) {
        matchRule(rules(context), number)?.let { removeRule(context, it) }
    }

    fun matchRule(rules: List<BlockRule>, number: String): BlockRule? {
        val n = normalize(number)
        if (n.isEmpty()) return null
        val incoming = variants(n)
        val last9 = n.filter { it.isDigit() }.takeLast(9)
        return rules.firstOrNull { rule ->
            if (rule.prefix) {
                val ruleVariants = variants(rule.value)
                incoming.any { inc -> ruleVariants.any { inc.startsWith(it) } }
            } else {
                val r9 = rule.value.filter { it.isDigit() }.takeLast(9)
                r9.length >= 3 && r9 == last9
            }
        }
    }

    fun isBlocked(context: Context, number: String) = matchRule(rules(context), number) != null

    fun settings(context: Context) = prefs(context).let {
        BlockSettings(
            hidden = it.getBoolean("hidden", false),
            unknown = it.getBoolean("unknown", false),
            foreign = it.getBoolean("foreign", false)
        )
    }

    fun setSetting(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun log(context: Context): List<BlockedCall> = runCatching {
        val arr = JSONArray(prefs(context).getString("log", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            BlockedCall(o.optString("n"), o.optLong("t"), o.optString("r"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun addLog(context: Context, number: String, reason: String) {
        val items = listOf(BlockedCall(number, System.currentTimeMillis(), reason)) + log(context).take(99)
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("n", it.number).put("t", it.time).put("r", it.reason)) }
        prefs(context).edit().putString("log", arr.toString()).apply()
    }

    fun clearLog(context: Context) {
        prefs(context).edit().putString("log", "[]").apply()
    }

    private fun isInContacts(context: Context, number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return true // bez uprawnienia nie ryzykujemy blokowania znajomych
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        }.getOrDefault(true)
    }

    /** Zwraca powód blokady albo null, jeśli połączenie ma przejść. */
    fun shouldBlock(context: Context, number: String, hiddenPresentation: Boolean): String? {
        val s = settings(context)
        val n = normalize(number)
        if (n.isEmpty() || hiddenPresentation) {
            return if (s.hidden) "Numer ukryty" else null
        }
        matchRule(rules(context), n)?.let { rule ->
            return if (rule.prefix) "Prefiks ${rule.value}" else "Lista blokowanych"
        }
        if (s.foreign && n.startsWith("+") && !n.startsWith(HOME_CC)) return "Numer zagraniczny"
        if (s.unknown && !isInContacts(context, number)) return "Spoza kontaktów"
        return null
    }

    fun isSupported() = Build.VERSION.SDK_INT >= 29

    fun isRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }
}

// ---------- Usługa filtrowania połączeń ----------

class CallBlockService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val incoming = Build.VERSION.SDK_INT < 29 || details.callDirection == Call.Details.DIRECTION_INCOMING
        val hidden = Build.VERSION.SDK_INT >= 30 &&
            details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val reason = if (incoming) runCatching { BlockStore.shouldBlock(this, number, hidden) }.getOrNull() else null
        val response = if (reason != null) {
            BlockStore.addLog(this, number.ifBlank { "Numer ukryty" }, reason)
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(details, response)
    }
}

// ---------- Ekran ----------

@Composable
fun BlockingScreen(
    padding: PaddingValues,
    revision: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onEnable: () -> Unit
) {
    val context = LocalContext.current
    val supported = BlockStore.isSupported()
    val active = remember(revision) { BlockStore.isRoleHeld(context) }
    val settings = remember(revision) { BlockStore.settings(context) }
    val rules = remember(revision) { BlockStore.rules(context) }
    val log = remember(revision) { BlockStore.log(context) }
    var newNumber by remember { mutableStateOf("") }
    var asPrefix by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć", tint = White)
                }
                Column {
                    Text("Blokowanie", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Niechciane połączenia", color = Muted, fontSize = 14.sp)
                }
            }
        }

        item {
            val (bg, tint, icon) = when {
                !supported -> Triple(Panel, Muted, Icons.Default.Warning)
                active -> Triple(GreenSoft, Green, Icons.Default.VerifiedUser)
                else -> Triple(PinkSoft, Red, Icons.Default.Warning)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when {
                            !supported -> "Niedostępne na tym telefonie"
                            active -> "Ochrona włączona"
                            else -> "Ochrona wyłączona"
                        },
                        color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !supported -> "Blokowanie połączeń przez aplikację wymaga Androida 10 lub nowszego. Możesz blokować numery w systemowej aplikacji Telefon."
                        active -> "Połączenia pasujące do reguł są odrzucane, zanim telefon zadzwoni. Znajdziesz je poniżej w dzienniku."
                        else -> "Ustaw Contact Pro jako aplikację do filtrowania połączeń, aby blokować niechciane numery. Android zapyta o zgodę."
                    },
                    color = Muted, fontSize = 14.sp
                )
                if (supported && !active) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onEnable, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Włącz ochronę", color = Color.White)
                    }
                }
            }
        }

        item {
            PanelCard {
                Text("Automatyczne reguły", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                SettingSwitch("Numery ukryte / prywatne", "Połączenia bez numeru", settings.hidden) {
                    BlockStore.setSetting(context, "hidden", it); onChanged()
                }
                SettingSwitch("Numery zagraniczne", "Wszystko spoza +48", settings.foreign) {
                    BlockStore.setSetting(context, "foreign", it); onChanged()
                }
                SettingSwitch("Wszystkie spoza kontaktów", "Uwaga: zablokuje też kuriera czy lekarza", settings.unknown) {
                    BlockStore.setSetting(context, "unknown", it); onChanged()
                }
            }
        }

        item {
            PanelCard {
                Text("Dodaj numer do blokady", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                FormField(newNumber, { newNumber = it }, "Numer lub początek numeru", KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Blokuj cały prefiks", color = White, fontSize = 14.sp)
                        Text("np. 800 lub +44 – wszystkie numery zaczynające się tak", color = Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = asPrefix, onCheckedChange = { asPrefix = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (BlockStore.addRule(context, newNumber, asPrefix)) {
                            toast(context, "Zablokowano ${BlockStore.normalize(newNumber)}")
                            newNumber = ""
                            asPrefix = false
                            onChanged()
                        }
                    },
                    enabled = newNumber.any { it.isDigit() },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Block, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Zablokuj", color = Color.White)
                }
            }
        }

        item { SectionHeader("Zablokowane numery", rules.size.toString()) }
        if (rules.isEmpty()) {
            item { EmptyState("Lista jest pusta. Numer zablokujesz też z zakładki Historia.") }
        } else {
            items(rules, key = { (if (it.prefix) "p" else "n") + it.value }) { rule ->
                InfoRow(
                    Icons.Default.Block,
                    if (rule.prefix) "${rule.value}…" else rule.value,
                    if (rule.prefix) "Prefiks – wszystkie numery zaczynające się tak" else "Numer"
                ) {
                    IconButton(onClick = { BlockStore.removeRule(context, rule); onChanged() }) {
                        Icon(Icons.Default.Delete, "Odblokuj", tint = Muted)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dziennik blokad", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (log.isNotEmpty()) {
                    TextButton(onClick = { BlockStore.clearLog(context); onChanged() }) { Text("Wyczyść", color = Accent) }
                }
            }
        }
        if (log.isEmpty()) {
            item { EmptyState("Jeszcze nic nie zablokowano.") }
        } else {
            items(log, key = { "${it.time}-${it.number}" }) { call ->
                InfoRow(
                    Icons.Default.PhoneDisabled,
                    call.number,
                    "${timeFormat.format(Date(call.time))} • ${call.reason}"
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Red)
        )
    }
}

/** Okno po dotknięciu nieznanego numeru w historii. */
@Composable
fun NumberActionsDialog(
    number: String,
    blocked: Boolean,
    onDismiss: () -> Unit,
    onAddContact: () -> Unit,
    onToggleBlock: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(number, color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (blocked) "Ten numer jest zablokowany." else "Co chcesz zrobić z tym numerem?",
                    color = Muted
                )
                Button(
                    onClick = onAddContact, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Dodaj do kontaktów", color = Color.White) }
                Button(
                    onClick = onToggleBlock, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (blocked) Panel2 else Red)
                ) { Text(if (blocked) "Odblokuj numer" else "Zablokuj numer", color = Color.White) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ANULUJ", color = Muted) } }
    )
}
