package com.kamal.smsfinance.util

/** Pure local policy for deciding whether the user should see a backup reminder. */
object BackupReminderPolicy {
    const val DEFAULT_AFTER_MILLIS = 30L * 24L * 60L * 60L * 1000L

    fun isDue(
        lastBackupTimestamp: Long?,
        snoozeUntil: Long,
        now: Long,
        thresholdMillis: Long = DEFAULT_AFTER_MILLIS
    ): Boolean {
        if (now < snoozeUntil) return false
        return lastBackupTimestamp == null || now - lastBackupTimestamp >= thresholdMillis
    }
}
