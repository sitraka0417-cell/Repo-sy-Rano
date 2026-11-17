package mg.sitraka.ranorepo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage

// ---------- Thèmes ----------

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4DB6FF), onPrimary = Color(0xFF00263F),
    secondary = Color(0xFF2EE6B6), onSecondary = Color(0xFF00382B),
    tertiary = Color(0xFFFFB74D), onTertiary = Color(0xFF3B2500),
    background = Color(0xFF0A1020), onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF111A2E), onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF1B2742), onSurfaceVariant = Color(0xFFA9B6D0),
    outline = Color(0xFF3A4A6B), error = Color(0xFFFF6B6B)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0A74D9), onPrimary = Color.White,
    secondary = Color(0xFF00A884), onSecondary = Color.White,
    tertiary = Color(0xFFF08A00), onTertiary = Color.White,
    background = Color(0xFFF3F6FC), onBackground = Color(0xFF0F1A2E),
    surface = Color.White, onSurface = Color(0xFF0F1A2E),
    surfaceVariant = Color(0xFFE6ECF8), onSurfaceVariant = Color(0xFF55627D),
    outline = Color(0xFF9AA8C4), error = Color(0xFFD93025)
)

private val SpecialScheme = darkColorScheme(
    primary = Color(0xFFFF7AC6), onPrimary = Color(0xFF3A0A29),
    secondary = Color(0xFF7CF5E0), onSecondary = Color(0xFF00352E),
    tertiary = Color(0xFFFFD166), onTertiary = Color(0xFF3B2A00),
    background = Color(0xFF150B2E), onBackground = Color(0xFFF5EDFF),
    surface = Color(0xFF241349), onSurface = Color(0xFFF5EDFF),
    surfaceVariant = Color(0xFF34205F), onSurfaceVariant = Color(0xFFCBB9F0),
    outline = Color(0xFF6A52A3), error = Color(0xFFFF7B7B)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val t = Prefs.theme
    val scheme: ColorScheme = when (t) {
        "light" -> LightScheme
        "special" -> SpecialScheme
        else -> DarkScheme
    }
    val view = LocalView.current
    SideEffect {
        val w = view.context.findActivity()?.window
        if (w != null) {
            val ctl = WindowCompat.getInsetsController(w, view)
            ctl.isAppearanceLightStatusBars = t == "light"
            ctl.isAppearanceLightNavigationBars = t == "light"
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun appBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return if (Prefs.theme == "special")
        Brush.verticalGradient(listOf(Color(0xFF1E0B47), Color(0xFF0B2B4D)))
    else Brush.verticalGradient(listOf(cs.background, cs.background))
}

@Composable
fun heroBrush(): Brush =
    Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))

// ---------- Composants ----------

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.surface.copy(alpha = if (Prefs.theme == "special") 0.78f else 1f),
            contentColor = cs.onSurface
        ),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun GradientButton(
    text: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(heroBrush(), alpha = if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.onPrimary)
            Spacer(Modifier.width(10.dp))
            Text(text, color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun DangerButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.error.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Ring(
    progress: Float, diameter: Dp, stroke: Dp, brush: Brush,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(250), label = "ring")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = stroke.toPx()
            val arcSize = Size(this.size.width - st, this.size.height - st)
            val tl = Offset(st / 2, st / 2)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = arcSize, style = Stroke(width = st, cap = StrokeCap.Round)
            )
            if (p > 0.001f) {
                drawArc(
                    brush = brush, startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = tl, size = arcSize, style = Stroke(width = st, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun Avatar(url: String?, name: String?, diameter: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(diameter).clip(CircleShape).background(heroBrush()),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = "Photo de profil",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else if (!name.isNullOrBlank()) {
            Text(
                name.first().uppercase(), color = cs.onPrimary,
                fontWeight = FontWeight.Bold, fontSize = (diameter.value * 0.42f).sp
            )
        } else {
            Icon(Icons.Rounded.Person, null, tint = cs.onPrimary, modifier = Modifier.size(diameter * 0.55f))
        }
    }
}

@Composable
fun GoogleButton(label: String, loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFDADCE0), shape)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color(0xFF4285F4))
            } else {
                Image(painterResource(R.drawable.ic_google), contentDescription = "Google", modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color(0xFF3C4043), fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
    }
}

@Composable
fun ModelChips(
    values: List<Int>, selected: Int, enabled: Boolean,
    onSelect: (Int) -> Unit, onOther: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { sec ->
            FilterChip(
                selected = sec == selected, onClick = { onSelect(sec) }, enabled = enabled,
                label = { Text(fmtDur(sec.toLong())) }
            )
        }
        item {
            AssistChip(
                onClick = onOther, enabled = enabled, label = { Text("Autre") },
                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
fun RowScope.NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3 && it.all { ch -> ch.isDigit() }) onChange(it) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

@Composable
fun DurationDialog(title: String, initialSec: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var h by remember { mutableStateOf((initialSec / 3600).toString()) }
    var m by remember { mutableStateOf(((initialSec % 3600) / 60).toString()) }
    var s by remember { mutableStateOf((initialSec % 60).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Heures", h) { h = it }
                NumField("Min", m) { m = it }
                NumField("Sec", s) { s = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = (h.toIntOrNull() ?: 0) * 3600 + (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
                if (total > 0) onOk(total)
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun BarChart(values: List<Long>, labels: List<String>, color: Color) {
    val cs = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val frac = v.toFloat() / max.toFloat()
                val h = (112.dp * frac).coerceAtLeast(3.dp)
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (v > 0) color else cs.surfaceVariant)
                )
                Text(
                    labels.getOrElse(i) { "" }, fontSize = 9.sp, maxLines = 1, softWrap = false,
                    color = cs.onSurfaceVariant, modifier = Modifier.height(16.dp).padding(top = 3.dp)
                )
            }
        }
    }
}
