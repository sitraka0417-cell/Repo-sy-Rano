package mg.sitraka.ranorepo

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

// ---------- Séance de sport en cours (chronomètre ou compte à rebours) ----------
// L'état est conservé dans les préférences : le chrono reste exact même si l'appli est fermée.

object SportRun {
    const val A_END = "mg.sitraka.ranorepo.SPORT_END"
    const val A_STOP = "mg.sitraka.ranorepo.SPORT_STOP"

    var running by mutableStateOf(false)
        private set
    var startWall by mutableLongStateOf(0L)
        private set
    var estSec by mutableIntStateOf(0)
        private set
    var type by mutableStateOf("")
        private set
    var endedText by mutableStateOf<String?>(null)

    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("rr_sport_run", Context.MODE_PRIVATE)
        running = sp.getBoolean("running", false)
        startWall = sp.getLong("start", 0L)
        estSec = sp.getInt("est", 0)
        type = sp.getString("type", "") ?: ""
        if (isDue(System.currentTimeMillis())) complete(c, true)
    }

    fun isDue(now: Long): Boolean = running && estSec > 0 && now >= startWall + estSec * 1000L

    fun start(c: Context, sportType: String, est: Int) {
        if (running) return
        val ctx = c.applicationContext
        val now = System.currentTimeMillis()
        running = true
        startWall = now
        estSec = est
        type = sportType
        endedText = null
        sp.edit().putBoolean("running", true).putLong("start", now).putInt("est", est)
            .putString("type", sportType).apply()
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_SPORT_END)
        } catch (_: Exception) {
        }
        if (est > 0) scheduleEnd(ctx, now + est * 1000L)
        postRunNotif(ctx)
    }

    // Termine la séance et l'enregistre. auto = le temps estimé est écoulé.
    fun complete(c: Context, auto: Boolean) {
        if (!running) return
        val ctx = c.applicationContext
        var sec = ((System.currentTimeMillis() - startWall) / 1000L).toInt()
        if (auto && estSec > 0) sec = estSec
        val t = type
        running = false
        sp.edit().putBoolean("running", false).apply()
        cancelEnd(ctx)
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_SPORT_RUN)
        } catch (_: Exception) {
        }
        if (sec >= 10) {
            Store.addSport(sec, t)
            endedText = "Séance enregistrée : $t, ${fmtDur(sec.toLong())}"
        } else {
            endedText = "Séance trop courte pour être enregistrée"
        }
        if (auto) postEndNotif(ctx, sec >= 10, t, sec)
    }

    // Abandonne la séance sans rien enregistrer.
    fun abandon(c: Context) {
        if (!running) return
        val ctx = c.applicationContext
        running = false
        endedText = null
        sp.edit().putBoolean("running", false).apply()
        cancelEnd(ctx)
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_SPORT_RUN)
        } catch (_: Exception) {
        }
    }

    fun dismissEnded(c: Context) {
        endedText = null
        try {
            c.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_SPORT_END)
        } catch (_: Exception) {
        }
    }

    // --- Alarme et notifications ---

    private fun endPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 21, Intent(c, SportReceiver::class.java).setAction(A_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun stopPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 22, Intent(c, SportReceiver::class.java).setAction(A_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleEnd(c: Context, at: Long) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifs.openApp(c)), endPi(c))
        } catch (_: Exception) {
        }
    }

    private fun cancelEnd(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java).cancel(endPi(c))
        } catch (_: Exception) {
        }
    }

    private fun postRunNotif(c: Context) {
        try {
            val b = NotificationCompat.Builder(c, Notifs.CH_TIMER)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Sport en cours")
                .setContentText(if (estSec > 0) "$type · temps estimé : ${fmtDur(estSec.toLong())}" else type)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setContentIntent(Notifs.openApp(c))
                .addAction(0, "Terminer", stopPi(c))
            if (estSec > 0) {
                b.setChronometerCountDown(true).setWhen(startWall + estSec * 1000L)
            } else {
                b.setWhen(startWall)
            }
            c.getSystemService(NotificationManager::class.java).notify(Notifs.ID_SPORT_RUN, b.build())
        } catch (_: Exception) {
        }
    }

    private fun postEndNotif(c: Context, saved: Boolean, t: String, sec: Int) {
        try {
            val b = NotificationCompat.Builder(c, Notifs.CH_SPORT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Temps de sport écoulé")
                .setContentText(if (saved) "Séance enregistrée : $t, ${fmtDur(sec.toLong())}" else "Séance trop courte")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(Notifs.openApp(c))
            c.getSystemService(NotificationManager::class.java).notify(Notifs.ID_SPORT_END, b.build())
        } catch (_: Exception) {
        }
    }
}

class SportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                SportRun.A_END -> SportRun.complete(context, true)
                SportRun.A_STOP -> SportRun.complete(context, false)
            }
        } catch (_: Exception) {
        }
    }
}
