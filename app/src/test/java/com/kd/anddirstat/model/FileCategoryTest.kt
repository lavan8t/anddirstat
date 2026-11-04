package com.kd.anddirstat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoryTest {

    @Test
    fun fromExtension_mapsKnownExtensionsToExpectedCategories() {
        assertEquals(FileCategory.VIDEO, FileCategory.fromExtension("mp4"))
        assertEquals(FileCategory.VIDEO, FileCategory.fromExtension(".MKV"))
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("jpg"))
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension(".PNG"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension("mp3"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension(".FLAC"))
        assertEquals(FileCategory.DOCUMENT, FileCategory.fromExtension("pdf"))
        assertEquals(FileCategory.DOCUMENT, FileCategory.fromExtension(".DOCX"))
        assertEquals(FileCategory.APP, FileCategory.fromExtension("apk"))
        assertEquals(FileCategory.APP, FileCategory.fromExtension(".XAPK"))
        assertEquals(FileCategory.ARCHIVE, FileCategory.fromExtension("zip"))
        assertEquals(FileCategory.ARCHIVE, FileCategory.fromExtension(".7Z"))
        assertEquals(FileCategory.TRASHED, FileCategory.fromExtension("[trashed]"))
        assertEquals(FileCategory.OTHER, FileCategory.fromExtension("unknown_extension_xyz"))
    }

    @Test
    fun categoryExtensions_areNormalizedWithoutLeadingDots() {
        for (category in FileCategory.entries) {
            for (ext in category.extensions) {
                if (ext != "[trashed]" && ext != "[recycle bin]") {
                    assertTrue("Extension $ext should not start with dot", !ext.startsWith("."))
                    assertEquals("Extension $ext should be lowercase", ext.lowercase(), ext)
                }
            }
        }
    }

    @Test
    fun statCategory_matchesExpectedLabels() {
        assertEquals("Video", FileCategory.VIDEO.statCategory)
        assertEquals("Image", FileCategory.IMAGE.statCategory)
        assertEquals("Audio", FileCategory.AUDIO.statCategory)
        assertEquals("Document", FileCategory.DOCUMENT.statCategory)
        assertEquals("App Package", FileCategory.APP.statCategory)
        assertEquals("Archive", FileCategory.ARCHIVE.statCategory)
        assertEquals("Recycle Bin", FileCategory.TRASHED.statCategory)
        assertEquals("Other", FileCategory.OTHER.statCategory)
    }
}
