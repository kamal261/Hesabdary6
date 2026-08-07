// SmsFinance file version: 1 — grafted from Hesabdary6/v1's DedupEngine, scoped down to what this
// project actually needs: dedup for the Unidentified-SMS queue. Recognized-transaction dedup
// already has a solid mechanism in TransactionRepository (existsExact + existsSimilar with
// accountTail cross-check) and is left untouched.
package com.kamal.smsfinance.sms

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents the same ambiguous SMS from being inserted into the Unidentified-review queue twice.
 *
 * Why not just (sender, body, timestamp) equality (the old approach)?
 * Android's SMS broadcast can redeliver the same message with a timestamp that differs by a
 * few milliseconds -- an exact match on `timestamp` silently fails to catch that, and the user
 * ends up reviewing the same message twice. A hash of (sender, body) plus a tolerance window
 * around `timestamp` catches this while still treating two *different* real messages (e.g. two
 * separate OTPs a few seconds apart) as distinct.
 *
 * Uses SHA-256 (truncated to 32 bits) instead of String.hashCode() -- hashCode()'s 32-bit space
 * has a much higher practical collision rate for this use case, and a collision here would mean
 * a real, distinct SMS gets silently treated as a duplicate and never shown to the user, which is
 * exactly the "silent loss" this whole review-queue mechanism exists to prevent.
 */
object DedupEngine {

    /** Tolerance window: two occurrences of the same (sender, body) within this many ms count as one delivery. */
    private const val DEDUP_WINDOW_MILLIS: Long = 5 * 60 * 1000 // 5 minutes

    /** Hard cap so the in-memory map can't grow unbounded during a long-lived process. */
    private const val MAX_ENTRIES = 5_000

    private val seen = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * @return true if this (sender, body) was already seen within the tolerance window of
     * [timestamp] -- caller should skip inserting it. Records the timestamp as a side effect
     * only when it's NOT a duplicate, so the window slides with each genuinely new occurrence.
     */
    fun isDuplicate(sender: String, body: String, timestamp: Long): Boolean {
        val key = hash(sender, body)
        val timestamps = seen.getOrPut(key) { mutableListOf() }

        val duplicate = synchronized(timestamps) {
            val isDup = timestamps.any { Math.abs(it - timestamp) <= DEDUP_WINDOW_MILLIS }
            if (!isDup) {
                timestamps.add(timestamp)
                if (timestamps.size > 20) timestamps.removeAt(0) // per-key cap, oldest first
            }
            isDup
        }

        if (seen.size > MAX_ENTRIES) prune()
        return duplicate
    }

    private fun hash(sender: String, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$sender|$body".toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) } // 32-bit hex, collision-resistant enough
    }

    /** Drops the oldest entries once [MAX_ENTRIES] is exceeded. Simple, not called on every insert. */
    private fun prune() {
        val toDrop = seen.size - MAX_ENTRIES
        if (toDrop <= 0) return
        seen.entries
            .sortedBy { it.value.minOrNull() ?: Long.MAX_VALUE }
            .take(toDrop)
            .forEach { seen.remove(it.key) }
    }

    /** For tests. */
    fun clear() = seen.clear()
}
