package com.kd.anddirstat.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeScreenshotSource(
    private val items: List<ScreenshotItem> = emptyList()
) : ScreenshotSource {
    override suspend fun load(keptPaths: Set<String>): List<ScreenshotItem> =
        items.filter { it.path !in keptPaths }
}

private class FakeKeptStore : KeptScreenshotsStore {
    private var paths: Set<String> = emptySet()
    override fun load(): Set<String> = paths
    override fun save(paths: Set<String>) {
        this.paths = paths
    }
}

private class FakeStars : StarRepository {
    var starred: Set<String> = emptySet()
    override fun starredPaths(): Set<String> = starred
    override fun isStarred(path: String): Boolean = path in starred
    override fun toggle(path: String) {
        starred = if (path in starred) starred - path else starred + path
    }
}

private class FakeHaptics : CleanerHaptics {
    override fun perform(kind: HapticKind) {}
}

private class FakeNotifier : CleanerNotifier {
    val messages = mutableListOf<String>()
    override fun notify(message: String) {
        messages.add(message)
    }
}

private class FakeDeleter : CleanerDeleter {
    val batches = mutableListOf<Set<String>>()
    var lastIsPermanent: Boolean? = null

    override suspend fun deleteFiles(
        paths: Set<String>,
        isPermanent: Boolean,
        actionName: String,
        onProgress: (Int, Int, String) -> Unit
    ): Int {
        batches.add(paths)
        lastIsPermanent = isPermanent
        return paths.size
    }

    override suspend fun deleteFile(path: String, isPermanent: Boolean): DeletionResult =
        DeletionResult(true)
}

private class FakeRestorer : TrashRestorer {
    override suspend fun restore(targets: List<Pair<String, String>>, onProgress: (Int, Int, String) -> Unit): Int =
        targets.size
}

class CleanerViewModelTest {

    private fun screenshot(path: String, size: Long): ScreenshotItem =
        ScreenshotItem(File(path), File(path).name, size, path)

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000L
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not met in time", predicate())
    }

    @Test
    fun duplicateGroupingFromInMemoryList() {
        val vm = DuplicatesViewModel(
            source = object : DuplicateSource {
                override fun load(): List<DuplicateFileItem> = emptyList()
            },
            deleter = FakeDeleter(),
            haptics = FakeHaptics()
        )
        val a = tempFileWithSize("photo.jpg", 100)
        val b = tempFileWithSize("photo.jpg", 100)
        val other = tempFileWithSize("other.png", 100)
        val small = tempFileWithSize("small.txt", 5)

        val groups = vm.groupFiles(
            listOf(a, b, other, small).map {
                DuplicateFileItem(it, it.absolutePath, 0L)
            }
        )

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("photo.jpg", group.fileName)
        assertEquals(100L, group.fileSize)
        assertEquals(
            setOf(a.absolutePath, b.absolutePath),
            group.files.map { it.path }.toSet()
        )
    }

    @Test
    fun selectAllAndInvertSelection() {
        val base = CleanerSelectionState(sortOrder = ScreenshotSort.NEWEST)
        val all = base.selectAll(listOf("/a", "/b", "/c"))
        assertEquals(setOf("/a", "/b", "/c"), all.selectedPaths)

        val inverted = all.invert(listOf("/a", "/b", "/c", "/d"))
        assertEquals(setOf("/d"), inverted.selectedPaths)

        val toggled = all.toggle("/a")
        assertEquals(setOf("/b", "/c"), toggled.selectedPaths)
    }

    @Test
    fun undoRestoresExactRemovedItemAndIndex() {
        val a = screenshot("/a.png", 10L)
        val b = screenshot("/b.png", 20L)
        val c = screenshot("/c.png", 30L)
        val vm = ScreenshotsViewModel(
            source = FakeScreenshotSource(listOf(a, b, c)),
            keptStore = FakeKeptStore(),
            stars = FakeStars(),
            haptics = FakeHaptics(),
            notifier = FakeNotifier(),
            deleter = FakeDeleter(),
            restorer = FakeRestorer()
        )

        vm.onScan()
        waitFor { vm.uiState.value.items.size == 3 }
        // Keep the current item (index 0); undo must restore that exact item at its original index.
        vm.onKeep()
        assertEquals(listOf(b, c), vm.uiState.value.items)
        assertTrue(vm.uiState.value.canUndo)

        vm.onUndo()
        assertEquals(listOf(a, b, c), vm.uiState.value.items)
        assertEquals(0, vm.uiState.value.currentIndex)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun sortOrderIsReflectedInState() {
        val a = screenshot("/a.png", 10L)
        val b = screenshot("/b.png", 20L)
        val vm = ScreenshotsViewModel(
            source = FakeScreenshotSource(listOf(a, b)),
            keptStore = FakeKeptStore(),
            stars = FakeStars(),
            haptics = FakeHaptics(),
            notifier = FakeNotifier(),
            deleter = FakeDeleter(),
            restorer = FakeRestorer()
        )

        vm.onScan()
        waitFor { vm.uiState.value.items.size == 2 }
        vm.onSortChanged(ScreenshotSort.LARGEST)
        assertEquals(ScreenshotSort.LARGEST, vm.uiState.value.selection.sortOrder)
        assertEquals(listOf(b.path, a.path), vm.uiState.value.items.map { it.path })
    }

    @Test
    fun commitCallsGuardedDeleterOnceWithSelectedSet() {
        val a = screenshot("/a.png", 10L)
        val b = screenshot("/b.png", 20L)
        val deleter = FakeDeleter()
        val vm = ScreenshotsViewModel(
            source = FakeScreenshotSource(listOf(a, b)),
            keptStore = FakeKeptStore(),
            stars = FakeStars(),
            haptics = FakeHaptics(),
            notifier = FakeNotifier(),
            deleter = deleter,
            restorer = FakeRestorer()
        )

        vm.onScan()
        waitFor { vm.uiState.value.items.size == 2 }
        vm.onToggleGrid()
        vm.onToggleSelected("/a.png")
        vm.onToggleSelected("/b.png")

        vm.onBatchDelete()

        waitFor { deleter.batches.size == 1 && deleter.lastIsPermanent != null }
        assertEquals(1, deleter.batches.size)
        assertEquals(setOf("/a.png", "/b.png"), deleter.batches.first())
        assertEquals(false, deleter.lastIsPermanent)
    }

    private fun tempFileWithSize(name: String, size: Int): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "cleaner-test-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(ByteArray(size))
        file.deleteOnExit()
        dir.deleteOnExit()
        return file
    }
}
