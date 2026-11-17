package mg.sitraka.ranorepo

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ---------- Notifications & alarme ----------

object Notifs {
    const val CH_TIMER = "timer_v1"
    const val CH_ALERT = "alert_v1"
    const val CH_WATER = "water_v1"
    const val ID_FG = 1
    const val ID_ALERT = 2
    const val ID_WATER = 3

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Chronomètre en cours", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Fin de repos / de travail", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WATER, "Rappels pour boire", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, attrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }

    fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

object AlarmSound {
    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    fun start(c: Context) {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            p.setDataSource(c, uri)
            p.isLooping = true
            p.prepare()
            p.start()
            mp = p
            handler.postDelayed(stopper, 30_000)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        handler.removeCallbacks(stopper)
        try {
            mp?.stop()
        } catch (_: Exception) {
        }
        try {
            mp?.release()
        } catch (_: Exception) {
        }
        mp = null
        Chrono.ringing = false
    }
}

// ---------- État du chronomètre (partagé UI <-> service) ----------

enum class Phase { IDLE, PAUSE, WORK, WAIT_PAUSE, WAIT_WORK }

object Chrono {
    var phase by mutableStateOf(Phase.IDLE)
    var remainingMs by mutableLongStateOf(0L)
    var totalMs by mutableLongStateOf(0L)
    var auto by mutableStateOf(false)
    var ringing by mutableStateOf(false)
    var alertTitle by mutableStateOf("")
    var alertText by mutableStateOf("")
}

class TimerService : Service() {
    companion object {
        const val ACTION_PAUSE = "mg.sitraka.ranorepo.PAUSE"
        const val ACTION_AUTO = "mg.sitraka.ranorepo.AUTO"
        const val ACTION_NEXT = "mg.sitraka.ranorepo.NEXT"
        const val ACTION_STOP = "mg.sitraka.ranorepo.STOP"
        const val ACTION_SILENCE = "mg.sitraka.ranorepo.SILENCE"
        const val ACTION_END = "mg.sitraka.ranorepo.END"

        fun send(c: Context, action: String, sec: Int = 0) {
            val i = Intent(c, TimerService::class.java).setAction(action).putExtra("sec", sec)
            try {
                ContextCompat.startForegroundService(c, i)
            } catch (e: Exception) {
                // Certains appareils refusent le démarrage en premier plan : on tente un démarrage simple
                try {
                    c.startService(i)
                } catch (e2: Exception) {
                    android.widget.Toast.makeText(c, "Impossible de démarrer le chronomètre", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var token = 0
    private var endElapsed = 0L

    private val nm: NotificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val am: AlarmManager by lazy { getSystemService(AlarmManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        try {
            handleCommand(intent)
        } catch (e: Exception) {
            Chrono.phase = Phase.IDLE
            Chrono.remainingMs = 0
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleCommand(intent: Intent?) {
        val action = intent?.action
        val sec = intent?.getIntExtra("sec", 300) ?: 300
        val tok = intent?.getIntExtra("token", -1) ?: -1
        when (action) {
            ACTION_PAUSE -> {
                Chrono.auto = false
                begin(Phase.PAUSE, sec * 1000L)
            }
            ACTION_AUTO -> {
                Chrono.auto = true
                begin(Phase.WORK, Prefs.workSec * 1000L)
            }
            ACTION_NEXT -> next()
            ACTION_STOP -> stopAll()
            ACTION_SILENCE -> {
                AlarmSound.stop()
                nm.cancel(Notifs.ID_ALERT)
            }
            ACTION_END -> {
                if (tok == token) onPhaseEnd()
            }
        }
        if (Chrono.phase == Phase.IDLE && !Chrono.ringing) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Cycle de vie des phases ---

    private fun begin(p: Phase, ms: Long) {
        AlarmSound.stop()
        nm.cancel(Notifs.ID_ALERT)
        ticker?.cancel()
        token++
        Chrono.phase = p
        Chrono.totalMs = ms
        Chrono.remainingMs = ms
        endElapsed = SystemClock.elapsedRealtime() + ms
        scheduleEndAlarm(System.currentTimeMillis() + ms)
        refreshNotif()
        val my = token
        ticker = scope.launch {
            while (isActive) {
                val left = endElapsed - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    Chrono.remainingMs = 0
                    if (my == token) onPhaseEnd()
                    break
                }
                Chrono.remainingMs = left
                delay(200)
            }
        }
    }

    private fun toWait(p: Phase) {
        ticker?.cancel()
        token++
        cancelEndAlarm()
        Chrono.phase = p
        Chrono.remainingMs = 0
        Chrono.totalMs = 0
        refreshNotif()
    }

    private fun onPhaseEnd() {
        when (Chrono.phase) {
            Phase.PAUSE -> {
                Store.addRepo((Chrono.totalMs / 1000).toInt())
                if (Chrono.auto) {
                    if (Prefs.autoConfirm) toWait(Phase.WAIT_WORK) else begin(Phase.WORK, Prefs.workSec * 1000L)
                } else {
                    finishIdle()
                }
                ring("Repo terminé", "Au travail maintenant")
                if (Chrono.phase == Phase.IDLE) watchIdle()
            }
            Phase.WORK -> {
                if (Prefs.autoConfirm) toWait(Phase.WAIT_PAUSE) else begin(Phase.PAUSE, Prefs.autoPauseSec * 1000L)
                ring("Temps de pause", "Prenez ${fmtDur(Prefs.autoPauseSec.toLong())} de repos")
            }
            else -> {}
        }
    }

    private fun next() {
        when (Chrono.phase) {
            Phase.WAIT_PAUSE -> begin(Phase.PAUSE, Prefs.autoPauseSec * 1000L)
            Phase.WAIT_WORK -> begin(Phase.WORK, Prefs.workSec * 1000L)
            else -> {}
        }
    }

    private fun finishIdle() {
        ticker?.cancel()
        token++
        cancelEndAlarm()
        Chrono.phase = Phase.IDLE
        Chrono.remainingMs = 0
        Chrono.auto = false
    }

    private fun watchIdle() {
        scope.launch {
            while (Chrono.ringing) delay(500)
            if (Chrono.phase == Phase.IDLE) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopAll() {
        if (Chrono.phase == Phase.PAUSE) {
            val el = ((Chrono.totalMs - Chrono.remainingMs) / 1000).toInt()
            if (el >= 5) Store.addRepo(el)
        }
        ticker?.cancel()
        token++
        cancelEndAlarm()
        AlarmSound.stop()
        nm.cancel(Notifs.ID_ALERT)
        Chrono.phase = Phase.IDLE
        Chrono.auto = false
        Chrono.remainingMs = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ring(title: String, text: String) {
        Chrono.alertTitle = title
        Chrono.alertText = text
        Chrono.ringing = true
        AlarmSound.start(this)
        val b = NotificationCompat.Builder(this, Notifs.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(Notifs.openApp(this))
            .addAction(0, "Arrêter l'alarme", servicePi(ACTION_SILENCE, 41))
        if (Chrono.phase == Phase.WAIT_PAUSE) b.addAction(0, "Je commence la pause", servicePi(ACTION_NEXT, 42))
        if (Chrono.phase == Phase.WAIT_WORK) b.addAction(0, "Je reprends le travail", servicePi(ACTION_NEXT, 42))
        nm.notify(Notifs.ID_ALERT, b.build())
    }

    // --- Notification de premier plan ---

    private fun servicePi(action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(
        this, code, Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotif(): Notification {
        val ph = Chrono.phase
        val title = when (ph) {
            Phase.PAUSE -> "Repos en cours"
            Phase.WORK -> "Travail en cours"
            Phase.WAIT_PAUSE -> "Temps de pause"
            Phase.WAIT_WORK -> "Temps de travail"
            Phase.IDLE -> "Rano & Repo"
        }
        val text = when (ph) {
            Phase.PAUSE -> "Profitez de votre pause"
            Phase.WORK -> "Compte à rebours avant la prochaine pause"
            Phase.WAIT_PAUSE -> "Touchez pour commencer la pause"
            Phase.WAIT_WORK -> "Repos terminé, au travail maintenant"
            Phase.IDLE -> ""
        }
        val b = NotificationCompat.Builder(this, Notifs.CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notifs.openApp(this))
        if (ph == Phase.PAUSE || ph == Phase.WORK) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + Chrono.remainingMs)
        }
        if (ph == Phase.WAIT_PAUSE) b.addAction(0, "Je commence la pause", servicePi(ACTION_NEXT, 42))
        if (ph == Phase.WAIT_WORK) b.addAction(0, "Je reprends le travail", servicePi(ACTION_NEXT, 42))
        if (ph != Phase.IDLE) b.addAction(0, "Arrêter", servicePi(ACTION_STOP, 43))
        return b.build()
    }

    private fun startFg() {
        val n = buildNotif()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifs.ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifs.ID_FG, n)
            }
        } catch (e: Exception) {
            // Repli : sans type de service, puis sans premier plan (le chrono continue dans l'appli)
            try {
                startForeground(Notifs.ID_FG, n)
            } catch (e2: Exception) {
            }
        }
    }

    private fun refreshNotif() {
        nm.notify(Notifs.ID_FG, buildNotif())
    }

    // --- Alarme système (fiable même en veille profonde) ---

    private fun endPi(): PendingIntent = PendingIntent.getForegroundService(
        this, 11,
        Intent(this, TimerService::class.java).setAction(ACTION_END).putExtra("token", token),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleEndAlarm(atWall: Long) {
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atWall, Notifs.openApp(this)), endPi())
        } catch (e: Exception) {
            // Alarme refusée par l'appareil : le décompte interne prend le relais
        }
    }

    private fun cancelEndAlarm() {
        try {
            am.cancel(endPi())
        } catch (e: Exception) {
        }
    }
}

// ---------- Rappels pour boire ----------

object WaterReminder {
    const val A_REMIND = "mg.sitraka.ranorepo.WATER_REMIND"
    const val A_DRINK = "mg.sitraka.ranorepo.WATER_DRINK"
    const val A_SNOOZE = "mg.sitraka.ranorepo.WATER_SNOOZE"

    private fun remindPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 21, Intent(c, WaterReceiver::class.java).setAction(A_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(c: Context, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifs.openApp(c)), remindPi(c))
        Prefs.changeWaterNext(at)
    }

    fun scheduleFromNow(c: Context) {
        schedule(c, System.currentTimeMillis() + Prefs.waterIntervalSec * 1000L)
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(remindPi(c))
        c.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_WATER)
        Prefs.changeWaterNext(0L)
    }
}

class WaterReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        when (i.action) {
            WaterReminder.A_REMIND -> {
                Prefs.changeWaterNext(0L)
                val cups = Store.waters.count { it.ts >= startOfDay(System.currentTimeMillis()) }
                val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val drink = PendingIntent.getBroadcast(
                    c, 31, Intent(c, WaterReceiver::class.java).setAction(WaterReminder.A_DRINK), flags
                )
                val later = PendingIntent.getBroadcast(
                    c, 32, Intent(c, WaterReceiver::class.java).setAction(WaterReminder.A_SNOOZE), flags
                )
                val n = NotificationCompat.Builder(c, Notifs.CH_WATER)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("C'est l'heure de boire de l'eau")
                    .setContentText("Déjà $cups sur ${Prefs.waterGoal} verres aujourd'hui")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(Notifs.openApp(c))
                    .addAction(0, "J'ai bu un verre", drink)
                    .addAction(0, "Plus tard (10 min)", later)
                    .build()
                nm.notify(Notifs.ID_WATER, n)
            }
            WaterReminder.A_DRINK -> {
                nm.cancel(Notifs.ID_WATER)
                Store.addWater()
                if (Prefs.waterAuto) WaterReminder.scheduleFromNow(c)
            }
            WaterReminder.A_SNOOZE -> {
                nm.cancel(Notifs.ID_WATER)
                WaterReminder.schedule(c, System.currentTimeMillis() + 10 * 60_000L)
            }
        }
    }
}
