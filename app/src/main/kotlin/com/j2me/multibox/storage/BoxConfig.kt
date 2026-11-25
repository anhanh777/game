package com.j2me.multibox.storage

import org.json.JSONObject

data class BoxConfig(
    val screenWidth: Int = 240,
    val screenHeight: Int = 320,
    val showVirtualKeypad: Boolean = true,
    val fpsLimit: Int = 0,
    val keepAwake: Boolean = true
) {
    fun toJson(): String = JSONObject().apply {
        put("screenWidth", screenWidth)
        put("screenHeight", screenHeight)
        put("showVirtualKeypad", showVirtualKeypad)
        put("fpsLimit", fpsLimit)
        put("keepAwake", keepAwake)
    }.toString(2)

    companion object {
        fun fromJson(json: String): BoxConfig {
            val obj = JSONObject(json)
            return BoxConfig(
                screenWidth = obj.optInt("screenWidth", 240),
                screenHeight = obj.optInt("screenHeight", 320),
                showVirtualKeypad = obj.optBoolean("showVirtualKeypad", true),
                fpsLimit = obj.optInt("fpsLimit", 30),
                keepAwake = obj.optBoolean("keepAwake", true)
            )
        }
    }
}
