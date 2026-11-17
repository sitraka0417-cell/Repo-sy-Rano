package mg.sitraka.ranorepo

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometric {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(a: FragmentActivity, onOk: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(a)
        val bp = BiometricPrompt(a, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Déverrouiller Rano & Repos & Sport")
            .setSubtitle("Posez votre doigt sur le capteur")
            .setNegativeButtonText("Utiliser le code PIN")
            .setAllowedAuthenticators(AUTH)
            .build()
        bp.authenticate(info)
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(cs.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
    }
}

@Composable
fun LockScreen(act: FragmentActivity, onUnlock: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Prefs.biometric && Biometric.available(act)) Biometric.prompt(act, onUnlock)
    }

    fun press(d: String) {
        if (entered.length >= Prefs.pinLen) return
        entered += d
        error = false
        if (entered.length == Prefs.pinLen) {
            if (Prefs.checkPin(entered)) onUnlock() else {
                error = true
                entered = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(heroBrush()),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Lock, null, tint = cs.onPrimary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Rano & Repos & Sport", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (error) "Code incorrect, réessayez" else "Entrez votre code PIN",
            color = if (error) cs.error else cs.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(Prefs.pinLen) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < entered.length) cs.primary else cs.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    r.forEach { d -> Key(d) { press(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Prefs.biometric && Biometric.available(act)) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).clickable { Biometric.prompt(act, onUnlock) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Fingerprint, null, tint = cs.primary, modifier = Modifier.size(36.dp)) }
                } else {
                    Spacer(Modifier.size(76.dp))
                }
                Key("0") { press("0") }
                Key("⌫") { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
            }
        }
    }
}
