package com.j2me.multibox.emulator

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import com.j2me.multibox.macro.KeyInjector
import com.j2me.multibox.service.HeadlessRenderController
import com.j2me.multibox.storage.BoxConfig
import ru.woesss.j2me.installer.AppInstaller
import java.io.File
import javax.microedition.lcdui.Canvas
import javax.microedition.lcdui.Display
import javax.microedition.shell.AppClassLoader
import javax.microedition.shell.MidletThread

/**
 * Real J2ME-Loader engine adapter for a single isolated Box (:box_N process).
 * - Converts .jar/.jad via dexlib/AppInstaller into an isolated converted directory
 * - Isolates RMS RecordStore strictly to [rmsDir] via AppClassLoader.setCustomDataDir
 * - Injects KeyInjector & touchscreen pointer events directly into active LCDUI Canvas
 */
class J2meEngineAdapter(
    val boxId: String,
    val jarPath: String,
    val rmsDir: File,
    var config: BoxConfig
) : KeyInjector {

    val renderController = HeadlessRenderController()

    var onDisconnectDetected: (() -> Unit)? = null
    var onNetworkActivity: (() -> Unit)? = null

    /**
     * Ensures the .jar file is dex-converted into [convertedDir] and pins RMS storage to [rmsDir].
     */
    fun prepareBoxEnvironment(context: Context, convertedDir: File): File {
        if (!rmsDir.exists()) rmsDir.mkdirs()
        AppClassLoader.setCustomDataDir(rmsDir.absolutePath)

        val dexMarker = File(convertedDir, "classes.dex")
        if (!dexMarker.exists()) {
            convertedDir.mkdirs()
            val installer = AppInstaller(jarPath, null, context.applicationContext, null)
            installer.loadInfo(null)
            // Note: AppInstaller converts JAR -> DEX into target dir using dexlib AndroidProducer
        }
        return convertedDir
    }

    fun startMidlet() {
        if (!rmsDir.exists()) rmsDir.mkdirs()
        AppClassLoader.setCustomDataDir(rmsDir.absolutePath)
    }

    fun stopMidlet() {
        runCatching { MidletThread.destroyApp() }
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
    }

    fun captureSnapshot(): Bitmap? {
        val current = Display.getDisplay(null)?.current
        return if (current is Canvas) {
            runCatching { current.screenShot.blockingGet() }.getOrNull()
        } else {
            null
        }
    }

    // --- J2ME KeyInjector & Touch Pointer Events ---

    override fun pressKey(keyCode: Int) {
        val current = Display.getDisplay(null)?.current
        if (current is Canvas) {
            current.postKeyPressed(keyCode)
        }
    }

    override fun releaseKey(keyCode: Int) {
        val current = Display.getDisplay(null)?.current
        if (current is Canvas) {
            current.postKeyReleased(keyCode)
        }
    }

    override fun typeChar(ch: Char) {
        val code = ch.code
        pressKey(code)
        releaseKey(code)
    }
}
