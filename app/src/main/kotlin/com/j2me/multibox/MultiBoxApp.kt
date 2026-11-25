package com.j2me.multibox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.preference.PreferenceManager
import ru.playsoftware.j2meloader.EmulatorApplication
import ru.playsoftware.j2meloader.util.Constants
import java.io.File

class MultiBoxApp : Application() {

    companion object {
        const val CHANNEL_BOX_RUNNER = "box_runner_channel"
        const val CHANNEL_CAPTCHA_ALERT = "captcha_alert_channel"
    }

    override fun onCreate() {
        super.onCreate()
        EmulatorApplication.init(this)
        initEmulatorDir()
        createNotificationChannels()
    }

    private fun initEmulatorDir() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sp.contains(Constants.PREF_EMULATOR_DIR)) {
            val envDir = File(filesDir, "j2me_env").apply { mkdirs() }
            sp.edit().putString(Constants.PREF_EMULATOR_DIR, envDir.absolutePath).apply()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val runnerChannel = NotificationChannel(
                CHANNEL_BOX_RUNNER,
                "Box Background Runner",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps active Java game boxes running in the background."
            }

            val captchaChannel = NotificationChannel(
                CHANNEL_CAPTCHA_ALERT,
                "Captcha & Login Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a box requires manual Captcha input."
            }

            manager.createNotificationChannel(runnerChannel)
            manager.createNotificationChannel(captchaChannel)
        }
    }
}
