package tachiyomi.source.local

import android.net.Uri
import com.hippo.unifile.UniFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LocalImporterTest {

    private val written = mutableMapOf<String, ByteArrayOutputStream>()

    private fun testUri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        return uri
    }

    private fun writableFile(fileName: String, fileUri: String = "content://test/$fileName"): UniFile {
        val output = ByteArrayOutputStream()
        written[fileName] = output
        return mockk {
            every { this@mockk.uri } returns testUri(fileUri)
            every { exists() } returns true
            every { openOutputStream() } returns output
            every { delete() } returns true
        }
    }

    private fun sourceFile(content: String, fileUri: String): UniFile = mockk {
        every { this@mockk.uri } returns testUri(fileUri)
        every { openInputStream() } returns ByteArrayInputStream(content.toByteArray())
    }

    @Test
    fun `imports new files as chapters`() {
        val mangaDirectory = mockk<UniFile> {
            every { findFile("chapter1.epub") } returns null
            every { createFile("chapter1.epub") } returns writableFile("chapter1.epub")
        }

        val result = LocalImporter.importInto(
            mangaDirectory,
            listOf(sourceFile("a", "content://selected/1") to "chapter1.epub"),
        )

        result shouldBe ImportResult(imported = 1, manga = 1)
        written["chapter1.epub"]!!.toByteArray() shouldBe "a".toByteArray()
    }

    @Test
    fun `updates existing chapter in place without creating a duplicate`() {
        val existing = writableFile("chapter1.epub", fileUri = "content://lib/old")
        val temp = writableFile("chapter1.epub.importing")
        val mangaDirectory = mockk<UniFile> {
            every { findFile("chapter1.epub") } returns existing
            every { findFile("chapter1.epub.importing") } returns null
            every { createFile("chapter1.epub.importing") } returns temp
            every { findFile("chapter1.epub.bak") } returns null
        }
        every { existing.renameTo("chapter1.epub.bak") } returns true
        every { temp.renameTo("chapter1.epub") } returns true

        val result = LocalImporter.importInto(
            mangaDirectory,
            listOf(sourceFile("new", "content://selected/1") to "chapter1.epub"),
        )

        result shouldBe ImportResult(updated = 1, manga = 1)
        verify(exactly = 1) { existing.renameTo("chapter1.epub.bak") }
        verify(exactly = 1) { temp.renameTo("chapter1.epub") }
    }

    @Test
    fun `rolls back the backup when replacing the chapter fails`() {
        val existing = writableFile("chapter1.epub", fileUri = "content://lib/old")
        val temp = writableFile("chapter1.epub.importing")
        val backup = writableFile("chapter1.epub.bak")
        val mangaDirectory = mockk<UniFile> {
            every { findFile("chapter1.epub") } returns existing
            every { findFile("chapter1.epub.importing") } returns null
            every { createFile("chapter1.epub.importing") } returns temp
            every { findFile("chapter1.epub.bak") } returnsMany listOf(null, backup)
        }
        every { existing.renameTo("chapter1.epub.bak") } returns true
        every { temp.renameTo("chapter1.epub") } returns false
        every { backup.renameTo("chapter1.epub") } returns true

        shouldThrow<IllegalStateException> {
            LocalImporter.importInto(
                mangaDirectory,
                listOf(sourceFile("new", "content://selected/1") to "chapter1.epub"),
            )
        }

        // The old chapter must be restored from the backup.
        verify(exactly = 1) { backup.renameTo("chapter1.epub") }
    }

    @Test
    fun `skips a file whose source equals the existing target`() {
        val existing = writableFile("chapter1.epub", fileUri = "content://selected/1")
        val selected = sourceFile("self", "content://selected/1")
        val mangaDirectory = mockk<UniFile> {
            every { findFile("chapter1.epub") } returns existing
        }

        val result = LocalImporter.importInto(mangaDirectory, listOf(selected to "chapter1.epub"))

        // Copying a file onto itself would truncate it, so it is skipped.
        result shouldBe ImportResult(skipped = 1, manga = 1)
        written["chapter1.epub"]!!.size() shouldBe 0
    }

    @Test
    fun `skips unsupported extensions`() {
        val mangaDirectory = mockk<UniFile>()

        val result = LocalImporter.importInto(
            mangaDirectory,
            listOf(sourceFile("a", "content://selected/1") to "chapter1.pdf"),
        )

        result shouldBe ImportResult(skipped = 1, manga = 1)
        verify { mangaDirectory wasNot Called }
    }

    @Test
    fun `sanitizes invalid file name characters`() {
        LocalImporter.sanitizeFileName("  a/b:c*d?e\"f<g>h|i.epub  ") shouldBe
            "a_b_c_d_e_f_g_h_i.epub"
        LocalImporter.sanitizeFileName(".hidden.epub") shouldBe "hidden.epub"
        LocalImporter.sanitizeFileName("  ") shouldBe ""
    }

    @Test
    fun `recognizes supported extensions case-insensitively`() {
        LocalImporter.isSupportedImport("Chapter.EPUB") shouldBe true
        LocalImporter.isSupportedImport("chapter.Cbz") shouldBe true
        LocalImporter.isSupportedImport("chapter.pdf") shouldBe false
        LocalImporter.isSupportedImport("chapter") shouldBe false
    }
}
