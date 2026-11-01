package io.github.alstarknight.slidex

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * An exported config is a zip holding `config.json` and, under `backgrounds/`, the pictures its pages use, so the
 * pages keep their backgrounds on another phone. Plain JSON files, as exported before, still import.
 */

private const val CONFIG_ENTRY = "config.json"
private const val BACKGROUNDS_DIR = "backgrounds/"
private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)

/** Starts the names of exported files, so they are easy to tell apart; [importedConfigName] drops it again. */
private const val EXPORT_PREFIX = "SlideX-"

/** File name offered when exporting the config called [configName]. */
fun exportFileName(configName: String): String = "$EXPORT_PREFIX$configName.zip"

/** Name for a config imported from a file called [fileName]: the file's name without the extension or prefix. */
fun importedConfigName(fileName: String): String? =
    fileName.substringBeforeLast('.').removePrefix(EXPORT_PREFIX).trim().takeIf { it.isNotEmpty() }

/** Writes [config] as a zip to [out], with the pictures [pictureFile] finds by name (null when one is missing). */
fun writeConfigArchive(out: OutputStream, config: PanelConfig, pictureFile: (String) -> File?) {
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(CONFIG_ENTRY))
        zip.write(config.toJson().encodeToByteArray())
        zip.closeEntry()
        for (name in config.backgrounds()) {
            val file = pictureFile(name) ?: continue
            zip.putNextEntry(ZipEntry(BACKGROUNDS_DIR + name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/**
 * Reads a config written by [writeConfigArchive], or a plain JSON config. Each picture in it goes to [savePicture]
 * with its name and contents, which it must not close. Throws [IllegalArgumentException] when there is no valid
 * config in it.
 */
fun readConfigArchive(input: InputStream, savePicture: (name: String, data: InputStream) -> Unit): PanelConfig {
    val buffered = BufferedInputStream(input)
    buffered.mark(ZIP_MAGIC.size)
    val head = ByteArray(ZIP_MAGIC.size).also { buffered.read(it) }
    buffered.reset()
    if (!head.contentEquals(ZIP_MAGIC)) return parseConfig(buffered.readBytes().decodeToString())
    var config: PanelConfig? = null
    val zip = ZipInputStream(buffered)
    generateSequence { zip.nextEntry }.forEach { entry ->
        if (entry.name == CONFIG_ENTRY) {
            config = parseConfig(zip.readBytes().decodeToString())
        } else {
            backgroundEntryName(entry.name)?.let { savePicture(it, zip) }
        }
    }
    return config ?: throw IllegalArgumentException("No $CONFIG_ENTRY in the file")
}

/** The picture's name for a zip entry `backgrounds/<name>`; null for other entries and names that reach elsewhere. */
fun backgroundEntryName(entry: String): String? {
    if (!entry.startsWith(BACKGROUNDS_DIR)) return null
    val name = entry.removePrefix(BACKGROUNDS_DIR)
    return name.takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != "." && it != ".." }
}
