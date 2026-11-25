package com.j2me.multibox.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.j2me.multibox.MultiBoxApp
import com.j2me.multibox.emulator.J2meEngineAdapter
import com.j2me.multibox.ipc.BoxStatus
import com.j2me.multibox.macro.MacroRunner
import com.j2me.multibox.macro.MacroStep
import com.j2me.multibox.storage.BoxStorageManager
import com.j2me.multibox.ui.box.BoxActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Abstract Foreground Service hosting a single isolated J2ME Box in its own process (:box_N).
 * Maintains CPU Partial WakeLock and WifiLock so games keep running when screen is off.
 */
abstract class BoxRunnerService : Service() {

    companion object {
        const val ACTION_START_BOX = "com.j2me.multibox.ACTION_START_BOX"
        const val ACTION_STOP_BOX = "com.j2me.multibox.ACTION_STOP_BOX"
        const val ACTION_STATUS_BROADCAST = "com.j2me.multibox.BOX_STATUS"
        const val EXTRA_BOX_ID = "extra_box_id"
        const val EXTRA_BOX_NAME = "extra_box_name"
        const val EXTRA_JAR_PATH = "extra_jar_path"
        const val EXTRA_SLOT = "extra_slot"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_MEMORY_MB = "extra_memory_mb"

        // Process-local engine registry accessed by BoxActivity running in the same :box_N process
        @Volatile
        var currentEngine: J2meEngineAdapter? = null
            internal set
        @Volatile
        var currentBoxId: String? = null
            internal set

        fun serviceClassForSlot(slot: Int): Class<out BoxRunnerService> = when (slot) {
            1 -> BoxRunnerService1::class.java
            2 -> BoxRunnerService2::class.java
            3 -> BoxRunnerService3::class.java
            4 -> BoxRunnerService4::class.java
            5 -> BoxRunnerService5::class.java
            6 -> BoxRunnerService6::class.java
            7 -> BoxRunnerService7::class.java
            8 -> BoxRunnerService8::class.java
            else -> throw IllegalArgumentException("Invalid slot: $slot")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var slot: Int = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BOX -> {
                stopBoxAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_BOX -> {
                val boxId = intent.getStringExtra(EXTRA_BOX_ID) ?: return START_NOT_STICKY
                val boxName = intent.getStringExtra(EXTRA_BOX_NAME) ?: boxId
                val jarPath = intent.getStringExtra(EXTRA_JAR_PATH) ?: return START_NOT_STICKY
                slot = intent.getIntExtra(EXTRA_SLOT, 1)

                startForegroundWithNotification(boxId, boxName, "Running in background")
                acquireLocks()
                bootEngine(boxId, boxName, jarPath)
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(boxId: String, boxName: String, contentText: String) {
        val openIntent = Intent(this, BoxActivity.activityClassForSlot(slot)).apply {
            putExtra(EXTRA_BOX_ID, boxId)
            putExtra(EXTRA_BOX_NAME, boxName)
            putExtra(EXTRA_SLOT, slot)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, slot, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, MultiBoxApp.CHANNEL_BOX_RUNNER)
            .setContentTitle("Box: $boxName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1000 + slot, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1000 + slot, notification)
        }
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "J2MEMultiBox::BoxWakeLock$slot").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12h safety cap, renewed on activity
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "J2MEMultiBox::BoxWifiLock$slot").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun bootEngine(boxId: String, boxName: String, jarPath: String) {
        currentBoxId = boxId
        val storage = BoxStorageManager(filesDir)
        val rmsDir = storage.getRmsDir(boxId)
        val config = storage.loadConfig(boxId)

        val engine = J2meEngineAdapter(
            boxId = boxId,
            jarPath = jarPath,
            rmsDir = rmsDir,
            config = config
        )
        currentEngine = engine

        val watchdog = ReconnectWatchdog(
            onRelaunchRequested = {
                broadcastStatus(boxId, BoxStatus.RECONNECTING)
                engine.relaunchMidlet()
                broadcastStatus(boxId, if (engine.renderController.isHeadless()) BoxStatus.ONLINE_HEADLESS else BoxStatus.ONLINE_FOREGROUND)
            }
        )

        engine.onDisconnectDetected = {
            scope.launch { watchdog.triggerReconnectNow() }
        }
        engine.onNetworkActivity = {
            watchdog.onNetworkActivity()
        }

        scope.launch {
            broadcastStatus(boxId, BoxStatus.STARTING)
            engine.startMidlet()
            broadcastStatus(boxId, BoxStatus.ONLINE_HEADLESS)
        }
    }

    fun notifyCaptchaRequired(boxId: String, boxName: String, prompt: String) {
        broadcastStatus(boxId, BoxStatus.AWAITING_CAPTCHA)
        val openIntent = Intent(this, BoxActivity.activityClassForSlot(slot)).apply {
            putExtra(EXTRA_BOX_ID, boxId)
            putExtra(EXTRA_BOX_NAME, boxName)
            putExtra(EXTRA_SLOT, slot)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            this, 2000 + slot, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alert = NotificationCompat.Builder(this, MultiBoxApp.CHANNEL_CAPTCHA_ALERT)
            .setContentTitle("Captcha Required: $boxName")
            .setContentText(prompt)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(2000 + slot, alert)
    }

    private fun broadcastStatus(boxId: String, status: BoxStatus) {
        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
        val intent = Intent(ACTION_STATUS_BROADCAST).apply {
            setPackage(packageName)
            putExtra(EXTRA_BOX_ID, boxId)
            putExtra(EXTRA_SLOT, slot)
            putExtra(EXTRA_STATUS, status.name)
            putExtra(EXTRA_MEMORY_MB, usedMb)
        }
        sendBroadcast(intent)
    }

    private fun stopBoxAndSelf() {
        currentBoxId?.let { broadcastStatus(it, BoxStatus.OFFLINE) }
        currentEngine?.stopMidlet()
        currentEngine = null
        currentBoxId = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }
}

class BoxRunnerService1 : BoxRunnerService()
class BoxRunnerService2 : BoxRunnerService()
class BoxRunnerService3 : BoxRunnerService()
class BoxRunnerService4 : BoxRunnerService()
class BoxRunnerService5 : BoxRunnerService()
class BoxRunnerService6 : BoxRunnerService()
class BoxRunnerService7 : BoxRunnerService()
class BoxRunnerService8 : BoxRunnerService()
