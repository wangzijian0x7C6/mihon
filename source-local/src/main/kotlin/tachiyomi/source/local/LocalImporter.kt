package tachiyomi.source.local

import com.hippo.unifile.UniFile

/**
 * Copies EPUB/CBZ files into a local manga directory, adding new chapters and
 * updating matching files in place.
 */
internal object LocalImporter {

    fun sanitizeFileName(name: String): String {
        return name
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim('.')
    }

    fun isSupportedImport(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in IMPORT_EXTENSIONS
    }

    /**
     * Skips a file whose source and destination are the same, and updates an
     * existing file through a temporary sibling first so a failed copy never
     * corrupts the original chapter.
     */
    fun importInto(mangaDirectory: UniFile, files: List<Pair<UniFile, String>>): ImportResult {
        var imported = 0
        var updated = 0
        var skipped = 0

        files.forEach { (source, sourceName) ->
            val targetName = sanitizeFileName(sourceName)
            if (targetName.isBlank() || !isSupportedImport(targetName)) {
                skipped++
                return@forEach
            }

            val existingFile = mangaDirectory.findFile(targetName)
            if (existingFile != null && existingFile.uri.toString() == source.uri.toString()) {
                // The user selected a directory that already contains this
                // manga's files; copying a file onto itself would truncate it.
                skipped++
                return@forEach
            }

            val target = existingFile ?: mangaDirectory.createFile(targetName)
                ?: error("Unable to create imported chapter file")
            try {
                if (existingFile == null) {
                    source.openInputStream().use { input ->
                        target.openOutputStream().use(input::copyTo)
                    }
                    imported++
                } else {
                    updateInPlace(mangaDirectory, source, target, targetName)
                    updated++
                }
            } catch (e: Throwable) {
                if (existingFile == null) target.delete()
                throw e
            }
        }

        return ImportResult(imported = imported, updated = updated, skipped = skipped, manga = 1)
    }

    private fun updateInPlace(
        mangaDirectory: UniFile,
        source: UniFile,
        target: UniFile,
        targetName: String,
    ) {
        val backupName = "$targetName.bak"
        val tempName = "$targetName.importing"
        val backup = mangaDirectory.findFile(backupName)
        val temp = mangaDirectory.findFile(tempName) ?: mangaDirectory.createFile(tempName)
            ?: error("Unable to create temporary chapter file")
        try {
            source.openInputStream().use { input ->
                temp.openOutputStream().use(input::copyTo)
            }
            // Back the old chapter up so a failed rename can be rolled back
            // instead of leaving a truncated file behind.
            if (!target.renameTo(backupName) || (backup != null && !backup.delete())) {
                error("Unable to back up existing chapter file")
            }
            if (!temp.renameTo(targetName)) {
                mangaDirectory.findFile(backupName)?.renameTo(targetName)
                error("Unable to replace existing chapter file")
            }
            mangaDirectory.findFile(backupName)?.delete()
        } finally {
            // After a successful rename the temp path no longer exists, so
            // this only cleans up leftovers from failed attempts.
            temp.takeIf { it.exists() }?.delete()
        }
    }

    private val IMPORT_EXTENSIONS = setOf("epub", "cbz")
}
