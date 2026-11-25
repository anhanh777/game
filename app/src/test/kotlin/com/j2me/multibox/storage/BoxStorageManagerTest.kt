package com.j2me.multibox.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class BoxStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test_rms_isolation_between_boxes() {
        val manager = BoxStorageManager(tempFolder.root)
        val box1 = manager.createBoxSandbox("box-1")
        val box2 = manager.createBoxSandbox("box-2")

        val save1 = File(box1.rmsDir, "session.rms")
        save1.writeText("account1_token")

        assertTrue(save1.exists())
        assertFalse(File(box2.rmsDir, "session.rms").exists())
        assertEquals(box1.rmsDir, manager.getRmsDir("box-1"))
    }

    @Test
    fun test_delete_box_removes_all_data() {
        val manager = BoxStorageManager(tempFolder.root)
        val box1 = manager.createBoxSandbox("box-1")
        val box2 = manager.createBoxSandbox("box-2")
        File(box1.rmsDir, "data.rms").writeText("secret")

        val deleted = manager.deleteBoxSandbox("box-1")
        assertTrue(deleted)
        assertFalse(box1.boxRoot.exists())
        assertTrue(box2.boxRoot.exists())
    }

    @Test
    fun test_save_and_load_box_config() {
        val manager = BoxStorageManager(tempFolder.root)
        manager.createBoxSandbox("box-1")
        val config = BoxConfig(
            screenWidth = 320,
            screenHeight = 480,
            showVirtualKeypad = true,
            fpsLimit = 30
        )
        manager.saveConfig("box-1", config)

        val loaded = manager.loadConfig("box-1")
        assertEquals(config, loaded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_import_corrupted_jar_throws_and_cleans_up() {
        val manager = BoxStorageManager(tempFolder.root)
        val invalidStream = ByteArrayInputStream("NOT_A_VALID_JAR".toByteArray())
        try {
            manager.importJarFile("game-bad", invalidStream)
        } finally {
            assertFalse(File(tempFolder.root, "games/game-bad.jar").exists())
        }
    }

    @Test
    fun test_import_valid_jar_succeeds() {
        val manager = BoxStorageManager(tempFolder.root)
        val zipHeaderBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val file = manager.importJarFile("nso-230", ByteArrayInputStream(zipHeaderBytes))

        assertTrue(file.exists())
        assertEquals("nso-230.jar", file.name)
    }
}
