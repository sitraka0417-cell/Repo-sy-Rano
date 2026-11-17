package mg.sitraka.ranorepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant

data class HEntry(val id: String, val ts: Long, val value: Long, val label: String = "")

@Composable
fun HistoryScreen(kind: String, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isRepo = kind == "repo"
    val isSport = kind == "sport"
    val accent = when {
        isRepo -> cs.secondary
        isSport -> cs.tertiary
        else -> cs.primary
    }
    var period by remember { mutableStateOf(PeriodKind.DAY) }
    var offset by remember { mutableIntStateOf(0) }
    var detailed by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<HEntry?>(null) }

    val r = remember(period, offset) { rangeFor(period, offset) }
    val all = when {
        isRepo -> Store.repos.map { HEntry(it.id, it.ts, it.sec.toLong()) }
        isSport -> Store.sports.map { HEntry(it.id, it.ts, it.sec.toLong(), it.type) }
        else -> Store.waters.map { HEntry(it.id, it.ts, 1L) }
    }
    val entries = all.filter { it.ts >= r.from && it.ts < r.to }.sortedByDescending { it.ts }
    val total = entries.sumOf { it.value }
    val vals = r.buckets.map { b -> entries.filter { it.ts >= b.from && it.ts < b.to }.sumOf { it.value } }
    val days = entries.groupBy { startOfDay(it.ts) }.toList().sortedByDescending { it.first }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Fermer") }
                Text(
                    when {
                        isRepo -> "Historique des repos"
                        isSport -> "Historique du sport"
                        else -> "Historique de l'eau"
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodKind.values().forEach { p ->
                    FilterChip(
                        selected = p == period,
                        onClick = { period = p; offset = 0 },
                        label = { Text(p.label) }
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset -= 1 }) { Icon(Icons.Rounded.ChevronLeft, "Précédent") }
                Text(
                    r.label, Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
                IconButton(onClick = { offset += 1 }, enabled = offset < 0) {
                    Icon(Icons.Rounded.ChevronRight, "Suivant")
                }
            }
        }
        item {
            AppCard {
                Text("Total", color = cs.onSurfaceVariant, fontSize = 14.sp)
                if (isRepo) {
                    Text(fmtDur(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.secondary)
                    Text("${entries.size} repos", color = cs.onSurfaceVariant, fontSize = 13.sp)
                } else if (isSport) {
                    Text(fmtDur(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.tertiary)
                    Text(
                        if (entries.size == 1) "1 séance" else "${entries.size} séances",
                        color = cs.onSurfaceVariant, fontSize = 13.sp
                    )
                    if (period == PeriodKind.WEEK) {
                        Text(
                            "Objectif de la semaine : ${fmtMinutes(Prefs.sportGoalMin.toLong())}",
                            color = cs.onSurfaceVariant, fontSize = 13.sp
                        )
                    }
                    entries.groupBy { it.label }.toList()
                        .sortedByDescending { p -> p.second.sumOf { e -> e.value } }
                        .forEach { (name, l) ->
                            Text(
                                "$name : ${fmtDur(l.sumOf { e -> e.value })}",
                                color = cs.onSurfaceVariant, fontSize = 13.sp
                            )
                        }
                } else {
                    Text(
                        "$total verre(s)", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary
                    )
                    Text("≈ ${fmtLitres(total.toInt())} L", color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        item {
            AppCard {
                BarChart(vals, r.buckets.map { it.label }, accent)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Vue détaillée", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = detailed, onCheckedChange = { detailed = it })
            }
        }
        if (days.isEmpty()) {
            item {
                Text(
                    "Aucune donnée sur cette période", color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center
                )
            }
        }
        items(days, key = { it.first }) { day ->
            val list = day.second
            val date = Instant.ofEpochMilli(day.first).atZone(zone()).toLocalDate()
            val sum = list.sumOf { it.value }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDay(date), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isRepo || isSport) fmtDur(sum) else "$sum verre(s)",
                        fontWeight = FontWeight.Bold, color = accent
                    )
                }
                if (detailed) {
                    Spacer(Modifier.height(6.dp))
                    list.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtHour(e.ts), fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                when {
                                    isRepo -> fmtDur(e.value)
                                    isSport -> "${e.label} · ${fmtDur(e.value)}"
                                    else -> "1 verre (${Prefs.cupMl} ml)"
                                },
                                Modifier.weight(1f)
                            )
                            IconButton(onClick = { toDelete = e }) {
                                Icon(Icons.Rounded.Delete, "Supprimer", tint = cs.error)
                            }
                        }
                    }
                }
            }
        }
    }

    val d = toDelete
    if (d != null) {
        ConfirmDialog(
            "Supprimer cette entrée ?",
            "L'entrée de ${fmtHour(d.ts)} sera supprimée de l'historique.",
            "Supprimer",
            onConfirm = {
                when {
                    isRepo -> Store.deleteRepo(d.id)
                    isSport -> Store.deleteSport(d.id)
                    else -> Store.deleteWater(d.id)
                }
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}
