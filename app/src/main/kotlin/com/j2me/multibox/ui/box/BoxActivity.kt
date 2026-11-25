package com.j2me.multibox.ui.box

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.j2me.multibox.emulator.J2meEngineAdapter
import com.j2me.multibox.service.BoxRunnerService
import com.j2me.multibox.storage.BoxStorageManager
import java.io.FileOutputStream

/**
 * Full-screen interactive J2ME Box view running in the same :box_N process as its BoxRunnerService.
 * Supports direct touchscreen input, toggleable virtual keypad, per-box resolution settings,
 * and Minimize (detaching Surface to 0 FPS headless mode while keeping the game running).
 */
abstract class BoxActivity : ComponentActivity() {

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

    private var engine: J2meEngineAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val boxId = intent.getStringExtra(BoxRunnerService.EXTRA_BOX_ID) ?: BoxRunnerService.currentBoxId ?: "unknown"
        val boxName = intent.getStringExtra(BoxRunnerService.EXTRA_BOX_NAME) ?: boxId
        engine = BoxRunnerService.currentEngine

        setContent {
            MaterialTheme {
                BoxFullScreen(
                    boxName = boxName,
                    engine = engine,
                    onSaveConfig = { w, h, showKeypad ->
                        engine?.let { eng ->
                            val updated = eng.config.copy(
                                screenWidth = w,
                                screenHeight = h,
                                showVirtualKeypad = showKeypad
                            )
                            eng.updateConfig(updated)
                            BoxStorageManager(filesDir).saveConfig(boxId, updated)
                        }
                    },
                    onMinimize = { minimizeBox(boxId) }
                )
            }
        }
    }

    private fun minimizeBox(boxId: String) {
        engine?.let { eng ->
            val snapshot = eng.captureSnapshot()
            if (snapshot != null) {
                val snapshotFile = BoxStorageManager(filesDir).getBoxPaths(boxId).snapshotFile
                FileOutputStream(snapshotFile).use { fos ->
                    snapshot.compress(Bitmap.CompressFormat.PNG, 85, fos)
                }
            }
            eng.detachSurface()
        }
        finish()
    }

    override fun onPause() {
        super.onPause()
        engine?.detachSurface()
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun BoxFullScreen(
    boxName: String,
    engine: J2meEngineAdapter?,
    onSaveConfig: (width: Int, height: Int, showKeypad: Boolean) -> Unit,
    onMinimize: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(engine?.config?.showVirtualKeypad ?: true) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top mini-toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B26))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = boxName, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row {
                IconButton(onClick = { showKeypad = !showKeypad }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Toggle Keypad", tint = Color.White)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Box Settings", tint = Color.White)
                }
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.CloseFullscreen, contentDescription = "Minimize Box", tint = Color(0xFF4ADE80))
                }
            }
        }

        // Game Canvas with direct touchscreen pass-through
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                engine?.attachSurface(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                engine?.attachSurface(holder.surface)
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                engine?.detachSurface()
                            }
                        })
                        setOnTouchListener { _, event ->
                            val x = event.x.toInt()
                            val y = event.y.toInt()
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> engine?.pointerPressed(x, y)
                                MotionEvent.ACTION_MOVE -> engine?.pointerDragged(x, y)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> engine?.pointerReleased(x, y)
                            }
                            true
                        }
                    }
                }
            )
        }

        if (showKeypad) {
            VirtualKeypadOverlay(
                onKeyDown = { code -> engine?.pressKey(code) },
                onKeyUp = { code -> engine?.releaseKey(code) }
            )
        }
    }

    if (showSettings) {
        var widthText by remember { mutableStateOf((engine?.config?.screenWidth ?: 240).toString()) }
        var heightText by remember { mutableStateOf((engine?.config?.screenHeight ?: 320).toString()) }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Cài đặt Box: $boxName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it.filter(Char::isDigit) },
                        label = { Text("Chiều rộng (px)") }
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter(Char::isDigit) },
                        label = { Text("Chiều cao (px)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val w = widthText.toIntOrNull()?.coerceIn(128, 1080) ?: 240
                    val h = heightText.toIntOrNull()?.coerceIn(160, 1920) ?: 320
                    onSaveConfig(w, h, showKeypad)
                    showSettings = false
                }) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Hủy") }
            }
        )
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
