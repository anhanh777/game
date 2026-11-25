package com.j2me.multibox.service

import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decouples J2ME game logic/network ticking from Android GPU Surface rendering.
 * When minimized or screen-off, surface is detached and rendering drops to 0 FPS
 * while MIDlet threads remain active.
 */
class HeadlessRenderController {

    private val headless = AtomicBoolean(true)
    @Volatile
    private var activeSurface: Surface? = null

    @Synchronized
    fun attachSurface(surface: Surface) {
        activeSurface = surface
        headless.set(false)
    }

    @Synchronized
    fun detachSurface() {
        activeSurface = null
        headless.set(true)
    }

    fun isHeadless(): Boolean = headless.get()

    fun getSurface(): Surface? = activeSurface
}
