package com.kd.anddirstat.scanner

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object TreeCacheManager {
    private const val CACHE_FILE_NAME = "storage_tree_cache.bin"
    private const val MAGIC_HEADER = 0x41445354 // "ADST"
    private const val CACHE_VERSION = 1

    // Default cache expiry: 2 hours
    const val DEFAULT_CACHE_EXPIRY_MS = 2 * 60 * 60 * 1000L

    // Smallest on-disk footprint of one child node: 2-byte UTF length + 1 char, 1 bool, 8 long, 4 int.
    private const val MIN_BYTES_PER_NODE = 17

    // Absolute ceiling on children in one node, so a corrupt count cannot ask for a huge array.
    private const val MAX_CHILDREN_PER_NODE = 300_000

    suspend fun saveTree(context: Context, rootNode: CompactNode) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")

            DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 256 * 1024)).use { out ->
                out.writeInt(MAGIC_HEADER)
                out.writeInt(CACHE_VERSION)
                out.writeLong(System.currentTimeMillis())
                writeNode(out, rootNode)
                out.flush()
            }

            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
        } catch (_: Exception) {
            try {
                File(context.filesDir, "$CACHE_FILE_NAME.tmp").delete()
            } catch (_: Exception) {}
        }
    }

    suspend fun loadTree(
        context: Context,
        maxAgeMs: Long = DEFAULT_CACHE_EXPIRY_MS
    ): CompactNode? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists() || !file.canRead() || file.length() < 16) return@withContext null

        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 256 * 1024)).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER) {
                    file.delete()
                    return@withContext null
                }

                val version = input.readInt()
                if (version != CACHE_VERSION) {
                    file.delete()
                    return@withContext null
                }

                val timestamp = input.readLong()
                val age = System.currentTimeMillis() - timestamp
                if (age > maxAgeMs || age < 0) {
                    file.delete()
                    return@withContext null
                }

                readNode(input)
            }
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) {}
            null
        }
    }

    private fun writeNode(out: DataOutputStream, node: CompactNode) {
        out.writeUTF(node.name)
        out.writeBoolean(node.isDirectory)
        out.writeLong(node.size)
        val children = node.children
        if (children != null && children.isNotEmpty()) {
            out.writeInt(children.size)
            for (child in children) {
                writeNode(out, child)
            }
        } else {
            out.writeInt(-1)
        }
    }

    private fun readNode(input: DataInputStream): CompactNode {
        val name = input.readUTF()
        val isDirectory = input.readBoolean()
        val size = input.readLong()
        val count = input.readInt()
        val children = if (count > 0) {
            // A count is attacker-free but still arbitrary file content: reject implausible values
            // before allocating, so a corrupt cache is discarded instead of ballooning the heap.
            if (count > MAX_CHILDREN_PER_NODE) {
                throw IOException("implausible child count: $count")
            }
            val remaining = input.available().toLong()
            if (remaining > 0L && count.toLong() * MIN_BYTES_PER_NODE > remaining) {
                throw IOException("child count $count exceeds remaining cache bytes $remaining")
            }
            Array(count) { readNode(input) }
        } else null

        return CompactNode(
            name = name,
            isDirectory = isDirectory,
            size = size,
            children = children
        )
    }

    fun clearCache(context: Context) {
        try {
            File(context.filesDir, CACHE_FILE_NAME).delete()
            File(context.filesDir, "$CACHE_FILE_NAME.tmp").delete()
        } catch (_: Exception) {}
    }
}
