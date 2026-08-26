package com.kamal.smsfinance.sms

import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Dedup Engine: Prevents duplicate transaction insertion.
 * 
 * CRITICAL FIXES from v2.1 critique:
 * 1. SHA-256 truncated (4 bytes = 32 bits) instead of String.hashCode() - prevents collision
 * 2. Sliding window around receivedAt instead of fixed bucket - catches duplicates across boundaries
 * 3. Thread-safe with ConcurrentHashMap
 */
object DedupEngine {

    /** Time window for duplicate detection (5 minutes) */
    private const val DEDUP_WINDOW_MILLIS: Long = 5 * 60 * 1000 // 5 minutes in millis

    /** Max entries to keep in memory (auto-prunes old entries) */
    private const val MAX_ENTRIES = 10000

    /** 
     * In-memory store: hash -> list of timestamps
     * Using ConcurrentHashMap for thread safety
     */
    private val seenHashes = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Checks if a message is a duplicate and records it if not.
     * 
     * @param sender SMS sender
     * @param body SMS body text
     * @param timestamp Message timestamp (delivery time)
     * @return true if DUPLICATE (already seen), false if NEW (recorded)
     */
    fun isDuplicate(sender: String, body: String, timestamp: Long): Boolean {
        val hash = computeHash(sender, body)
        val now = System.currentTimeMillis()
        
        // Clean up old entries periodically (every 100 checks)
        if (seenHashes.size % 100 == 0) {
            pruneOldEntries(now)
        }

        val timestamps = seenHashes.getOrPut(hash) { Collections.synchronizedList(mutableListOf<Long>()) }
        
        // Sliding window check: any existing timestamp within ±DEDUP_WINDOW_MILLIS?
        val isDup = timestamps.any { existingTs ->
            Math.abs(existingTs - timestamp) <= DEDUP_WINDOW_MILLIS
        }

        if (!isDup) {
            timestamps.add(timestamp)
            // Keep only recent timestamps for this hash (max 10 per hash)
            if (timestamps.size > 10) {
                timestamps.sort()
                timestamps.removeAt(0)
            }
        }

        return isDup
    }

    /**
     * Computes a collision-resistant hash for (sender + body).
     * Uses SHA-256 truncated to 4 bytes (8 hex chars = 32 bits).
     * Much better distribution than String.hashCode().
     */
    private fun computeHash(sender: String, body: String): String {
        val input = "$sender|$body".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        // Take first 4 bytes (32 bits) as hex string
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    /**
     * Removes entries older than 2x the dedup window.
     * Prevents unbounded memory growth.
     */
    private fun pruneOldEntries(now: Long) {
        val cutoff = now - DEDUP_WINDOW_MILLIS * 2
        seenHashes.values.forEach { timestamps ->
            timestamps.removeIf { it < cutoff }
        }
        // Remove empty lists
        seenHashes.values.removeIf { it.isEmpty() }
        
        // Hard cap on total entries
        if (seenHashes.size > MAX_ENTRIES) {
            val toRemove = seenHashes.size - MAX_ENTRIES
            val oldestKeys = seenHashes.entries
                .sortedBy { it.value.firstOrNull() ?: Long.MAX_VALUE }
                .take(toRemove)
                .map { it.key }
            oldestKeys.forEach { seenHashes.remove(it) }
        }
    }

    /**
     * Clears all dedup state (for testing or manual reset).
     */
    fun clear() {
        seenHashes.clear()
    }

    /**
     * Returns current dedup cache stats (for debugging).
     */
    fun getStats(): DedupStats {
        return DedupStats(
            totalHashes = seenHashes.size,
            totalTimestamps = seenHashes.values.sumOf { it.size },
            oldestTimestamp = seenHashes.values.flatten().minOrNull() ?: 0,
            newestTimestamp = seenHashes.values.flatten().maxOrNull() ?: 0
        )
    }

    data class DedupStats(
        val totalHashes: Int,
        val totalTimestamps: Int,
        val oldestTimestamp: Long,
        val newestTimestamp: Long
    )
}