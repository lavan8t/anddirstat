package com.kd.anddirstat.scanner

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreTreeBuilderTest {

    @Test
    fun buildTree_emptyEntries_returnsEmptyRoot() {
        val root = MediaStoreTreeBuilder.buildTree(
            rootPath = "/storage/emulated/0",
            entries = emptySequence()
        )
        assertEquals("/storage/emulated/0", root.name)
        assertTrue(root.isDirectory)
        assertEquals(0L, root.size)
        assertTrue(root.children.isNullOrEmpty())
    }

    @Test
    fun buildTree_singleFile_createsDirectoryHierarchyAndLeaf() {
        val entries = sequenceOf(
            MediaStoreEntry(
                path = "/storage/emulated/0/DCIM/photo.jpg",
                size = 5000L
            )
        )
        val root = MediaStoreTreeBuilder.buildTree(
            rootPath = "/storage/emulated/0",
            entries = entries
        )

        assertEquals(5000L, root.size)
        assertEquals(1, root.children?.size)

        val dcim = root.children?.first()
        assertNotNull(dcim)
        assertEquals("DCIM", dcim?.name)
        assertTrue(dcim!!.isDirectory)
        assertEquals(5000L, dcim.size)

        val file = dcim.children?.first()
        assertNotNull(file)
        assertEquals("photo.jpg", file?.name)
        assertEquals(false, file?.isDirectory)
        assertEquals(5000L, file?.size)
    }

    @Test
    fun buildTree_multipleNestedFiles_aggregatesSizesCorrectly() {
        val entries = sequenceOf(
            MediaStoreEntry("/storage/emulated/0/DCIM/Camera/IMG_1.jpg", 1000L),
            MediaStoreEntry("/storage/emulated/0/DCIM/Camera/IMG_2.jpg", 2000L),
            MediaStoreEntry("/storage/emulated/0/Download/report.pdf", 4000L),
            MediaStoreEntry("/storage/emulated/0/notes.txt", 500L)
        )

        val root = MediaStoreTreeBuilder.buildTree(
            rootPath = "/storage/emulated/0",
            entries = entries
        )

        assertEquals(7500L, root.size)
        assertEquals(3, root.children?.size) // DCIM, Download, notes.txt

        val dcim = root.children?.find { it.name == "DCIM" }
        assertNotNull(dcim)
        assertEquals(3000L, dcim?.size)

        val camera = dcim?.children?.find { it.name == "Camera" }
        assertNotNull(camera)
        assertEquals(3000L, camera?.size)
        assertEquals(2, camera?.children?.size)

        val download = root.children?.find { it.name == "Download" }
        assertNotNull(download)
        assertEquals(4000L, download?.size)

        val notes = root.children?.find { it.name == "notes.txt" }
        assertNotNull(notes)
        assertEquals(500L, notes?.size)
    }
}
