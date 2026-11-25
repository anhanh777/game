package com.j2me.multibox.macro

import kotlinx.coroutines.delay

/**
 * One-shot sequential keyboard macro runner for universal J2ME login & reconnect.
 * Streams CharArray credentials directly into KeyInjector without allocating immutable Strings.
 */
class MacroRunner(
    private val injector: KeyInjector,
    private val interCharDelayMs: Long = 35L
) {

    suspend fun executeLoginMacro(
        steps: List<MacroStep>,
        username: CharArray,
        password: CharArray,
        onAwaitManualInput: suspend (prompt: String) -> Unit
    ) {
        for (step in steps) {
            when (step) {
                is MacroStep.Wait -> delay(step.durationMs)
                is MacroStep.KeyPress -> {
                    injector.pressKey(step.keyCode)
                    delay(step.holdMs)
                    injector.releaseKey(step.keyCode)
                }
                is MacroStep.TypeText -> emitTemplate(step.template, username, password)
                is MacroStep.AwaitManualInput -> onAwaitManualInput(step.prompt)
            }
        }
    }

    private suspend fun emitTemplate(template: String, username: CharArray, password: CharArray) {
        var i = 0
        while (i < template.length) {
            when {
                template.startsWith("\${USERNAME}", i) -> {
                    for (ch in username) {
                        injector.typeChar(ch)
                        delay(interCharDelayMs)
                    }
                    i += "\${USERNAME}".length
                }
                template.startsWith("\${PASSWORD}", i) -> {
                    for (ch in password) {
                        injector.typeChar(ch)
                        delay(interCharDelayMs)
                    }
                    i += "\${PASSWORD}".length
                }
                else -> {
                    injector.typeChar(template[i])
                    delay(interCharDelayMs)
                    i++
                }
            }
        }
    }
}
