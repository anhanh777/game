package com.j2me.multibox.macro

interface KeyInjector {
    fun pressKey(keyCode: Int)
    fun releaseKey(keyCode: Int)
    fun typeChar(ch: Char)
}
