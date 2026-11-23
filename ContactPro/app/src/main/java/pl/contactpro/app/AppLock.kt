package pl.contactpro.app

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom

object LockStore {
    private fun prefs(context: Context) = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    private fun hash(salt: String, pin: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()), Base64.NO_WRAP)

    fun isEnabled(context: Context) = prefs(context).getString("hash", null) != null

    fun pinLength(context: Context) = prefs(context).getInt("len", 4)

    fun setPin(context: Context, pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        prefs(context).edit().putString("salt", salt).putString("hash", hash(salt, pin)).putInt("len", pin.length).apply()
    }

    fun check(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString("salt", null) ?: return false
        return p.getString("hash", null) == hash(salt, pin)
    }

    fun disable(context: Context) {
        prefs(context).edit().remove("hash").remove("salt").remove("len").putBoolean("bio", false).apply()
    }

    fun biometricEnabled(context: Context) = prefs(context).getBoolean("bio", false)

    fun setBiometric(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("bio", enabled).apply()
    }

    fun biometricAvailable(context: Context) =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Odblokuj Contact Pro")
            .setSubtitle("Użyj odcisku palca lub twarzy")
            .setNegativeButtonText("Użyj PIN-u")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val length = remember { LockStore.pinLength(context) }
    val bio = remember { LockStore.biometricEnabled(context) && LockStore.biometricAvailable(context) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun tryBio() {
        if (bio && activity != null) LockStore.showBiometric(activity, onUnlock)
    }
    LaunchedEffect(Unit) { tryBio() }

    fun press(d: String) {
        if (pin.length >= length) return
        pin += d
        error = false
        if (pin.length == length) {
            if (LockStore.check(context, pin)) onUnlock() else {
                error = true
                pin = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Lock, null, tint = Accent, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Contact Pro", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (error) "Nieprawidłowy PIN" else "Wpisz PIN", color = if (error) Red else Muted, fontSize = 15.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < pin.length) Accent else Panel2)
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(74.dp).clip(CircleShape)
                            .background(if (k.length == 1) Panel else Color.Transparent)
                            .clickable(enabled = k != "bio" || bio) {
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> tryBio()
                                    else -> press(k)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Usuń", tint = Muted)
                            "bio" -> if (bio) Icon(Icons.Default.Fingerprint, "Odcisk palca", tint = Accent, modifier = Modifier.size(34.dp))
                            else -> Text(k, color = White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val valid = first.length in 4..8 && first.all { it.isDigit() }
    val match = valid && first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Ustaw PIN", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Od 4 do 8 cyfr.", color = Muted, fontSize = 13.sp)
                PinField(first, { first = it.filter(Char::isDigit).take(8) }, "Nowy PIN")
                PinField(second, { second = it.filter(Char::isDigit).take(8) }, "Powtórz PIN")
                if (second.isNotEmpty() && !match) Text("PIN-y się różnią", color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(first) }, enabled = match) { Text("ZAPISZ", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANULUJ", color = Muted) } }
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Panel2,
            focusedLabelColor = Accent, unfocusedLabelColor = Muted,
            focusedTextColor = White, unfocusedTextColor = White, cursorColor = Accent
        )
    )
}
