package mihon.core.archive

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpubReaderTest {

    @Test
    fun `does not link the Android-incompatible Java List removeLast method`() {
        val bytecode = requireNotNull(
            EpubReader::class.java.getResourceAsStream("/mihon/core/archive/EpubReader.class"),
        ).use { it.readBytes().toString(Charsets.ISO_8859_1) }

        bytecode shouldNotContain "removeLast"
    }

    @Test
    fun `reads wrapped and direct image pages in spine order`() {
        val reader = epubReader(
            "META-INF/container.xml" to container("OPS/package.opf"),
            "OPS/package.opf" to packageDocument(
                manifest = """
                    <item id="cover" href="text/cover.xhtml" media-type="application/xhtml+xml"/>
                    <item id="page-2" href="images/page%2002.jpg#spread" media-type="image/jpeg"/>
                    <item id="page-3" href="graphics/page3.svg" media-type="image/svg+xml"/>
                    <item id="page-4" href="text/page4.xhtml" media-type="application/xhtml+xml"/>
                """,
                spine = """
                    <itemref idref="cover"/>
                    <itemref idref="page-2"/>
                    <itemref idref="page-3"/>
                    <itemref idref="page-4"/>
                """,
            ),
            "OPS/text/cover.xhtml" to """
                <html><body><img src="../images/cover.jpg?size=large"/></body></html>
            """,
            "OPS/graphics/page3.svg" to """
                <svg><image href="../images/page03.png"/></svg>
            """,
            "OPS/text/page4.xhtml" to """
                <html><body><object type="image/webp" data="../images/page04.webp"/></body></html>
            """,
        )

        reader.getImagesFromPages().shouldContainExactly(
            "OPS/images/cover.jpg",
            "OPS/images/page 02.jpg",
            "OPS/images/page03.png",
            "OPS/images/page04.webp",
        )
    }

    @Test
    fun `supports windows separators and legacy svg image links`() {
        val reader = epubReader(
            "META-INF\\container.xml" to container("OPS/package.opf"),
            "OPS\\package.opf" to packageDocument(
                manifest = """
                    <item id="page" href="text/page.xhtml" media-type="application/xhtml+xml"/>
                """,
                spine = "<itemref idref=\"page\"/>",
            ),
            "OPS\\text\\page.xhtml" to """
                <svg><image xlink:href="../images/page.jpg"/></svg>
            """,
        )

        reader.getPackageHref() shouldBe "OPS\\package.opf"
        reader.getImagesFromPages().shouldContainExactly("OPS\\images\\page.jpg")
    }

    @Test
    fun `falls back to the conventional package path when container metadata is unusable`() {
        val reader = epubReader(
            "META-INF/container.xml" to container(""),
            "OEBPS/content.opf" to packageDocument(
                manifest = "<item id=\"page\" href=\"page.jpg\" media-type=\"image/jpeg\"/>",
                spine = "<itemref idref=\"page\"/>",
            ),
        )

        reader.getPackageHref() shouldBe "OEBPS/content.opf"
        reader.getImagesFromPages().shouldContainExactly("OEBPS/page.jpg")
    }

    @Test
    fun `skips missing content documents and external images`() {
        val reader = epubReader(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to packageDocument(
                manifest = """
                    <item id="missing" href="missing.xhtml" media-type="application/xhtml+xml"/>
                    <item id="external" href="external.xhtml" media-type="application/xhtml+xml"/>
                """,
                spine = "<itemref idref=\"missing\"/><itemref idref=\"external\"/>",
            ),
            "external.xhtml" to """
                <html><body>
                    <img src="https://example.com/page.jpg"/>
                    <img src="//example.com/page.jpg"/>
                    <img src="#page"/>
                </body></html>
            """,
        )

        reader.getImagesFromPages() shouldBe emptyList()
    }

    @Test
    fun `reads title and epub 3 cover outside the spine`() {
        val reader = epubReader(
            "META-INF/container.xml" to container("OPS/package.opf"),
            "OPS/package.opf" to """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <metadata><dc:title>  My Comic  </dc:title></metadata>
                    <manifest>
                        <item id="cover" href="images/cover.webp" media-type="image/webp" properties="nav cover-image"/>
                        <item id="page" href="images/page.jpg" media-type="image/jpeg"/>
                    </manifest>
                    <spine><itemref idref="page"/></spine>
                </package>
            """.trimIndent(),
        )

        reader.getTitle() shouldBe "My Comic"
        reader.getCoverImage() shouldBe "OPS/images/cover.webp"
    }

    @Test
    fun `reads epub 2 cover page metadata`() {
        val reader = epubReader(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to """
                <package>
                    <metadata><meta name="cover" content="cover-page"/></metadata>
                    <manifest>
                        <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine/>
                </package>
            """.trimIndent(),
            "cover.xhtml" to "<html><body><img src=\"images/cover.jpg\"/></body></html>",
        )

        reader.getCoverImage() shouldBe "images/cover.jpg"
    }

    private fun epubReader(vararg entries: Pair<String, String>): EpubReader {
        val contents = entries.toMap()
        return EpubReader { path -> contents[path]?.byteInputStream() }
    }

    private fun container(packagePath: String) = """
        <?xml version="1.0"?>
        <container>
            <rootfiles><rootfile full-path="$packagePath"/></rootfiles>
        </container>
    """.trimIndent()

    private fun packageDocument(manifest: String, spine: String) = """
        <?xml version="1.0"?>
        <package>
            <manifest>$manifest</manifest>
            <spine>$spine</spine>
        </package>
    """.trimIndent()
}
