package com.j2me.multibox.macro

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroRunnerTest {

    private class RecordingKeyInjector : KeyInjector {
        val events = mutableListOf<String>()

        override fun pressKey(keyCode: Int) {
            events.add("PRESS:$keyCode")
        }

        override fun releaseKey(keyCode: Int) {
            events.add("RELEASE:$keyCode")
        }

        override fun typeChar(ch: Char) {
            events.add("CHAR:$ch")
        }
    }

    @Test
    fun test_login_macro_substitutes_credentials_and_injects_keys() = runTest {
        val injector = RecordingKeyInjector()
        val runner = MacroRunner(injector)

        val steps = listOf(
            MacroStep.KeyPress(J2meKeyCode.FIRE, holdMs = 10),
            MacroStep.TypeText("\${USERNAME}"),
            MacroStep.KeyPress(J2meKeyCode.DOWN, holdMs = 10),
            MacroStep.TypeText("\${PASSWORD}"),
            MacroStep.KeyPress(J2meKeyCode.FIRE, holdMs = 10)
        )

        runner.executeLoginMacro(
            steps = steps,
            username = "ab".toCharArray(),
            password = "12".toCharArray(),
            onAwaitManualInput = {}
        )

        assertEquals(
            listOf(
                "PRESS:${J2meKeyCode.FIRE}",
                "RELEASE:${J2meKeyCode.FIRE}",
                "CHAR:a",
                "CHAR:b",
                "PRESS:${J2meKeyCode.DOWN}",
                "RELEASE:${J2meKeyCode.DOWN}",
                "CHAR:1",
                "CHAR:2",
                "PRESS:${J2meKeyCode.FIRE}",
                "RELEASE:${J2meKeyCode.FIRE}"
            ),
            injector.events
        )
    }

    @Test
    fun test_await_manual_input_pauses_until_resumed() = runTest {
        val injector = RecordingKeyInjector()
        val runner = MacroRunner(injector)
        val resumeSignal = CompletableDeferred<Unit>()
        var promptSeen: String? = null

        val steps = listOf(
            MacroStep.TypeText("u"),
            MacroStep.AwaitManualInput("Enter Captcha"),
            MacroStep.KeyPress(J2meKeyCode.FIRE, holdMs = 10)
        )

        val job = launch {
            runner.executeLoginMacro(
                steps = steps,
                username = charArrayOf(),
                password = charArrayOf(),
                onAwaitManualInput = { prompt ->
                    promptSeen = prompt
                    resumeSignal.await()
                }
            )
        }

        testScheduler.advanceUntilIdle()
        assertEquals("Enter Captcha", promptSeen)
        assertEquals(listOf("CHAR:u"), injector.events)

        resumeSignal.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(
            listOf("CHAR:u", "PRESS:${J2meKeyCode.FIRE}", "RELEASE:${J2meKeyCode.FIRE}"),
            injector.events
        )
    }

    @Test
    fun test_json_roundtrip_serialization() {
        val original = listOf(
            MacroStep.Wait(2000),
            MacroStep.KeyPress(J2meKeyCode.FIRE, 80),
            MacroStep.TypeText("\${USERNAME}"),
            MacroStep.AwaitManualInput("Captcha")
        )
        val json = MacroStep.listToJson(original)
        val parsed = MacroStep.listFromJson(json)
        assertEquals(original, parsed)
    }
}
