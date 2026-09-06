package mihon.core.archive

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Wrapper over ArchiveReader to load files in epub format.
 */
class EpubReader private constructor(
    private val inputStreamProvider: (String) -> InputStream?,
    private val closeAction: () -> Unit,
) : Closeable {

    constructor(reader: ArchiveReader) : this(reader::getInputStream, reader::close)

    internal constructor(inputStreamProvider: (String) -> InputStream?) : this(inputStreamProvider, {})

    /**
     * Path separator used by this epub.
     */
    private val pathSeparator = getPathSeparator()

    /**
     * Returns an input stream for reading the contents of the specified zip file entry.
     */
    fun getInputStream(entryName: String): InputStream? {
        return inputStreamProvider(entryName)
    }

    /**
     * Returns the path of all the images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val readingOrder = getReadingOrder(doc)
        return getImagesFromReadingOrder(readingOrder, ref)
    }

    /**
     * Returns the path to the package document.
     */
    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
            val path = metaDoc.getElementsByTag("rootfile")
                .first()
                ?.attr("full-path")
                ?.takeIf(String::isNotBlank)
            if (path != null) {
                return resolveZipPath("", path)
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    /**
     * Returns the package document where all the files are listed.
     */
    fun getPackageDocument(ref: String): Document {
        val stream = requireNotNull(getInputStream(ref)) { "EPUB package document not found: $ref" }
        return stream.use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    /**
     * Returns all the items in the epub's reading order.
     */
    private fun getReadingOrder(document: Document): List<ManifestItem> {
        val manifest = document.select("manifest > item")
            .mapNotNull { node ->
                val id = node.attr("id")
                val href = node.attr("href")
                if (id.isBlank() || href.isBlank()) {
                    null
                } else {
                    id to ManifestItem(href, node.attr("media-type"))
                }
            }
            .toMap()

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull(manifest::get)
    }

    /**
     * Returns all images from the epub in spine order. Fixed-layout EPUBs may
     * reference images directly from the spine instead of wrapping them in XHTML.
     */
    private fun getImagesFromReadingOrder(items: List<ManifestItem>, packageHref: String): List<String> {
        val basePath = getParentDirectory(packageHref)
        return items.flatMap { item ->
            val entryPath = resolveZipPath(basePath, item.href)
            when {
                item.isPage() -> getImagesFromPage(entryPath)
                item.isImage() -> listOf(entryPath)
                else -> emptyList()
            }
        }
    }

    private fun getImagesFromPage(entryPath: String): List<String> {
        val document = getInputStream(entryPath)?.use { Jsoup.parse(it, null, "") } ?: return emptyList()
        val imageBasePath = getParentDirectory(entryPath)

        return document.allElements.mapNotNull { element ->
            val reference = when (element.tagName()) {
                "img" -> element.attr("src")
                "image" -> element.attr("href").ifBlank { element.attr("xlink:href") }
                "object" -> element.attr("data").takeIf {
                    element.attr("type").startsWith("image/", ignoreCase = true)
                }
                else -> null
            }
            reference
                ?.toLocalReferenceOrNull()
                ?.let { resolveZipPath(imageBasePath, it) }
        }
    }

    /**
     * Returns the path separator used by the epub file.
     */
    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    /**
     * Resolves a zip path from base and relative components and a path separator.
     */
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        val cleanRelativePath = relativePath
            .substringBefore('#')
            .substringBefore('?')
            .decodeUrlPath()
            .replace('\\', '/')
        val cleanBasePath = basePath.replace('\\', '/')
        val combinedPath = if (cleanRelativePath.startsWith('/')) {
            cleanRelativePath
        } else {
            "$cleanBasePath/$cleanRelativePath"
        }

        val resolvedSegments = mutableListOf<String>()
        combinedPath.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (resolvedSegments.isNotEmpty()) resolvedSegments.removeLast()
                else -> resolvedSegments.add(segment)
            }
        }
        return resolvedSegments.joinToString(pathSeparator)
    }

    /**
     * Gets the parent directory of a path.
     */
    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) {
            path.substring(0, separatorIndex)
        } else {
            ""
        }
    }

    override fun close() = closeAction()

    private data class ManifestItem(
        val href: String,
        val mediaType: String,
    ) {
        fun isImage(): Boolean {
            return mediaType.startsWith("image/", ignoreCase = true) ||
                href.substringAfterLast('.', "").substringBefore('#').substringBefore('?').lowercase() in
                IMAGE_EXTENSIONS
        }

        fun isPage(): Boolean {
            return mediaType.lowercase() in PAGE_MEDIA_TYPES ||
                href.substringAfterLast('.', "")
                    .substringBefore('#')
                    .substringBefore('?')
                    .lowercase() in PAGE_EXTENSIONS
        }
    }

    private fun String.decodeUrlPath(): String {
        return try {
            URLDecoder.decode(replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            this
        }
    }

    private fun String.toLocalReferenceOrNull(): String? {
        val path = substringBefore('#').substringBefore('?')
        return path.takeIf { it.isNotBlank() && !it.startsWith("//") && !URI_SCHEME.matches(it) }
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "jxl", "png", "svg", "webp")
        val PAGE_EXTENSIONS = setOf("htm", "html", "svg", "xhtml", "xml")
        val PAGE_MEDIA_TYPES = setOf("application/xhtml+xml", "image/svg+xml", "text/html")
        val URI_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
    }
}
