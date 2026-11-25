package com.j2me.multibox.storage

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class BoxPaths(
    val boxRoot: File,
    val rmsDir: File,
    val configFile: File,
    val snapshotFile: File
)

/**
 * Manages strictly isolated filesystem sandboxes for each Box account
 * and validates imported .jar/.jad game files.
 */
class BoxStorageManager(private val rootDir: File) {

    private val boxesDir: File = File(rootDir, "boxes").apply { mkdirs() }
    private val gamesDir: File = File(rootDir, "games").apply { mkdirs() }

    private fun sanitizeId(id: String): String {
        require(id.isNotBlank()) { "ID must not be blank" }
        val clean = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        require(clean != "." && clean != "..") { "Invalid ID" }
        return clean
    }

    fun getBoxPaths(boxId: String): BoxPaths {
        val safeId = sanitizeId(boxId)
        val boxRoot = File(boxesDir, safeId)
        return BoxPaths(
            boxRoot = boxRoot,
            rmsDir = File(boxRoot, "rms"),
            configFile = File(boxRoot, "config.json"),
            snapshotFile = File(boxRoot, "snapshot.png")
        )
    }

    fun createBoxSandbox(boxId: String): BoxPaths {
        val paths = getBoxPaths(boxId)
        paths.boxRoot.mkdirs()
        paths.rmsDir.mkdirs()
        if (!paths.configFile.exists()) {
            paths.configFile.writeText(BoxConfig().toJson())
        }
        return paths
    }

    fun getRmsDir(boxId: String): File {
        val rmsDir = getBoxPaths(boxId).rmsDir
        if (!rmsDir.exists()) {
            rmsDir.mkdirs()
        }
        return rmsDir
    }

    fun deleteBoxSandbox(boxId: String): Boolean {
        val paths = getBoxPaths(boxId)
        return if (paths.boxRoot.exists()) {
            paths.boxRoot.deleteRecursively()
        } else {
            true
        }
    }

    fun saveConfig(boxId: String, config: BoxConfig) {
        val paths = createBoxSandbox(boxId)
        paths.configFile.writeText(config.toJson())
    }

    fun loadConfig(boxId: String): BoxConfig {
        val paths = getBoxPaths(boxId)
        return if (paths.configFile.exists()) {
            BoxConfig.fromJson(paths.configFile.readText())
        } else {
            BoxConfig()
        }
    }

    /**
     * Imports a .jar file into app-private storage after verifying ZIP/JAR magic bytes (PK\x03\x04).
     * Cleans up partial/invalid files on failure.
     */
    fun importJarFile(gameId: String, inputStream: InputStream): File {
        val safeGameId = sanitizeId(gameId)
        val targetFile = File(gamesDir, "$safeGameId.jar")
        val tempFile = File(gamesDir, "$safeGameId.jar.tmp")

        try {
            BufferedInputStream(inputStream).use { bis ->
                bis.mark(4)
                val header = ByteArray(4)
                val bytesRead = bis.read(header)
                val isValidZip = bytesRead == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte()

                if (!isValidZip) {
                    throw IllegalArgumentException("Invalid JAR/ZIP file header")
                }

                bis.reset()
                FileOutputStream(tempFile).use { fos ->
                    bis.copyTo(fos)
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        } catch (e: Exception) {
            tempFile.delete()
            if (targetFile.exists() && e is IllegalArgumentException) {
                targetFile.delete()
            }
            throw e
        }
    }

    fun importJadFile(gameId: String, inputStream: InputStream): File {
        val safeGameId = sanitizeId(gameId)
        val targetFile = File(gamesDir, "$safeGameId.jad")
        targetFile.outputStream().use { fos ->
            inputStream.copyTo(fos)
        }
        return targetFile
    }
}
