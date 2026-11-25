package com.j2me.multibox.ui.box

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.j2me.multibox.service.BoxRunnerService
import com.j2me.multibox.storage.BoxStorageManager
import com.j2me.multibox.ui.MainActivity
import ru.playsoftware.j2meloader.config.Config
import ru.playsoftware.j2meloader.config.ProfileModel
import ru.playsoftware.j2meloader.config.ProfilesManager
import ru.playsoftware.j2meloader.util.Constants
import java.io.File
import java.io.FileOutputStream
import javax.microedition.shell.AppClassLoader
import javax.microedition.shell.MicroActivity

/**
 * Full-screen interactive J2ME Box Activity extending J2ME-Loader's MicroActivity.
 * Runs inside :box_N process with isolated RMS dataDir, keeps MIDlet running in background
 * when minimized or screen-off (pauseDisabled = true, keepAliveOnDestroy = true),
 * and provides floating Settings (Screen Size / Keypad) + Minimize buttons.
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

        val convertedDir = File(storage.getBoxPaths(boxId).boxRoot, "converted/$boxId")
        val jarPath = intent.getStringExtra(BoxRunnerService.EXTRA_JAR_PATH)
        if (intent.data == null) {
            if ((!File(convertedDir, "converted.dex").exists() || !File(storage.getBoxPaths(boxId).boxRoot, "configs/$boxId/config.json").exists()) && !jarPath.isNullOrBlank()) {
                val adapter = BoxRunnerService.currentEngine ?: com.j2me.multibox.emulator.J2meEngineAdapter(
                    boxId = boxId,
                    jarPath = jarPath,
                    rmsDir = rmsDir,
                    config = storage.loadConfig(boxId)
                )
                adapter.prepareBoxEnvironment(this, convertedDir)
            }
            intent.data = Uri.parse(convertedDir.absolutePath)
            intent.putExtra(Constants.KEY_MIDLET_NAME, intent.getStringExtra(BoxRunnerService.EXTRA_BOX_NAME) ?: boxId)
        }

        super.onCreate(savedInstanceState)

        // Keep MIDlet & network loop alive when minimized or screen turns off
        pauseDisabled = true
        keepAliveOnDestroy = true

        attachOverlayControls()
    }

    private fun attachOverlayControls() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(175, 15, 23, 42))
            setPadding(12, 6, 12, 6)
        }

        val settingsBtn = Button(this).apply {
            text = "Cỡ màn hình"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(56, 189, 248))
            setOnClickListener { showScreenSettingsDialog() }
        }

        val minimizeBtn = Button(this).apply {
            text = "Thu nhỏ"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(22, 163, 74))
            setOnClickListener { minimizeToDashboard() }
        }

        val btnMargin = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = 10 }

        bar.addView(settingsBtn, btnMargin)
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

    private fun showScreenSettingsDialog() {
        val storage = BoxStorageManager(filesDir)
        val boxConfig = storage.loadConfig(boxId)
        val configDir = File(storage.getBoxPaths(boxId).boxRoot, "configs/$boxId")
        val currentProfile = ProfilesManager.loadConfig(configDir) ?: ProfileModel(configDir)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 12)
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val widthInput = EditText(this).apply {
            hint = "Chiều rộng (vd: 240)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentProfile.screenWidth.toString())
        }
        val heightInput = EditText(this).apply {
            hint = "Chiều cao (vd: 320)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentProfile.screenHeight.toString())
        }

        val presets = listOf("240x320" to (240 to 320), "320x480" to (320 to 480), "360x640" to (360 to 640))
        presets.forEach { (label, size) ->
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                setOnClickListener {
                    widthInput.setText(size.first.toString())
                    heightInput.setText(size.second.toString())
                }
            }
            presetRow.addView(btn)
        }

        val scaleFitCheck = CheckBox(this).apply {
            text = "Phóng to vừa màn hình"
            isChecked = currentProfile.screenScaleToFit
        }
        val aspectCheck = CheckBox(this).apply {
            text = "Giữ tỷ lệ khung hình"
            isChecked = currentProfile.screenKeepAspectRatio
        }
        val keypadCheck = CheckBox(this).apply {
            text = "Hiện bàn phím ảo"
            isChecked = currentProfile.showKeyboard
        }

        container.addView(TextView(this).apply { text = "Chọn nhanh độ phân giải:" })
        container.addView(presetRow)
        container.addView(TextView(this).apply { text = "Chiều rộng (px):" })
        container.addView(widthInput)
        container.addView(TextView(this).apply { text = "Chiều cao (px):" })
        container.addView(heightInput)
        container.addView(scaleFitCheck)
        container.addView(aspectCheck)
        container.addView(keypadCheck)

        AlertDialog.Builder(this)
            .setTitle("Cài đặt màn hình Box")
            .setView(container)
            .setPositiveButton("Áp dụng") { _, _ ->
                val w = widthInput.text.toString().toIntOrNull()?.coerceIn(128, 1440) ?: 240
                val h = heightInput.text.toString().toIntOrNull()?.coerceIn(160, 2560) ?: 320

                val updatedBoxConfig = boxConfig.copy(
                    screenWidth = w,
                    screenHeight = h,
                    showVirtualKeypad = keypadCheck.isChecked
                )
                storage.saveConfig(boxId, updatedBoxConfig)

                currentProfile.dir = configDir
                currentProfile.screenWidth = w
                currentProfile.screenHeight = h
                currentProfile.screenScaleToFit = scaleFitCheck.isChecked
                currentProfile.screenKeepAspectRatio = aspectCheck.isChecked
                currentProfile.showKeyboard = keypadCheck.isChecked
                currentProfile.disablePause = true
                ProfilesManager.saveConfig(currentProfile)

                BoxRunnerService.currentEngine?.updateConfig(updatedBoxConfig)
                // Recreate activity to apply new Canvas resolution immediately
                recreate()
            }
            .setNegativeButton("Hủy", null)
            .show()
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
