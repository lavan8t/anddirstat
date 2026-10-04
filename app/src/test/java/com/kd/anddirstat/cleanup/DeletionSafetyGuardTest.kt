package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionSafetyGuardTest {

    @Test
    fun testRootPathsAreBlocked() {
        val rootNode = CompactNode(name = "storage", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/storage/emulated/0"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/storage/emulated/0/"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(rootNode, "/sdcard"))
    }

    @Test
    fun testSystemDirectoriesAreBlocked() {
        val sysNode = CompactNode(name = "system", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/system"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/system/bin/sh"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/vendor/lib"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/data/system"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysNode, "/data/dalvik-cache"))
    }

    @Test
    fun testAndroidFrameworkRootContainersAreBlocked() {
        val androidNode = CompactNode(name = "Android", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "/storage/emulated/0/Android"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "/storage/emulated/0/Android/data"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "/storage/emulated/0/Android/obb"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(androidNode, "Android"))
    }

    @Test
    fun testSyntheticContainersAreBlocked() {
        val freeSpace = CompactNode(name = "[Free Space]", isDirectory = false)
        val sysOs = CompactNode(name = "[System & OS]", isDirectory = false)
        val apps = CompactNode(name = "Apps & System Packages", isDirectory = true)
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(freeSpace, "[Free Space]"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(sysOs, "[System & OS]"))
        assertFalse(DeletionSafetyGuard.isDeletionAllowed(apps, "Apps & System Packages"))
    }

    @Test
    fun testUserFilesAndFoldersAreAllowed() {
        val userFile = CompactNode(name = "video.mp4", isDirectory = false)
        val userFolder = CompactNode(name = "Camera", isDirectory = true)
        assertTrue(DeletionSafetyGuard.isDeletionAllowed(userFile, "/storage/emulated/0/Download/video.mp4"))
        assertTrue(DeletionSafetyGuard.isDeletionAllowed(userFolder, "/storage/emulated/0/DCIM/Camera"))
        assertTrue(DeletionSafetyGuard.isDeletionAllowed(userFile, "Download/video.mp4"))
    }
}
