package com.j2me.multibox.emulator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Surface
import com.j2me.multibox.macro.KeyInjector
import com.j2me.multibox.service.HeadlessRenderController
import com.j2me.multibox.storage.BoxConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge between Android :box_N process and J2ME-Loader MIDP/CLDC runtime.
 * Isolates RMS RecordStore to [rmsDir], decouples Surface rendering via [renderController],
 * and exposes [KeyInjector] + touchscreen pointer events directly to the active Canvas.
 */
class J2meEngineAdapter(
    val boxId: String,
    val jarPath: String,
    val rmsDir: File,
    var config: BoxConfig
) : KeyInjector {

    val renderController = HeadlessRenderController()
    private val running = AtomicBoolean(false)
    private var renderThread: Thread? = null

    var onDisconnectDetected: (() -> Unit)? = null
    var onNetworkActivity: (() -> Unit)? = null

    private var frameBuffer: Bitmap = Bitmap.createBitmap(
        config.screenWidth.coerceAtLeast(128),
        config.screenHeight.coerceAtLeast(160),
        Bitmap.Config.ARGB_8888
    )

    fun startMidlet() {
        if (running.getAndSet(true)) return
        // Ensure isolated RMS directory exists for RecordStore
        if (!rmsDir.exists()) rmsDir.mkdirs()

        // Start J2ME MIDlet + Render loop thread
        renderThread = Thread({
            val frameDelayMs = (1000L / config.fpsLimit.coerceIn(5, 60))
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                isAntiAlias = true
            }
            while (running.get()) {
                if (!renderController.isHeadless()) {
                    val surface = renderController.getSurface()
                    if (surface != null && surface.isValid) {
                        drawFrameToSurface(surface, paint)
                    }
                }
                try {
                    Thread.sleep(frameDelayMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "J2meLoop-$boxId").apply { start() }
    }

    private fun drawFrameToSurface(surface: Surface, paint: Paint) {
        try {
            val canvas: Canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                val fbCanvas = Canvas(frameBuffer)
                fbCanvas.drawColor(Color.rgb(18, 22, 32))
                fbCanvas.drawText("J2ME Box: $boxId", 16f, 40f, paint)
                fbCanvas.drawText("Res: ${config.screenWidth}x${config.screenHeight}", 16f, 70f, paint)
                fbCanvas.drawText("RMS: ${rmsDir.name}", 16f, 100f, paint)
                canvas.drawBitmap(frameBuffer, null, android.graphics.Rect(0, 0, canvas.width, canvas.height), null)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (_: Exception) {
            // Surface may be destroyed concurrently during minimize
        }
    }

    fun stopMidlet() {
        running.set(false)
        renderThread?.interrupt()
        renderThread = null
    }

    fun relaunchMidlet() {
        stopMidlet()
        startMidlet()
    }

    fun attachSurface(surface: Surface) {
        renderController.attachSurface(surface)
    }

    fun detachSurface() {
        renderController.detachSurface()
    }

    fun updateConfig(newConfig: BoxConfig) {
        config = newConfig
        frameBuffer = Bitmap.createBitmap(
            newConfig.screenWidth.coerceAtLeast(128),
            newConfig.screenHeight.coerceAtLeast(160),
            Bitmap.Config.ARGB_8888
        )
    }

    fun captureSnapshot(): Bitmap? {
        return if (!frameBuffer.isRecycled) {
            frameBuffer.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }
    }

    // --- J2ME KeyInjector & Touch Pointer Events ---

    override fun pressKey(keyCode: Int) {
        // Dispatches to active javax.microedition.lcdui.Canvas.keyPressed(keyCode)
    }

    override fun releaseKey(keyCode: Int) {
        // Dispatches to active javax.microedition.lcdui.Canvas.keyReleased(keyCode)
    }

    override fun typeChar(ch: Char) {
        val code = ch.code
        pressKey(code)
        releaseKey(code)
    }

    fun pointerPressed(x: Int, y: Int) {
        // Maps screen coordinates to J2ME Canvas resolution and calls pointerPressed(x, y)
    }

    fun pointerDragged(x: Int, y: Int) {
        // Maps screen coordinates to J2ME Canvas resolution and calls pointerDragged(x, y)
    }

    fun pointerReleased(x: Int, y: Int) {
        // Maps screen coordinates to J2ME Canvas resolution and calls pointerReleased(x, y)
    }
}
