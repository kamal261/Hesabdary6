package com.kamal.smsfinance.data

/**
 * Allocates fresh group ids for a backup restore. Group ids are semantic references rather than
 * Room foreign keys, so they need an explicit collision-free mapping of their own.
 */
object TransferGroupIdRemapper {
    fun createMapping(
        oldGroupIds: Collection<Long>,
        occupiedGroupIds: Set<Long>,
        firstCandidate: Long
    ): Map<Long, Long> {
        val result = linkedMapOf<Long, Long>()
        val used = occupiedGroupIds.toMutableSet()
        var candidate = firstCandidate.coerceAtLeast(1L)
        oldGroupIds.filter { it > 0L }.distinct().forEach { oldId ->
            while (candidate in used || candidate in result.values) candidate++
            result[oldId] = candidate
            used += candidate
            candidate++
        }
        return result
    }
}
