package mg.sitraka.ranorepo

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Notifs.createChannels(this)
        Auth.init()
    }
}

class MainActivity : FragmentActivity() {
    private var lastStop = 0L
    var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = Prefs.pinEnabled
        setContent {
            AppTheme { Root(this) }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lastStop > 0 && Prefs.pinEnabled && SystemClock.elapsedRealtime() - lastStop > 15_000) {
            locked = true
        }
    }
}

@Composable
fun Root(act: MainActivity) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    CompositionLocalProvider(LocalContentColor provides cs.onBackground) {
        Box(Modifier.fillMaxSize().background(appBrush())) {
            if (act.locked) {
                LockScreen(act) { act.locked = false }
            } else {
                MainShell(act)
            }
        }
    }
}

@Composable
fun Header(onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val u = Auth.user
    val first = u?.displayName?.trim()?.split(" ")?.firstOrNull()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Rano & Repo", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (!first.isNullOrBlank()) "Bonjour, $first" else "Pauses et hydratation",
                color = cs.onSurfaceVariant, fontSize = 14.sp
            )
        }
        Avatar(
            url = u?.photoUrl?.toString(), name = u?.displayName ?: u?.email, diameter = 44.dp,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onAvatar)
        )
    }
}

@Composable
fun MainShell(act: MainActivity) {
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var history by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = history != null) { history = null }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = cs.onBackground,
        bottomBar = {
            if (history == null) {
                NavigationBar(containerColor = cs.surface.copy(alpha = 0.97f)) {
                    listOf(
                        Triple("Repos", Icons.Rounded.Timer, 0),
                        Triple("Eau", Icons.Rounded.WaterDrop, 1),
                        Triple("Réglages", Icons.Rounded.Settings, 2)
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, label) }, label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val h = history
            if (h != null) {
                HistoryScreen(h) { history = null }
            } else {
                Header { tab = 2 }
                when (tab) {
                    0 -> RepoScreen { history = "repo" }
                    1 -> WaterScreen { history = "water" }
                    else -> SettingsScreen(act)
                }
            }
        }
    }
}
