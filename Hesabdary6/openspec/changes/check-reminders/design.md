# Design: Check Reminders (یادآوری چک)

## Architecture Overview
This feature adds reminder logic to the existing Check tracking system. The design follows clean architecture: Entity → Repository → ViewModel → UI.

## Component Changes

### 1. Domain Layer
#### `Check.kt` (Entity)
- **No changes to entity fields** — `reminderDays` already exists
- **Add extension function** for reminder status computation

```kotlin
// In CheckExtensions.kt or CheckRepository
fun Check.getReminderStatus(today: LocalDate = LocalDate.now()): ReminderStatus
```

#### New: `ReminderStatus` enum
```kotlin
enum class ReminderStatus {
    NONE,       // No reminder needed
    UPCOMING,   // Within reminderDays window
    TODAY,      // Maturity date is today
    OVERDUE     // Past maturity, not deposited
}
```

### 2. Data Layer
#### `CheckRepository.kt`
- Add method: `getChecksNeedingAttention(): Flow<List<Check>>`
- Add method: `getAllChecksWithStatus(): Flow<List<Pair<Check, ReminderStatus>>>`

#### `CheckDao.kt` (Room)
- No new queries needed — existing `getAllChecks()` sufficient
- Status computed in repository/ViewModel

### 3. Presentation Layer
#### `ChecksViewModel.kt`
- Expose `checksWithStatus: StateFlow<List<CheckWithStatus>>`
- Add `filterOption: CheckFilter` state (ALL, NEED_ATTENTION, OVERDUE_ONLY)
- Sorting logic: OVERDUE → TODAY → UPCOMING → NONE

#### Data class for UI
```kotlin
data class CheckWithStatus(
    val check: Check,
    val status: ReminderStatus,
    val displayText: String,    // "3 days left", "Today", "Overdue"
    val sortOrder: Int          // for sorting
)
```

#### `ChecksScreen.kt` (Compose UI)
- Display `ReminderStatus` badge for each check
- Add filter chips: All / Needs Attention / Overdue
- Update list rendering to use `CheckWithStatus`
- Color coding: Red (OVERDUE), Orange (TODAY), Blue (UPCOMING), Gray (NONE)

### 4. Utility
#### `DateUtils.kt` (new or existing)
- Time zone handling: use `ZoneId.systemDefault()`
- Date comparison using `LocalDate` and `ChronoUnit.DAYS`

## Data Flow
```
User opens ChecksScreen
       ↓
ChecksViewModel.getChecksWithStatus()
       ↓
CheckRepository.getAllChecksWithStatus()
       ↓
CheckDao.getAllChecks() → Flow<List<Check>>
       ↓
Repository maps each Check → CheckWithStatus (computes ReminderStatus)
       ↓
ViewModel applies filter/sort → StateFlow<List<CheckWithStatus>>
       ↓
ChecksScreen renders list with badges
```

## Dependencies
- **Existing:** Room, Kotlin Flow, Jetpack Compose, Java Time API (`java.time.*`)
- **New:** None (all stdlib)

## Testing Strategy
- Unit: `CheckReminderTest` — `getReminderStatus` boundary conditions
- Unit: `CheckRepositoryTest` — mapping logic, filter/sort
- UI: `ChecksScreenTest` — badge rendering, filter chips (if Compose test)

## Migration/Compatibility
- Existing checks without `reminderDays` default to 3 (already in entity)
- No database migration needed
- Backward compatible — new fields are optional with defaults

## Non-Functional
- **Performance:** O(n) status computation on each emit; n = checks count (typically < 100)
- **Time zone:** Device local date (user expectation for check maturity)
- **Localization:** Persian for user-facing strings, English for code