package com.j2me.multibox.ipc

enum class BoxStatus {
    OFFLINE,
    STARTING,
    ONLINE_FOREGROUND,
    ONLINE_HEADLESS,
    RECONNECTING,
    AWAITING_CAPTCHA
}

data class BoxRuntimeInfo(
    val boxId: String,
    val slot: Int,
    val status: BoxStatus,
    val memoryMb: Int = 0,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)
