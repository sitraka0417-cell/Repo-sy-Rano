package mg.sitraka.ranorepo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

// ---------- Utilitaires de temps ----------

fun zone(): ZoneId = ZoneId.systemDefault()

fun startOfDay(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate().atStartOfDay(zone()).toInstant().toEpochMilli()

fun LocalDate.ms(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()

fun fmtDur(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val parts = mutableListOf<String>()
    if (h > 0) parts.add("$h h")
    if (m > 0) parts.add("$m min")
    if (s > 0) parts.add("$s s")
    return if (parts.isEmpty()) "0 s" else parts.joinToString(" ")
}

fun fmtClock(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun fmtHour(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(zone()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun fmtDay(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }

fun fmtLitres(cups: Int): String =
    String.format(Locale.FRENCH, "%.2f", cups * Prefs.cupMl / 1000.0)

enum class PeriodKind(val label: String) { DAY("Jour"), WEEK("Semaine"), MONTH("Mois"), YEAR("Année") }

data class Bucket(val label: String, val from: Long, val to: Long)
data class PRange(val from: Long, val to: Long, val label: String, val buckets: List<Bucket>)

fun rangeFor(p: PeriodKind, offset: Int): PRange {
    val today = LocalDate.now(zone())
    return when (p) {
        PeriodKind.DAY -> {
            val d = today.plusDays(offset.toLong())
            val b = (0..23).map { h ->
                val s = d.ms() + h * 3_600_000L
                Bucket(if (h % 3 == 0) "$h" else "", s, s + 3_600_000L)
            }
            PRange(d.ms(), d.plusDays(1).ms(), if (offset == 0) "Aujourd'hui" else fmtDay(d), b)
        }
        PeriodKind.WEEK -> {
            val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            val names = listOf("L", "M", "M", "J", "V", "S", "D")
            val b = (0..6).map { i ->
                val d = mon.plusDays(i.toLong())
                Bucket(names[i], d.ms(), d.plusDays(1).ms())
            }
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
            val label = if (offset == 0) "Cette semaine" else "${mon.format(f)} – ${mon.plusDays(6).format(f)}"
            PRange(mon.ms(), mon.plusDays(7).ms(), label, b)
        }
        PeriodKind.MONTH -> {
            val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val n = first.lengthOfMonth()
            val b = (1..n).map { day ->
                val d = first.plusDays((day - 1).toLong())
                Bucket(if (day == 1 || day % 5 == 0) "$day" else "", d.ms(), d.plusDays(1).ms())
            }
            val label = first.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH))
                .replaceFirstChar { it.uppercase() }
            PRange(first.ms(), first.plusMonths(1).ms(), label, b)
        }
        PeriodKind.YEAR -> {
            val y = today.year + offset
            val letters = "JFMAMJJASOND"
            val b = (1..12).map { m ->
                val f = LocalDate.of(y, m, 1)
                Bucket(letters[m - 1].toString(), f.ms(), f.plusMonths(1).ms())
            }
            PRange(LocalDate.of(y, 1, 1).ms(), LocalDate.of(y + 1, 1, 1).ms(), "$y", b)
        }
    }
}

// ---------- Préférences ----------

object Prefs {
    private lateinit var sp: SharedPreferences
    val DEFAULT_MODELS = listOf(300, 15)
    val DEFAULT_SPORTS = listOf("Marche rapide", "Course")

    var theme by mutableStateOf("dark")
        private set
    var models by mutableStateOf(DEFAULT_MODELS)
        private set
    var selectedSec by mutableIntStateOf(300)
        private set
    var autoEnabled by mutableStateOf(false)
        private set
    var workSec by mutableIntStateOf(3600)
        private set
    var autoPauseSec by mutableIntStateOf(300)
        private set
    var autoConfirm by mutableStateOf(true)
        private set
    var waterGoal by mutableIntStateOf(15)
        private set
    var waterAuto by mutableStateOf(false)
        private set
    var waterIntervalSec by mutableIntStateOf(3600)
        private set
    var cupMl by mutableIntStateOf(125)
        private set
    var waterNext by mutableLongStateOf(0L)
        private set
    var pinHash by mutableStateOf<String?>(null)
        private set
    var pinLen by mutableIntStateOf(4)
        private set
    var biometric by mutableStateOf(false)
        private set
    var sportGoalMin by mutableIntStateOf(150)
        private set
    var sportTypes by mutableStateOf(DEFAULT_SPORTS)
        private set
    var sportSelected by mutableStateOf("Marche rapide")
        private set
    var sportEstOn by mutableStateOf(false)
        private set
    var sportEstMin by mutableIntStateOf(15)
        private set
    var sportSplit by mutableStateOf(false)
        private set
    var sportSplitCustom by mutableStateOf(false)
        private set
    var sportSplitDays by mutableIntStateOf(5)
        private set
    var sportDayMin by mutableStateOf(listOf(0, 0, 0, 0, 0, 0, 0))
        private set
    private var pinSalt = ""

    val pinEnabled: Boolean get() = pinHash != null

    fun init(c: Context) {
        sp = c.getSharedPreferences("rr_prefs", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "dark") ?: "dark"
        val m = (sp.getString("models", "300,15") ?: "300,15").split(",").mapNotNull { it.trim().toIntOrNull() }
        models = if (m.isEmpty()) DEFAULT_MODELS else m
        selectedSec = sp.getInt("selectedSec", 300)
        autoEnabled = sp.getBoolean("autoEnabled", false)
        workSec = sp.getInt("workSec", 3600)
        autoPauseSec = sp.getInt("autoPauseSec", 300)
        autoConfirm = sp.getBoolean("autoConfirm", true)
        waterGoal = sp.getInt("waterGoal", 15)
        waterAuto = sp.getBoolean("waterAuto", false)
        waterIntervalSec = sp.getInt("waterIntervalSec", 3600)
        cupMl = sp.getInt("cupMl", 125)
        waterNext = sp.getLong("waterNext", 0L)
        pinHash = sp.getString("pinHash", null)
        pinSalt = sp.getString("pinSalt", "") ?: ""
        pinLen = sp.getInt("pinLen", 4)
        biometric = sp.getBoolean("biometric", false)
        sportGoalMin = sp.getInt("sportGoalMin", 150)
        sportTypes = try {
            val a = JSONArray(sp.getString("sportTypes", null) ?: "[]")
            val l = (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }
            if (l.isEmpty()) DEFAULT_SPORTS else l
        } catch (_: Exception) {
            DEFAULT_SPORTS
        }
        sportSelected = sp.getString("sportSelected", null) ?: DEFAULT_SPORTS[0]
        sportEstOn = sp.getBoolean("sportEstOn", false)
        sportEstMin = sp.getInt("sportEstMin", 15)
        sportSplit = sp.getBoolean("sportSplit", false)
        sportSplitCustom = sp.getBoolean("sportSplitCustom", false)
        sportSplitDays = sp.getInt("sportSplitDays", 5).coerceIn(1, 7)
        val dm = (sp.getString("sportDayMin", "0,0,0,0,0,0,0") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
        sportDayMin = if (dm.size == 7) dm else listOf(0, 0, 0, 0, 0, 0, 0)
    }

    fun changeTheme(v: String) { theme = v; sp.edit().putString("theme", v).apply() }
    fun selectSec(v: Int) { selectedSec = v; sp.edit().putInt("selectedSec", v).apply() }

    private fun saveModels(l: List<Int>) {
        models = l
        sp.edit().putString("models", l.joinToString(",")).apply()
    }
    fun addModel(sec: Int) { if (sec !in models) saveModels(models + sec) }
    fun replaceModel(old: Int, new: Int) {
        saveModels(models.map { if (it == old) new else it }.distinct())
        selectSec(new)
    }
    fun removeModel(sec: Int) {
        saveModels(models.filter { it != sec })
        selectSec(models.firstOrNull() ?: 300)
    }

    fun changeAutoEnabled(v: Boolean) { autoEnabled = v; sp.edit().putBoolean("autoEnabled", v).apply() }
    fun changeWorkSec(v: Int) { workSec = v; sp.edit().putInt("workSec", v).apply() }
    fun setAutoPause(v: Int) { autoPauseSec = v; sp.edit().putInt("autoPauseSec", v).apply() }
    fun changeAutoConfirm(v: Boolean) { autoConfirm = v; sp.edit().putBoolean("autoConfirm", v).apply() }

    fun changeWaterGoal(v: Int) { waterGoal = v.coerceIn(1, 40); sp.edit().putInt("waterGoal", waterGoal).apply() }
    fun changeWaterAuto(v: Boolean) { waterAuto = v; sp.edit().putBoolean("waterAuto", v).apply() }
    fun setWaterInterval(v: Int) { waterIntervalSec = v; sp.edit().putInt("waterIntervalSec", v).apply() }
    fun changeCupMl(v: Int) { cupMl = v; sp.edit().putInt("cupMl", v).apply() }
    fun changeWaterNext(v: Long) { waterNext = v; sp.edit().putLong("waterNext", v).apply() }

    // --- Sport ---
    fun changeSportGoalMin(v: Int) { sportGoalMin = v.coerceIn(1, 5000); sp.edit().putInt("sportGoalMin", sportGoalMin).apply() }
    private fun saveSportTypes(l: List<String>) {
        sportTypes = l
        sp.edit().putString("sportTypes", JSONArray(l).toString()).apply()
    }
    fun changeSportSelected(v: String) { sportSelected = v; sp.edit().putString("sportSelected", v).apply() }
    fun addSportType(name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && sportTypes.none { it.equals(n, ignoreCase = true) }) saveSportTypes(sportTypes + n)
    }
    fun removeSportType(name: String) {
        saveSportTypes(sportTypes.filter { it != name })
        changeSportSelected(sportTypes.firstOrNull() ?: DEFAULT_SPORTS[0])
    }
    fun changeSportEstOn(v: Boolean) { sportEstOn = v; sp.edit().putBoolean("sportEstOn", v).apply() }
    fun changeSportEstMin(v: Int) { sportEstMin = v.coerceIn(1, 1000); sp.edit().putInt("sportEstMin", sportEstMin).apply() }
    fun changeSportSplit(v: Boolean) { sportSplit = v; sp.edit().putBoolean("sportSplit", v).apply() }
    fun changeSportSplitCustom(v: Boolean) { sportSplitCustom = v; sp.edit().putBoolean("sportSplitCustom", v).apply() }
    fun changeSportSplitDays(v: Int) { sportSplitDays = v.coerceIn(1, 7); sp.edit().putInt("sportSplitDays", sportSplitDays).apply() }
    fun changeSportDayMin(l: List<Int>) {
        if (l.size == 7) {
            sportDayMin = l
            sp.edit().putString("sportDayMin", l.joinToString(",")).apply()
        }
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { String.format("%02x", it) }

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        val h = hash(salt, pin)
        pinSalt = salt
        pinHash = h
        pinLen = pin.length
        sp.edit().putString("pinSalt", salt).putString("pinHash", h).putInt("pinLen", pin.length).apply()
    }
    fun clearPin() {
        pinHash = null
        biometric = false
        sp.edit().remove("pinHash").remove("pinSalt").putBoolean("biometric", false).apply()
    }
    fun checkPin(pin: String): Boolean {
        val h = pinHash ?: return false
        return hash(pinSalt, pin) == h
    }
    fun changeBiometric(v: Boolean) { biometric = v; sp.edit().putBoolean("biometric", v).apply() }
}

// ---------- Données (historique) ----------

data class RepoRec(val id: String, val ts: Long, val sec: Int)
data class WaterRec(val id: String, val ts: Long)
data class SportRec(val id: String, val ts: Long, val sec: Int, val type: String)

object Store {
    val repos = mutableStateListOf<RepoRec>()
    val waters = mutableStateListOf<WaterRec>()
    val sports = mutableStateListOf<SportRec>()
    private lateinit var sp: SharedPreferences
    private val regs = mutableListOf<ListenerRegistration>()
    private var uid: String? = null

    fun init(c: Context) {
        sp = c.getSharedPreferences("rr_data", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(sp.getString("repos", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                repos.add(RepoRec(o.getString("id"), o.getLong("ts"), o.getInt("sec")))
            }
            val w = JSONArray(sp.getString("waters", "[]"))
            for (i in 0 until w.length()) {
                val o = w.getJSONObject(i)
                waters.add(WaterRec(o.getString("id"), o.getLong("ts")))
            }
            val s = JSONArray(sp.getString("sports", "[]"))
            for (i in 0 until s.length()) {
                val o = s.getJSONObject(i)
                sports.add(SportRec(o.getString("id"), o.getLong("ts"), o.getInt("sec"), o.optString("type", "Sport")))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val a = JSONArray()
        repos.forEach { a.put(JSONObject().put("id", it.id).put("ts", it.ts).put("sec", it.sec)) }
        val w = JSONArray()
        waters.forEach { w.put(JSONObject().put("id", it.id).put("ts", it.ts)) }
        val s = JSONArray()
        sports.forEach { s.put(JSONObject().put("id", it.id).put("ts", it.ts).put("sec", it.sec).put("type", it.type)) }
        sp.edit().putString("repos", a.toString()).putString("waters", w.toString()).putString("sports", s.toString()).apply()
    }

    private fun col(name: String) = FirebaseFirestore.getInstance()
        .collection("users").document(uid ?: "none").collection(name)

    fun addRepo(sec: Int) {
        val r = RepoRec(UUID.randomUUID().toString(), System.currentTimeMillis(), sec)
        repos.add(r)
        save()
        if (uid != null) col("repos").document(r.id).set(mapOf("ts" to r.ts, "sec" to r.sec))
    }

    fun addWater() {
        val r = WaterRec(UUID.randomUUID().toString(), System.currentTimeMillis())
        waters.add(r)
        save()
        if (uid != null) col("waters").document(r.id).set(mapOf("ts" to r.ts))
    }

    fun addSport(sec: Int, type: String) {
        val r = SportRec(UUID.randomUUID().toString(), System.currentTimeMillis(), sec, type)
        sports.add(r)
        save()
        if (uid != null) col("sports").document(r.id).set(mapOf("ts" to r.ts, "sec" to r.sec, "type" to r.type))
    }

    fun deleteSport(id: String) {
        sports.removeAll { it.id == id }
        save()
        if (uid != null) col("sports").document(id).delete()
    }

    fun deleteRepo(id: String) {
        repos.removeAll { it.id == id }
        save()
        if (uid != null) col("repos").document(id).delete()
    }

    fun deleteWater(id: String) {
        waters.removeAll { it.id == id }
        save()
        if (uid != null) col("waters").document(id).delete()
    }

    fun clearLocal() {
        repos.clear()
        waters.clear()
        sports.clear()
        save()
    }

    fun startSync(u: String) {
        if (uid == u) return
        stopSync()
        uid = u
        repos.forEach { col("repos").document(it.id).set(mapOf("ts" to it.ts, "sec" to it.sec)) }
        waters.forEach { col("waters").document(it.id).set(mapOf("ts" to it.ts)) }
        sports.forEach { col("sports").document(it.id).set(mapOf("ts" to it.ts, "sec" to it.sec, "type" to it.type)) }

        regs.add(col("repos").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            var changed = false
            for (ch in snap.documentChanges) {
                val d = ch.document
                when (ch.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        if (repos.none { it.id == d.id }) {
                            val ts = d.getLong("ts")
                            val sec = d.getLong("sec")
                            if (ts != null && sec != null) {
                                repos.add(RepoRec(d.id, ts, sec.toInt()))
                                changed = true
                            }
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (repos.removeAll { it.id == d.id }) changed = true
                    }
                }
            }
            if (changed) save()
        })

        regs.add(col("waters").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            var changed = false
            for (ch in snap.documentChanges) {
                val d = ch.document
                when (ch.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        if (waters.none { it.id == d.id }) {
                            val ts = d.getLong("ts")
                            if (ts != null) {
                                waters.add(WaterRec(d.id, ts))
                                changed = true
                            }
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (waters.removeAll { it.id == d.id }) changed = true
                    }
                }
            }
            if (changed) save()
        })
        listenSports()
    }

    private fun listenSports() {
        regs.add(col("sports").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            var changed = false
            for (ch in snap.documentChanges) {
                val d = ch.document
                when (ch.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        if (sports.none { it.id == d.id }) {
                            val ts = d.getLong("ts")
                            val sec = d.getLong("sec")
                            if (ts != null && sec != null) {
                                sports.add(SportRec(d.id, ts, sec.toInt(), d.getString("type") ?: "Sport"))
                                changed = true
                            }
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (sports.removeAll { it.id == d.id }) changed = true
                    }
                }
            }
            if (changed) save()
        })
    }

    fun stopSync() {
        regs.forEach { it.remove() }
        regs.clear()
        uid = null
    }
}
