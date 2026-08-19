package com.kamal.smsfinance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferGroupIdRemapperTest {

    @Test
    fun mapping_is_unique_and_avoids_groups_already_in_destination() {
        val mapping = TransferGroupIdRemapper.createMapping(
            oldGroupIds = listOf(10L, 20L, 10L),
            occupiedGroupIds = setOf(1_000L, 1_002L),
            firstCandidate = 1_000L
        )

        assertEquals(setOf(10L, 20L), mapping.keys)
        assertEquals(1_001L, mapping[10L])
        assertEquals(1_003L, mapping[20L])
        assertEquals(mapping.values.size, mapping.values.toSet().size)
        assertTrue(mapping.values.none { it in setOf(1_000L, 1_002L) })
        assertNotEquals(mapping[10L], mapping[20L])
    }

    @Test
    fun non_positive_backup_group_ids_are_not_restored_as_relationships() {
        val mapping = TransferGroupIdRemapper.createMapping(
            oldGroupIds = listOf(0L, -4L, 12L),
            occupiedGroupIds = emptySet(),
            firstCandidate = 50L
        )

        assertEquals(mapOf(12L to 50L), mapping)
    }
}
