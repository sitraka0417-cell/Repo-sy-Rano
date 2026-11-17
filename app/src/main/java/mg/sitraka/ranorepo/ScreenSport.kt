package mg.sitraka.ranorepo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

private val DAY_NAMES = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

private fun minCeil(sec: Long): Long = (sec + 59) / 60

fun fmtMinutes(min: Long): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

@Composable
private fun ProgressBar(frac: Float, color: Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(cs.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(color)
        )
    }
}

@Composable
fun MinutesDialog(title: String, initialMin: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var v by remember { mutableStateOf(initialMin.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v,
                onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) v = it },
                label = { Text("Minutes") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = v.toIntOrNull() ?: 0
                if (n > 0) onOk(n)
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun SportTypeDialog(onDismiss: () -> Unit, onOk: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autre sport") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Nom du sport") }, singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ajouter à ma liste", Modifier.weight(1f))
                    Switch(checked = save, onCheckedChange = { save = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onOk(name.trim(), save) }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PlanDialog(initial: List<Int>, onDismiss: () -> Unit, onOk: (List<Int>) -> Unit) {
    val vals = remember { mutableStateListOf(*initial.map { it.toString() }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Planning de la semaine") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Durée en minutes pour chaque jour. Laissez 0 pour un jour de repos.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DAY_NAMES.forEachIndexed { i, day ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day, Modifier.weight(1f))
                        OutlinedTextField(
                            value = vals[i],
                            onValueChange = { if (it.length <= 3 && it.all { ch -> ch.isDigit() }) vals[i] = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(vals.map { it.toIntOrNull() ?: 0 }) }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun SportScreen(onHistory: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val cs = MaterialTheme.colorScheme
    val running = SportRun.running

    var showOther by remember { mutableStateOf(false) }
    var showEst by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var showDelType by remember { mutableStateOf(false) }
    var showAbandon by remember { mutableStateOf(false) }

    // Horloge de l'écran pendant une séance
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (SportRun.running) {
            now = System.currentTimeMillis()
            if (SportRun.isDue(now)) SportRun.complete(ctx, true)
            delay(250)
        }
    }

    // ----- Statistiques -----
    val week = rangeFor(PeriodKind.WEEK, 0)
    val weekRecs = Store.sports.filter { it.ts >= week.from && it.ts < week.to }
    val weekSec = weekRecs.sumOf { it.sec.toLong() }
    val dayStart = startOfDay(System.currentTimeMillis())
    val todayRecs = Store.sports.filter { it.ts >= dayStart }
    val todaySec = todayRecs.sumOf { it.sec.toLong() }
    val goalSec = Prefs.sportGoalMin * 60L
    val leftSec = (goalSec - weekSec).coerceAtLeast(0L)
    val todayIdx = LocalDate.now(zone()).dayOfWeek.value - 1
    val daysLeft = 7 - todayIdx

    val perDayMin = (Prefs.sportGoalMin + Prefs.sportSplitDays - 1) / Prefs.sportSplitDays
    val completedDays = weekRecs.groupBy { startOfDay(it.ts) }.values
        .count { l -> l.sumOf { r -> r.sec.toLong() } >= perDayMin * 60L }
    val targetSec: Long = when {
        !Prefs.sportSplit -> 0L
        Prefs.sportSplitCustom -> Prefs.sportDayMin[todayIdx] * 60L
        completedDays >= Prefs.sportSplitDays && todaySec < perDayMin * 60L -> 0L
        else -> perDayMin * 60L
    }

    val sportDays = Store.sports.map { startOfDay(it.ts) }.toSet()
    var streak = 0
    var cursor = LocalDate.now(zone())
    if (cursor.ms() !in sportDays) cursor = cursor.minusDays(1)
    while (cursor.ms() in sportDays) {
        streak++
        cursor = cursor.minusDays(1)
    }

    // ----- Chronomètre -----
    val est = SportRun.estSec
    val elapsedMs = if (running) (now - SportRun.startWall).coerceAtLeast(0L) else 0L
    val remainMs = if (running && est > 0) (est * 1000L - elapsedMs).coerceAtLeast(0L) else 0L
    val idleEstMs = if (Prefs.sportEstOn) Prefs.sportEstMin * 60_000L else 0L
    val clockMs = when {
        running && est > 0 -> remainMs
        running -> (elapsedMs / 1000L) * 1000L
        else -> idleEstMs
    }
    val prog = when {
        running && est > 0 -> remainMs.toFloat() / (est * 1000L).toFloat()
        running -> (elapsedMs % 60_000L) / 60_000f
        else -> 1f
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----- Chronomètre -----
        AppCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val ringBrush = if (running) Brush.linearGradient(listOf(cs.tertiary, cs.primary)) else heroBrush()
                Ring(prog, 240.dp, 16.dp, ringBrush) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtClock(clockMs), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                !running -> "Prêt pour le sport"
                                est > 0 -> "${SportRun.type} · temps restant"
                                else -> "${SportRun.type} · en cours"
                            },
                            color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ----- Séance terminée -----
        val ended = SportRun.endedText
        if (ended != null) {
            AppCard {
                Text("Séance terminée", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.tertiary)
                Text(ended, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { SportRun.dismissEnded(ctx) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("OK") }
            }
        }

        // ----- Choix du sport + démarrage -----
        AppCard {
            if (!running) {
                Text("Type de sport", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Prefs.sportTypes) { t ->
                        FilterChip(
                            selected = t == Prefs.sportSelected,
                            onClick = { Prefs.changeSportSelected(t) },
                            label = { Text(t) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showOther = true }, label = { Text("Autre") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }
                        )
                    }
                }
                val sel = Prefs.sportSelected
                if (sel !in Prefs.DEFAULT_SPORTS) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sel in Prefs.sportTypes) "Sport personnalisé : $sel"
                            else "Sport ponctuel : $sel (non enregistré)",
                            Modifier.weight(1f), color = cs.onSurfaceVariant, fontSize = 13.sp
                        )
                        if (sel in Prefs.sportTypes) {
                            IconButton(onClick = { showDelType = true }) {
                                Icon(Icons.Rounded.Delete, "Supprimer", tint = cs.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Temps estimé", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (Prefs.sportEstOn) "Compte à rebours de ${fmtMinutes(Prefs.sportEstMin.toLong())}"
                            else "Désactivé : chronomètre normal",
                            color = cs.onSurfaceVariant, fontSize = 12.sp
                        )
                    }
                    if (Prefs.sportEstOn) {
                        TextButton(onClick = { showEst = true }) { Text("Modifier") }
                    }
                    Switch(checked = Prefs.sportEstOn, onCheckedChange = { Prefs.changeSportEstOn(it) })
                }
                Spacer(Modifier.height(14.dp))
                GradientButton("Commencer le sport", Icons.Rounded.PlayArrow, {
                    SportRun.start(ctx, Prefs.sportSelected, if (Prefs.sportEstOn) Prefs.sportEstMin * 60 else 0)
                })
            } else {
                Text(
                    if (est > 0) "${SportRun.type} · temps estimé ${fmtDur(est.toLong())}" else SportRun.type,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                DangerButton("Terminer et enregistrer", Icons.Rounded.Stop, { SportRun.complete(ctx, false) })
                TextButton(onClick = { showAbandon = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abandonner sans enregistrer")
                }
            }
        }

        // ----- Objectif de la semaine -----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = cs.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Objectif de la semaine", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        fmtMinutes(Prefs.sportGoalMin.toLong()), color = cs.tertiary,
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
                TextButton(onClick = { showGoal = true }) { Text("Modifier") }
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(weekSec.toFloat() / goalSec.toFloat(), cs.tertiary)
            Spacer(Modifier.height(8.dp))
            Text("Fait cette semaine : ${fmtDur(weekSec)}", color = cs.onSurfaceVariant, fontSize = 13.sp)
            if (leftSec > 0) {
                Text(
                    "Reste à faire : ${fmtMinutes(minCeil(leftSec))}",
                    fontWeight = FontWeight.SemiBold, color = cs.onSurface
                )
                val perDay = minCeil((leftSec + daysLeft - 1) / daysLeft)
                Text(
                    "Soit environ ${fmtMinutes(perDay)} par jour sur " +
                        (if (daysLeft > 1) "les $daysLeft jours restants" else "le dernier jour") +
                        " (aujourd'hui compris)",
                    color = cs.onSurfaceVariant, fontSize = 12.sp
                )
            } else {
                Text("Objectif atteint, bravo !", fontWeight = FontWeight.SemiBold, color = cs.secondary)
            }
            if (streak > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Série en cours : $streak jour" + (if (streak > 1) "s" else "") + " d'affilée",
                    color = cs.primary, fontSize = 13.sp
                )
            }
        }

        // ----- Répartition par jour -----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Répartition par jour", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Divise l'objectif de la semaine en séances quotidiennes",
                        color = cs.onSurfaceVariant, fontSize = 13.sp
                    )
                }
                Switch(checked = Prefs.sportSplit, onCheckedChange = { Prefs.changeSportSplit(it) })
            }
            if (Prefs.sportSplit) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !Prefs.sportSplitCustom,
                        onClick = { Prefs.changeSportSplitCustom(false) },
                        label = { Text("Automatique") }
                    )
                    FilterChip(
                        selected = Prefs.sportSplitCustom,
                        onClick = { Prefs.changeSportSplitCustom(true) },
                        label = { Text("Personnalisée") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (!Prefs.sportSplitCustom) {
                    Text("Jours d'entraînement par semaine", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(1, 2, 3, 4, 5, 6, 7)) { n ->
                            FilterChip(
                                selected = Prefs.sportSplitDays == n,
                                onClick = { Prefs.changeSportSplitDays(n) },
                                label = { Text("$n") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${Prefs.sportSplitDays} jour" + (if (Prefs.sportSplitDays > 1) "s" else "") +
                            " × ${fmtMinutes(perDayMin.toLong())} par jour",
                        color = cs.onSurfaceVariant, fontSize = 13.sp
                    )
                } else {
                    val plan = Prefs.sportDayMin
                    val total = plan.sum()
                    Text(
                        DAY_NAMES.indices.filter { plan[it] > 0 }
                            .joinToString("  ·  ") { "${DAY_NAMES[it].take(3)}. ${plan[it]} min" }
                            .ifEmpty { "Aucun jour planifié" },
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Total planifié : ${fmtMinutes(total.toLong())} " +
                            "(objectif : ${fmtMinutes(Prefs.sportGoalMin.toLong())})",
                        color = if (total == Prefs.sportGoalMin) cs.secondary else cs.tertiary,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = { showPlan = true }) { Text("Modifier le planning") }
                }
                Spacer(Modifier.height(12.dp))
                if (targetSec > 0) {
                    val leftToday = (targetSec - todaySec).coerceAtLeast(0L)
                    Text(
                        "Objectif d'aujourd'hui : ${fmtMinutes(targetSec / 60)}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(todaySec.toFloat() / targetSec.toFloat(), cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (leftToday > 0) "Reste aujourd'hui : ${fmtMinutes(minCeil(leftToday))}"
                        else "Objectif du jour atteint !",
                        color = if (leftToday > 0) cs.onSurfaceVariant else cs.secondary, fontSize = 13.sp
                    )
                } else {
                    Text(
                        if (Prefs.sportSplitCustom) "Jour de repos prévu aujourd'hui"
                        else "Jour de repos : votre quota de séances de la semaine est atteint",
                        color = cs.onSurfaceVariant, fontSize = 13.sp
                    )
                }
            }
        }

        // ----- Total du jour -----
        AppCard {
            Text("Sport d'aujourd'hui", color = cs.onSurfaceVariant, fontSize = 14.sp)
            Text(fmtDur(todaySec), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.tertiary)
            Text(
                when (todayRecs.size) {
                    0 -> "Aucune séance pour l'instant"
                    1 -> "1 séance effectuée"
                    else -> "${todayRecs.size} séances effectuées"
                },
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
    if (showOther) {
        SportTypeDialog({ showOther = false }) { name, save ->
            if (save) Prefs.addSportType(name)
            val existing = Prefs.sportTypes.firstOrNull { it.equals(name, ignoreCase = true) }
            Prefs.changeSportSelected(existing ?: name)
            showOther = false
        }
    }
    if (showEst) {
        MinutesDialog("Temps estimé", Prefs.sportEstMin, { showEst = false }) { m ->
            Prefs.changeSportEstMin(m)
            showEst = false
        }
    }
    if (showGoal) {
        MinutesDialog("Objectif de la semaine", Prefs.sportGoalMin, { showGoal = false }) { m ->
            Prefs.changeSportGoalMin(m)
            showGoal = false
        }
    }
    if (showPlan) {
        PlanDialog(Prefs.sportDayMin, { showPlan = false }) { l ->
            Prefs.changeSportDayMin(l)
            showPlan = false
        }
    }
    if (showDelType) {
        ConfirmDialog(
            "Supprimer ce sport ?",
            "« ${Prefs.sportSelected} » sera retiré de votre liste.",
            "Supprimer",
            onConfirm = { Prefs.removeSportType(Prefs.sportSelected); showDelType = false },
            onDismiss = { showDelType = false }
        )
    }
    if (showAbandon) {
        ConfirmDialog(
            "Abandonner la séance ?",
            "Le temps écoulé ne sera pas enregistré.",
            "Abandonner",
            onConfirm = { SportRun.abandon(ctx); showAbandon = false },
            onDismiss = { showAbandon = false }
        )
    }
}
