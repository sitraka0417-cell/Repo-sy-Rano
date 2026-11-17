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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WaterScreen(onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val start = startOfDay(System.currentTimeMillis())
    val todays = Store.waters.filter { it.ts >= start }.sortedBy { it.ts }
    val count = todays.size
    val goal = Prefs.waterGoal
    var showInterval by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ----- Compteur -----
        AppCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(
                    count.toFloat() / goal.toFloat(), 240.dp, 16.dp,
                    Brush.linearGradient(listOf(cs.primary, cs.secondary))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.WaterDrop, null, tint = cs.primary, modifier = Modifier.size(30.dp))
                        Text("$count", fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                        Text("sur $goal verres", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("≈ ${fmtLitres(count)} L bus aujourd'hui", color = cs.onSurfaceVariant)
                if (count >= goal) {
                    Text(
                        "Objectif atteint, bravo !", color = cs.secondary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                GradientButton("J'ai bu un verre", Icons.Rounded.WaterDrop, {
                    Store.addWater()
                    if (Prefs.waterAuto) WaterReminder.scheduleFromNow(ctx)
                })
                TextButton(
                    onClick = { todays.lastOrNull()?.let { Store.deleteWater(it.id) } },
                    enabled = todays.isNotEmpty()
                ) { Text("Annuler le dernier verre") }
            }
        }

        // ----- Heures des verres du jour -----
        AppCard {
            Text("Aujourd'hui", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (todays.isEmpty()) {
                Text("Aucun verre enregistré pour l'instant", color = cs.onSurfaceVariant, fontSize = 13.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(todays) { w ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(fmtHour(w.ts), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onHistory, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.History, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Historique")
            }
        }

        // ----- Objectif et taille du verre -----
        AppCard {
            Text("Objectif quotidien", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { Prefs.changeWaterGoal(goal - 1) }) { Icon(Icons.Rounded.Remove, "Moins") }
                Text(
                    "$goal verres", Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                FilledIconButton(onClick = { Prefs.changeWaterGoal(goal + 1) }) { Icon(Icons.Rounded.Add, "Plus") }
            }
            Spacer(Modifier.height(12.dp))
            Text("Taille d'un verre", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(125, 150, 200, 250, 300, 500)) { ml ->
                    FilterChip(
                        selected = Prefs.cupMl == ml, onClick = { Prefs.changeCupMl(ml) },
                        label = { Text("$ml ml") }
                    )
                }
            }
        }

        // ----- Rappels automatiques -----
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rappels automatiques", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Une alarme sonne après chaque verre pour vous rappeler de boire",
                        color = cs.onSurfaceVariant, fontSize = 13.sp
                    )
                }
                Switch(checked = Prefs.waterAuto, onCheckedChange = {
                    Prefs.changeWaterAuto(it)
                    if (it) WaterReminder.scheduleFromNow(ctx) else WaterReminder.cancel(ctx)
                })
            }
            if (Prefs.waterAuto) {
                Spacer(Modifier.height(14.dp))
                Text("Intervalle : ${fmtDur(Prefs.waterIntervalSec.toLong())}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ModelChips(
                    values = listOf(1800, 2700, 3600, 5400, 7200),
                    selected = Prefs.waterIntervalSec, enabled = true,
                    onSelect = {
                        Prefs.setWaterInterval(it)
                        WaterReminder.scheduleFromNow(ctx)
                    },
                    onOther = { showInterval = true }
                )
                Spacer(Modifier.height(10.dp))
                val next = Prefs.waterNext
                Text(
                    if (next > System.currentTimeMillis()) "Prochain rappel à ${fmtHour(next)}"
                    else "Touchez « J'ai bu un verre » pour lancer le prochain rappel",
                    color = cs.secondary, fontSize = 13.sp
                )
                Text(
                    "À chaque rappel, un bouton permet de confirmer que vous avez bu.",
                    color = cs.onSurfaceVariant, fontSize = 12.sp
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Désactivé : vous enregistrez vos verres manuellement, à l'heure de votre choix.",
                    color = cs.onSurfaceVariant, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showInterval) {
        DurationDialog("Intervalle entre les rappels", Prefs.waterIntervalSec, { showInterval = false }) { sec ->
            Prefs.setWaterInterval(sec)
            WaterReminder.scheduleFromNow(ctx)
            showInterval = false
        }
    }
}
