package com.j2me.multibox.macro

import org.json.JSONArray
import org.json.JSONObject

object J2meKeyCode {
    const val UP = -1
    const val DOWN = -2
    const val LEFT = -3
    const val RIGHT = -4
    const val FIRE = -5
    const val SOFT_LEFT = -6
    const val SOFT_RIGHT = -7
    const val KEY_0 = 48
    const val KEY_1 = 49
    const val KEY_2 = 50
    const val KEY_3 = 51
    const val KEY_4 = 52
    const val KEY_5 = 53
    const val KEY_6 = 54
    const val KEY_7 = 55
    const val KEY_8 = 56
    const val KEY_9 = 57
    const val STAR = 42
    const val POUND = 35
}

sealed class MacroStep {
    data class Wait(val durationMs: Long) : MacroStep()
    data class KeyPress(val keyCode: Int, val holdMs: Long = 80L) : MacroStep()
    data class TypeText(val template: String) : MacroStep()
    data class AwaitManualInput(val prompt: String = "Enter Captcha") : MacroStep()

    companion object {
        fun listToJson(steps: List<MacroStep>): String {
            val array = JSONArray()
            for (step in steps) {
                val obj = JSONObject()
                when (step) {
                    is Wait -> {
                        obj.put("action", "WAIT")
                        obj.put("durationMs", step.durationMs)
                    }
                    is KeyPress -> {
                        obj.put("action", "KEY_PRESS")
                        obj.put("keyCode", step.keyCode)
                        obj.put("holdMs", step.holdMs)
                    }
                    is TypeText -> {
                        obj.put("action", "TYPE_TEXT")
                        obj.put("template", step.template)
                    }
                    is AwaitManualInput -> {
                        obj.put("action", "AWAIT_MANUAL_INPUT")
                        obj.put("prompt", step.prompt)
                    }
                }
                array.put(obj)
            }
            return array.toString(2)
        }

        fun listFromJson(json: String): List<MacroStep> {
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            val result = mutableListOf<MacroStep>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                when (obj.getString("action")) {
                    "WAIT" -> result.add(Wait(obj.getLong("durationMs")))
                    "KEY_PRESS" -> result.add(KeyPress(obj.getInt("keyCode"), obj.optLong("holdMs", 80L)))
                    "TYPE_TEXT" -> result.add(TypeText(obj.getString("template")))
                    "AWAIT_MANUAL_INPUT" -> result.add(AwaitManualInput(obj.optString("prompt", "Enter Captcha")))
                }
            }
            return result
        }
    }
}
