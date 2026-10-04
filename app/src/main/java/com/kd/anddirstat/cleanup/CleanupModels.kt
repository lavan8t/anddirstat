package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode

data class StagedQueueItem(
    val node: CompactNode,
    val path: String,
    val reclaimableBytes: Long,
    val isApp: Boolean = false,
    val pkgName: String? = null
)

object CleanupQueueHelper {
    fun calculateTotalReclaimable(items: Collection<StagedQueueItem>): Long {
        return items.sumOf { it.reclaimableBytes }
    }

    fun toggleItem(
        queue: Map<CompactNode, StagedQueueItem>,
        item: StagedQueueItem
    ): Map<CompactNode, StagedQueueItem> {
        return if (queue.containsKey(item.node)) {
            queue - item.node
        } else {
            queue + (item.node to item)
        }
    }
}
