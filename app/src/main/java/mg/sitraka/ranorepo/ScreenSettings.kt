package mg.sitraka.ranorepo

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@Composable
private fun ThemeCard(
    label: String, icon: ImageVector, preview: List<Color>, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(preview)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onChange(it) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PinDialog(mode: String, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val needOld = mode != "set"
    val needNew = mode != "disable"

    fun validate(): String? {
        if (needOld && !Prefs.checkPin(old)) return "Code actuel incorrect"
        if (needNew) {
            if (n1.length !in 4..6) return "Le code doit contenir 4 à 6 chiffres"
            if (n1 != n2) return "Les deux codes ne correspondent pas"
        }
        return null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    "set" -> "Créer un code PIN"
                    "change" -> "Modifier le code PIN"
                    else -> "Désactiver le code PIN"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needOld) PinField("Code actuel", old) { old = it }
                if (needNew) {
                    PinField("Nouveau code (4 à 6 chiffres)", n1) { n1 = it }
                    PinField("Confirmer le code", n2) { n2 = it }
                }
                val e = err
                if (e != null) Text(e, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validate()
                if (e != null) {
                    err = e
                } else {
                    if (mode == "disable") Prefs.clearPin() else Prefs.setPin(n1)
                    onDismiss()
                }
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun SettingsScreen(act: FragmentActivity) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var pinMode by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val u = Auth.user

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----- Compte -----
        AppCard {
            if (u == null) {
                Text("Compte et synchronisation", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Connectez-vous avec Google pour retrouver vos historiques de repos, d'eau et de sport sur tous vos appareils.",
                    color = cs.onSurfaceVariant, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                GoogleButton("Se connecter avec Google", loading) {
                    scope.launch {
                        loading = true
                        err = Auth.signIn(act)
                        loading = false
                    }
                }
                val e = err
                if (e != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(e, color = cs.error, fontSize = 13.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.photoUrl?.toString(), u.displayName ?: u.email, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName ?: "Utilisateur", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(u.email ?: "", color = cs.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = cs.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Synchronisation active", color = cs.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                DangerButton("Se déconnecter", Icons.Rounded.PowerSettingsNew, { confirmLogout = true })
            }
        }

        // ----- Thème -----
        AppCard {
            Text("Apparence", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeCard(
                    "Sombre", Icons.Rounded.DarkMode, listOf(Color(0xFF0A1020), Color(0xFF1B2742)),
                    Prefs.theme == "dark", Modifier.weight(1f)
                ) { Prefs.changeTheme("dark") }
                ThemeCard(
                    "Clair", Icons.Rounded.LightMode, listOf(Color(0xFF4DB6FF), Color(0xFF0A74D9)),
                    Prefs.theme == "light", Modifier.weight(1f)
                ) { Prefs.changeTheme("light") }
                ThemeCard(
                    "Spécial", Icons.Rounded.AutoAwesome, listOf(Color(0xFFFF7AC6), Color(0xFF6A52A3)),
                    Prefs.theme == "special", Modifier.weight(1f)
                ) { Prefs.changeTheme("special") }
            }
        }

        // ----- Sécurité -----
        AppCard {
            Text("Sécurité", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Code PIN", fontWeight = FontWeight.SemiBold)
                    Text("Protège l'accès à l'application", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
                Switch(
                    checked = Prefs.pinEnabled,
                    onCheckedChange = { pinMode = if (it) "set" else "disable" }
                )
            }
            if (Prefs.pinEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pinMode = "change" }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Modifier le code PIN") }
                Spacer(Modifier.height(10.dp))
                val bioOk = Biometric.available(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Empreinte digitale", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (bioOk) "Déverrouillez d'un simple geste"
                            else "Aucune empreinte enregistrée sur cet appareil",
                            color = cs.onSurfaceVariant, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = Prefs.biometric && bioOk, enabled = bioOk,
                        onCheckedChange = { Prefs.changeBiometric(it) }
                    )
                }
            }
        }

        AppCard {
            Text("À propos", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val ver = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
            Text("Rano & Repos & Sport · version $ver", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
    }

    val pm = pinMode
    if (pm != null) PinDialog(pm) { pinMode = null }
    if (confirmLogout) {
        ConfirmDialog(
            "Se déconnecter ?",
            "Les données restent sauvegardées dans votre compte et reviendront à la prochaine connexion.",
            "Se déconnecter",
            onConfirm = {
                confirmLogout = false
                scope.launch { Auth.signOut(act) }
            },
            onDismiss = { confirmLogout = false }
        )
    }
}
