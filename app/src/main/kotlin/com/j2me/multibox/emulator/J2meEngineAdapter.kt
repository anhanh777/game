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

    companion object {
        private val dexLock = Any()
    }

    /**
     * Ensures the .jar file is dex-converted into [convertedDir] and pins RMS storage to [rmsDir].
     * Caches the heavy dx conversion per .jar file so subsequent boxes open instantaneously.
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

            val cacheDir = File(jarFile.parentFile, "${jarFile.nameWithoutExtension}_dex_cache")
            val cacheDex = File(cacheDir, Config.MIDLET_DEX_FILE.trimStart('/'))
            val cacheConf = File(cacheDir, Config.MIDLET_MANIFEST_FILE.trimStart('/'))
            val cacheRes = File(cacheDir, Config.MIDLET_RES_FILE.trimStart('/'))

            synchronized(dexLock) {
                if (!cacheDex.exists() || !cacheConf.exists() || !cacheRes.exists()) {
                    cacheDir.mkdirs()
                    // 1. Convert Java .jar bytecode to Android Dalvik .dex
                    Main.main(
                        arrayOf(
                            "--no-optimize",
                            "--core-library",
                            "--output=${cacheDex.absolutePath}",
                            jarFile.absolutePath
                        )
                    )

                    // 2. Copy original .jar as res.jar for J2ME resources
                    jarFile.copyTo(cacheRes, overwrite = true)

                    // 3. Extract MANIFEST.MF into converted.dex.conf
                    val manifestText = JarFile(jarFile).use { jf ->
                        val entry = jf.getJarEntry(JarFile.MANIFEST_NAME)
                            ?: throw IllegalStateException("JAR missing META-INF/MANIFEST.MF")
                        jf.getInputStream(entry).bufferedReader().use { it.readText() }
                    }
                    Descriptor(manifestText, false).writeTo(cacheConf)
                }
            }

            cacheDex.copyTo(dexFile, overwrite = true)
            cacheRes.copyTo(resFile, overwrite = true)
            cacheConf.copyTo(confFile, overwrite = true)
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
                    fpsLimit = 0
                    graphicsMode = 3 // Hardware-accelerated 2D Canvas for zero touch/input lag
                    parallelRedrawScreen = true
                    immediateMode = true
                    vkFeedback = true
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
