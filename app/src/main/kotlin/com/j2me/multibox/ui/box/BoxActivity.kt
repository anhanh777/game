package com.j2me.multibox.ui.box

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.j2me.multibox.service.BoxRunnerService
import com.j2me.multibox.storage.BoxStorageManager
import com.j2me.multibox.ui.MainActivity
import java.io.File
import java.io.FileOutputStream
import javax.microedition.shell.AppClassLoader
import javax.microedition.shell.MicroActivity

/**
 * Full-screen interactive J2ME Box Activity extending J2ME-Loader's MicroActivity.
 * Runs inside :box_N process with isolated RMS dataDir, keeps MIDlet running in background
 * when minimized or screen-off (pauseDisabled = true, keepAliveOnDestroy = true),
 * and provides a floating Minimize button back to the Dashboard.
 */
abstract class BoxActivity : MicroActivity() {

    companion object {
        fun activityClassForSlot(slot: Int): Class<out BoxActivity> = when (slot) {
            1 -> BoxActivity1::class.java
            2 -> BoxActivity2::class.java
            3 -> BoxActivity3::class.java
            4 -> BoxActivity4::class.java
            5 -> BoxActivity5::class.java
            6 -> BoxActivity6::class.java
            7 -> BoxActivity7::class.java
            8 -> BoxActivity8::class.java
            else -> throw IllegalArgumentException("Invalid slot: $slot")
        }
    }

    private var boxId: String = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        boxId = intent.getStringExtra(BoxRunnerService.EXTRA_BOX_ID)
            ?: BoxRunnerService.currentBoxId
            ?: "unknown"

        val storage = BoxStorageManager(filesDir)
        val rmsDir = storage.getRmsDir(boxId)
        AppClassLoader.setCustomDataDir(rmsDir.absolutePath)

        val engine = BoxRunnerService.currentEngine
        if (intent.data == null && engine != null) {
            val convertedDir = File(storage.getBoxPaths(boxId).boxRoot, "converted")
            engine.prepareBoxEnvironment(this, convertedDir)
            intent.data = Uri.fromFile(convertedDir)
            intent.putExtra(KEY_MIDLET_NAME, intent.getStringExtra(BoxRunnerService.EXTRA_BOX_NAME) ?: boxId)
        }

        super.onCreate(savedInstanceState)

        // Keep MIDlet & network loop alive when minimized or screen turns off
        pauseDisabled = true
        keepAliveOnDestroy = true

        attachMinimizeOverlay()
    }

    private fun attachMinimizeOverlay() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(170, 20, 25, 38))
            setPadding(16, 8, 16, 8)
        }

        val minimizeBtn = Button(this).apply {
            text = "Thu nhỏ"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 163, 74))
            setOnClickListener { minimizeToDashboard() }
        }
        bar.addView(minimizeBtn)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = 24
            rightMargin = 24
        }
        root.addView(bar, params)
    }

    private fun minimizeToDashboard() {
        BoxRunnerService.currentEngine?.let { engine ->
            val snapshot = engine.captureSnapshot()
            if (snapshot != null) {
                val snapshotFile = BoxStorageManager(filesDir).getBoxPaths(boxId).snapshotFile
                runCatching {
                    FileOutputStream(snapshotFile).use { fos ->
                        snapshot.compress(Bitmap.CompressFormat.PNG, 85, fos)
                    }
                }
            }
            engine.detachSurface()
        }

        // Bring MainActivity Dashboard to front while keeping this :box_N process & MIDlet alive
        val dashboardIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(dashboardIntent)
    }
}

class BoxActivity1 : BoxActivity()
class BoxActivity2 : BoxActivity()
class BoxActivity3 : BoxActivity()
class BoxActivity4 : BoxActivity()
class BoxActivity5 : BoxActivity()
class BoxActivity6 : BoxActivity()
class BoxActivity7 : BoxActivity()
class BoxActivity8 : BoxActivity()
