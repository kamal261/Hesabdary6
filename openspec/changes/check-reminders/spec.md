# Specification: Check Reminders (یادآوری چک)

## Overview
Implement in-app reminders for post-dated checks based on configurable advance notice (`reminderDays`).

## Data Model
### Check Entity (existing - `Check.kt`)
```kotlin
@Entity(tableName = "checks")
data class Check(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountToman: Long,
    val maturityDate: Long,        // epoch millis (local date)
    val reminderDays: Int = 3,     // days before maturity to remind
    val description: String = "",
    val isDeposited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

## Reminder Logic
### Reminder Status Computation
```kotlin
enum class ReminderStatus {
    NONE,           // reminderDays == 0 or far from maturity
    UPCOMING,       // within reminderDays window
    TODAY,          // maturity date is today
    OVERDUE         // past maturity and not deposited
}

fun Check.getReminderStatus(today: LocalDate = LocalDate.now()): ReminderStatus {
    val maturity = Instant.ofEpochMilli(maturityDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val daysUntil = ChronoUnit.DAYS.between(today, maturity)
    
    return when {
        isDeposited -> ReminderStatus.NONE
        daysUntil < 0 -> ReminderStatus.OVERDUE
        daysUntil == 0 -> ReminderStatus.TODAY
        daysUntil <= reminderDays -> ReminderStatus.UPCOMING
        else -> ReminderStatus.NONE
    }
}
```

## UI Requirements
### ChecksScreen Updates
- Show status badge/icon next to each check:
  - `UPCOMING`: 🔔 "X days left"
  - `TODAY`: ⏰ "Today"
  - `OVERDUE`: ⚠️ "Overdue"
- Sort checks: OVERDUE first, then TODAY, then UPCOMING, then others
- Filter option: "Show only checks needing attention"

## Configuration
- `reminderDays` per check (set at creation, editable)
- Default: 3 days (matches typical banking practice)

## Edge Cases
- Time zone: use device local date (not UTC)
- Leap years: `ChronoUnit.DAYS` handles correctly
- Checks created in past with future maturity: compute correctly
- `reminderDays = 0`: only show on maturity date (TODAY/OVERDUE)

## Testing
### Unit Tests (CheckReminderTest)
- `getReminderStatus` returns correct status for each boundary
- Time zone independence (test with fixed dates)
- Deposited checks always return NONE
- Overdue detection works past maturity

### UI Tests
- ChecksScreen shows correct badges
- Sorting order correct
- Filter works

## Files to Modify
1. `Check.kt` - add `getReminderStatus` extension or repository method
2. `CheckRepository.kt` - add query for checks needing attention
3. `ChecksViewModel.kt` - expose reminder status to UI
4. `ChecksScreen.kt` - render badges, sorting, filter
5. `CheckReminderTest.kt` - new unit test file (JUnit5)

## Acceptance Criteria (Reiterated)
- [ ] Check with `reminderDays=3`, maturity in 2 days → "🔔 2 days left"
- [ ] Check with `reminderDays=0`, maturity today → "⏰ Today"
- [ ] Check past maturity, not deposited → "⚠️ Overdue"
- [ ] Deposited check → no badge
- [ ] All existing tests pass
- [ ] New unit tests added and passing