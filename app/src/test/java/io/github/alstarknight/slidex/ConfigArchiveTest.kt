package io.github.alstarknight.slidex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ConfigArchiveTest {

    private val config = PanelConfig(
        pages = listOf(
            Page(listOf(NoteCard("n", "Note", "hi")), PageStyle(background = "a.jpg")),
            Page(style = PageStyle(background = "missing.png")),
        ),
    )

    @Test
    fun configAndPicturesComeBack() {
        val picture = File.createTempFile("background", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val out = ByteArrayOutputStream()
        writeConfigArchive(out, config) { name -> picture.takeIf { name == "a.jpg" } }

        val saved = mutableMapOf<String, ByteArray>()
        val read = readConfigArchive(ByteArrayInputStream(out.toByteArray())) { name, data -> saved[name] = data.readBytes() }

        assertEquals(config, read)
        assertEquals(setOf("a.jpg"), saved.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), saved["a.jpg"])
    }

    @Test
    fun plainJsonStillImports() {
        val read = readConfigArchive(ByteArrayInputStream(config.toJson().encodeToByteArray())) { _, _ -> error("no pictures") }
        assertEquals(config, read)
    }

    @Test
    fun picturesOutsideTheBackgroundsFolderAreSkipped() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("config.json"))
            zip.write(config.toJson().encodeToByteArray())
            zip.putNextEntry(ZipEntry("other/b.jpg"))
            zip.putNextEntry(ZipEntry("backgrounds/sub/c.jpg"))
        }
        val saved = mutableListOf<String>()
        readConfigArchive(ByteArrayInputStream(out.toByteArray())) { name, _ -> saved += name }
        assertEquals(emptyList<String>(), saved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zipWithoutConfigIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("backgrounds/a.jpg")) }
        readConfigArchive(ByteArrayInputStream(out.toByteArray())) { _, _ -> }
    }

    @Test
    fun entryNamesCannotReachOutside() {
        assertEquals("a.jpg", backgroundEntryName("backgrounds/a.jpg"))
        assertNull(backgroundEntryName("backgrounds/../config.json"))
        assertNull(backgroundEntryName("backgrounds/.."))
        assertNull(backgroundEntryName("backgrounds/"))
        assertNull(backgroundEntryName("config.json"))
    }

    @Test
    fun importedConfigIsNamedAfterTheFile() {
        assertEquals("工作", importedConfigName(exportFileName("工作")))
        assertEquals("my config", importedConfigName("my config.json"))
        assertNull(importedConfigName("SlideX-.zip"))
    }
}
