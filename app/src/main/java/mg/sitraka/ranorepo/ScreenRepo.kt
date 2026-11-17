package mg.sitraka.ranorepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RepoScreen(onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val phase = Chrono.phase
    val running = phase != Phase.IDLE

    var showManual by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Int?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showWorkDlg by remember { mutableStateOf(false) }
    var showPauseDlg by remember { mutableStateOf(false) }

    val dayStart = startOfDay(System.currentTimeMillis())
    val today = Store.repos.filter { it.ts >= dayStart }
    val todaySec = today.sumOf { it.sec.toLong() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----- Chronomètre -----
        AppCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val counting = phase == Phase.PAUSE || phase == Phase.WORK
                val prog = if (counting && Chrono.totalMs > 0) Chrono.remainingMs.toFloat() / Chrono.totalMs.toFloat() else 1f
                val ringBrush = when (phase) {
                    Phase.PAUSE -> Brush.linearGradient(listOf(cs.secondary, cs.primary))
                    Phase.WORK -> Brush.linearGradient(listOf(cs.primary, cs.tertiary))
                    Phase.WAIT_PAUSE, Phase.WAIT_WORK -> Brush.linearGradient(listOf(cs.tertiary, cs.tertiary))
                    Phase.IDLE -> heroBrush()
                }
                Ring(prog, 240.dp, 16.dp, ringBrush) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                counting -> fmtClock(Chrono.remainingMs)
                                running -> "00:00"
                                else -> fmtClock(Prefs.selectedSec * 1000L)
                            },
                            fontSize = 48.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            when (phase) {
                                Phase.IDLE -> "Prêt pour le repos"
                                Phase.PAUSE -> "Repos en cours"
                                Phase.WORK -> "Travail en cours"
                                Phase.WAIT_PAUSE -> "C'est l'heure de la pause"
                                Phase.WAIT_WORK -> "Retour au travail"
                            },
                            color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ----- Alarme en cours -----
        if (Chrono.ringing) {
            AppCard {
                Text(Chrono.alertTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.tertiary)
                Text(Chrono.alertText, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { AlarmSound.stop() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Arrêter l'alarme") }
            }
        }

        // ----- Choix de la durée + boutons -----
        AppCard {
            if (phase == Phase.IDLE) {
                Text("Durée du repos", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ModelChips(
                    values = Prefs.models, selected = Prefs.selectedSec, enabled = true,
                    onSelect = { Prefs.selectSec(it) }, onOther = { showManual = true }
                )
                val sel = Prefs.selectedSec
                val isCustom = sel in Prefs.models && sel !in Prefs.DEFAULT_MODELS
                if (isCustom) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Durée personnalisée : ${fmtDur(sel.toLong())}",
                            Modifier.weight(1f), color = cs.onSurfaceVariant, fontSize = 13.sp
                        )
                        IconButton(onClick = { showEdit = true }) { Icon(Icons.Rounded.Edit, "Modifier") }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Rounded.Delete, "Supprimer", tint = cs.error)
                        }
                    }
                } else if (sel !in Prefs.models) {
                    Text(
                        "Durée ponctuelle : ${fmtDur(sel.toLong())} (non enregistrée)",
                        color = cs.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                GradientButton("Début du repos", Icons.Rounded.PlayArrow, {
                    TimerService.send(ctx, TimerService.ACTION_PAUSE, Prefs.selectedSec)
                })
            } else {
                when (phase) {
                    Phase.WAIT_PAUSE -> {
                        GradientButton("Je commence la pause", Icons.Rounded.PlayArrow, {
                            TimerService.send(ctx, TimerService.ACTION_NEXT)
                        })
                        Spacer(Modifier.height(10.dp))
                    }
                    Phase.WAIT_WORK -> {
                        GradientButton("Je reprends le travail", Icons.Rounded.Work, {
                            TimerService.send(ctx, TimerService.ACTION_NEXT)
                        })
                        Spacer(Modifier.height(10.dp))
                    }
                    else -> {}
                }
                DangerButton(
                    if (Chrono.auto) "Arrêter l'automatisation" else "Arrêter le repos",
                    Icons.Rounded.Stop, { TimerService.send(ctx, TimerService.ACTION_STOP) }
                )
            }
        }

        // ----- Automatisation -----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Automatisation", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Alterne travail et repos tout seul", color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
                Switch(
                    checked = Prefs.autoEnabled, onCheckedChange = { Prefs.changeAutoEnabled(it) },
                    enabled = !Chrono.auto
                )
            }
            if (Prefs.autoEnabled) {
                Spacer(Modifier.height(14.dp))
                Text("Travailler pendant", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmtDur(Prefs.workSec.toLong()), Modifier.weight(1f),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary
                    )
                    TextButton(onClick = { showWorkDlg = true }, enabled = !Chrono.auto) { Text("Modifier") }
                }
                Spacer(Modifier.height(6.dp))
                Text("Puis repos de ${fmtDur(Prefs.autoPauseSec.toLong())}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ModelChips(
                    values = Prefs.models, selected = Prefs.autoPauseSec, enabled = !Chrono.auto,
                    onSelect = { Prefs.setAutoPause(it) }, onOther = { showPauseDlg = true }
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Confirmer chaque changement", fontWeight = FontWeight.Medium)
                        Text(
                            "Un bouton apparaît à chaque alarme. Désactivé : tout s'enchaîne automatiquement.",
                            color = cs.onSurfaceVariant, fontSize = 12.sp
                        )
                    }
                    Switch(checked = Prefs.autoConfirm, onCheckedChange = { Prefs.changeAutoConfirm(it) })
                }
                Spacer(Modifier.height(14.dp))
                if (!Chrono.auto) {
                    GradientButton(
                        "Je commence le travail", Icons.Rounded.Work,
                        { TimerService.send(ctx, TimerService.ACTION_AUTO) },
                        enabled = phase == Phase.IDLE
                    )
                } else {
                    Text(
                        "Automatisation en cours : travail ${fmtDur(Prefs.workSec.toLong())}, " +
                            "puis repos ${fmtDur(Prefs.autoPauseSec.toLong())}, en boucle.",
                        color = cs.secondary, fontSize = 13.sp
                    )
                }
            }
        }

        // ----- Total du jour -----
        AppCard {
            Text("Repos d'aujourd'hui", color = cs.onSurfaceVariant, fontSize = 14.sp)
            Text(fmtDur(todaySec), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.secondary)
            Text(
                if (today.isEmpty()) "Aucun repos pour l'instant" else if (today.size == 1) "1 repos effectué" else "${today.size} repos effectués",
                color = cs.onSurfaceVariant, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onHistory, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.History, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Historique")
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // ----- Dialogues -----
    if (showManual) {
        DurationDialog("Durée personnalisée", Prefs.selectedSec, { showManual = false }) { sec ->
            showManual = false
            if (sec in Prefs.models) Prefs.selectSec(sec) else pending = sec
        }
    }
    val p = pending
    if (p != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Ajouter aux modèles ?") },
            text = { Text("Voulez-vous enregistrer ${fmtDur(p.toLong())} dans vos durées habituelles ?") },
            confirmButton = {
                TextButton(onClick = { Prefs.addModel(p); Prefs.selectSec(p); pending = null }) {
                    Text("Oui, ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { Prefs.selectSec(p); pending = null }) { Text("Non, juste cette fois") }
            }
        )
    }
    if (showEdit) {
        DurationDialog("Modifier la durée", Prefs.selectedSec, { showEdit = false }) { sec ->
            Prefs.replaceModel(Prefs.selectedSec, sec)
            showEdit = false
        }
    }
    if (showDelete) {
        ConfirmDialog(
            "Supprimer cette durée ?",
            "${fmtDur(Prefs.selectedSec.toLong())} sera retirée de vos durées enregistrées.",
            "Supprimer",
            onConfirm = { Prefs.removeModel(Prefs.selectedSec); showDelete = false },
            onDismiss = { showDelete = false }
        )
    }
    if (showWorkDlg) {
        DurationDialog("Durée de travail", Prefs.workSec, { showWorkDlg = false }) { sec ->
            Prefs.changeWorkSec(sec)
            showWorkDlg = false
        }
    }
    if (showPauseDlg) {
        DurationDialog("Durée du repos automatique", Prefs.autoPauseSec, { showPauseDlg = false }) { sec ->
            Prefs.setAutoPause(sec)
            showPauseDlg = false
        }
    }
}
