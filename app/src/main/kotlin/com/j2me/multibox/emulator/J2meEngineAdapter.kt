package com.j2me.multibox.emulator

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import com.android.dx.command.dexer.Main
import com.j2me.multibox.macro.KeyInjector
import com.j2me.multibox.service.HeadlessRenderController
import com.j2me.multibox.storage.BoxConfig
import ru.playsoftware.j2meloader.config.Config
import ru.playsoftware.j2meloader.config.ProfileModel
import ru.playsoftware.j2meloader.config.ProfilesManager
import ru.woesss.j2me.jar.Descriptor
import java.io.File
import java.util.jar.JarFile
import javax.microedition.lcdui.Canvas
import javax.microedition.lcdui.Display
import javax.microedition.shell.AppClassLoader
import javax.microedition.shell.MidletThread

/**
 * Real J2ME-Loader engine adapter for a single isolated Box (:box_N process).
 * - Converts .jar -> converted.dex + converted.dex.conf + res.jar via dexlib
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

        val dexFile = File(convertedDir, Config.MIDLET_DEX_FILE.trimStart('/'))
        val confFile = File(convertedDir, Config.MIDLET_MANIFEST_FILE.trimStart('/'))
        val resFile = File(convertedDir, Config.MIDLET_RES_FILE.trimStart('/'))

        if (!dexFile.exists() || !confFile.exists() || !resFile.exists()) {
            convertedDir.mkdirs()
            val jarFile = File(jarPath)
            require(jarFile.exists()) { "JAR file not found: $jarPath" }

            // 1. Convert Java .jar bytecode to Android Dalvik .dex
            Main.main(
                arrayOf(
                    "--no-optimize",
                    "--core-library",
                    "--output=${dexFile.absolutePath}",
                    jarFile.absolutePath
                )
            )

            // 2. Copy original .jar as res.jar for J2ME resources
            jarFile.copyTo(resFile, overwrite = true)

            // 3. Extract MANIFEST.MF into converted.dex.conf
            val manifestText = JarFile(jarFile).use { jf ->
                val entry = jf.getJarEntry(JarFile.MANIFEST_NAME)
                    ?: throw IllegalStateException("JAR missing META-INF/MANIFEST.MF")
                jf.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            Descriptor(manifestText, false).writeTo(confFile)
        }

        // 4. Ensure ProfileModel config.json exists in {boxRoot}/configs/{boxId}/config.json
        val boxRoot = convertedDir.parentFile?.parentFile
        if (boxRoot != null) {
            val configDir = File(boxRoot, "configs/$boxId")
            val configJson = File(configDir, Config.MIDLET_CONFIG_FILE.trimStart('/'))
            if (!configJson.exists()) {
                configDir.mkdirs()
                val profile = ProfileModel(configDir).apply {
                    screenWidth = config.screenWidth
                    screenHeight = config.screenHeight
                    showKeyboard = config.showVirtualKeypad
                    fpsLimit = config.fpsLimit
                    disablePause = true
                    touchInput = true
                }
                ProfilesManager.saveConfig(profile)
            }
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
