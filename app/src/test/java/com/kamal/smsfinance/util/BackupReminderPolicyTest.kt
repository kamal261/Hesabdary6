package com.kamal.smsfinance.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderPolicyTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun no_backup_is_due() {
        assertTrue(
            BackupReminderPolicy.isDue(
                lastBackupTimestamp = null,
                snoozeUntil = 0L,
                now = 100L
            )
        )
    }

    @Test
    fun recent_backup_is_not_due() {
        val now = 40L * day
        assertFalse(
            BackupReminderPolicy.isDue(
                lastBackupTimestamp = now - 2L * day,
                snoozeUntil = 0L,
                now = now
            )
        )
    }

    @Test
    fun old_backup_is_due() {
        val now = 40L * day
        assertTrue(
            BackupReminderPolicy.isDue(
                lastBackupTimestamp = now - 30L * day,
                snoozeUntil = 0L,
                now = now
            )
        )
    }

    @Test
    fun snooze_hides_reminder_until_expiry() {
        val now = 40L * day
        assertFalse(
            BackupReminderPolicy.isDue(
                lastBackupTimestamp = null,
                snoozeUntil = now + 2L * day,
                now = now
            )
        )
    }
}
