# Tasks: Check Reminders (یادآوری چک)

## Task Breakdown

### Phase 1: Domain & Data Layer
- [ ] **T1.1** Create `ReminderStatus` enum in `domain/reminder/ReminderStatus.kt`
  - Values: NONE, UPCOMING, TODAY, OVERDUE
  - Unit test: verify enum values exist

- [ ] **T1.2** Add `Check.getReminderStatus()` extension function in `domain/reminder/CheckReminderExtensions.kt`
  - Input: `today: LocalDate = LocalDate.now()`
  - Logic per spec: deposited → NONE; daysUntil < 0 → OVERDUE; daysUntil == 0 → TODAY; daysUntil <= reminderDays → UPCOMING; else NONE
  - Unit tests: boundary conditions (-1, 0, 1, reminderDays, reminderDays+1), deposited check, time zone fixed

- [ ] **T1.3** Create `CheckWithStatus` data class in `presentation/checks/CheckWithStatus.kt`
  - Fields: check, status, displayText, sortOrder
  - Factory function: `CheckWithStatus.from(check: Check, today: LocalDate)`

- [ ] **T1.4** Update `CheckRepository` with status computation
  - Add `getAllChecksWithStatus(): Flow<List<CheckWithStatus>>`
  - Add `getChecksNeedingAttention(): Flow<List<CheckWithStatus>>` (filters UPCOMING/TODAY/OVERDUE)
  - Unit tests: mapping correctness, filter accuracy

### Phase 2: Presentation Layer
- [ ] **T2.1** Update `ChecksViewModel`
  - Expose `checksWithStatus: StateFlow<List<CheckWithStatus>>`
  - Add `filterOption: CheckFilter` state (ALL, NEED_ATTENTION, OVERDUE_ONLY)
  - Add sorting: OVERDUE (0) → TODAY (1) → UPCOMING (2) → NONE (3)
  - Unit tests: filter/sort logic with mocked repository data

- [ ] **T2.2** Update `ChecksScreen` (Compose UI)
  - Render `ReminderStatus` badge for each check:
    - OVERDUE: 🔴 "Overdue" / "معوق"
    - TODAY: 🟠 "Today" / "امروز"
    - UPCOMING: 🔵 "X days left" / "X روز باقی"
    - NONE: no badge
  - Add filter chips: All / Needs Attention / Overdue
  - Apply sorting from ViewModel
  - UI tests: badge rendering, filter chips, sort order

### Phase 3: Testing & Integration
- [ ] **T3.1** Create `CheckReminderTest.kt` (JUnit5, JVM)
  - Test all boundary conditions for `getReminderStatus`
  - Test deposited check always returns NONE
  - Test time zone independence (use fixed LocalDate)
  - Run: `./gradlew testDebugUnitTest`

- [ ] **T3.2** Create `CheckRepositoryTest.kt` (JUnit5)
  - Test `getAllChecksWithStatus` mapping
  - Test `getChecksNeedingAttention` filtering
  - Run: `./gradlew testDebugUnitTest`

- [ ] **T3.3** Run full test suite
  - `./gradlew testDebugUnitTest` — all 59+ existing + new tests pass
  - `./gradlew assembleDebug` — APK builds successfully

### Phase 4: Polish & Documentation
- [ ] **T4.1** Add Persian strings for reminder statuses in `strings.xml`
- [ ] **T4.2** Update `Check` entity docs with reminder logic reference
- [ ] **T4.3** Verify no regression in existing check functionality

## Acceptance Criteria Checklist
- [ ] Check with `reminderDays=3`, maturity in 2 days → "🔔 2 days left" / "🔔 ۲ روز باقی"
- [ ] Check with `reminderDays=0`, maturity today → "⏰ Today" / "⏰ امروز"
- [ ] Check past maturity, not deposited → "⚠️ Overdue" / "⚠️ معوق"
- [ ] Deposited check → no badge
- [ ] All existing tests pass (59+)
- [ ] New unit tests added and passing
- [ ] APK builds successfully

## Dependencies
- T1.1 → T1.2 → T1.3 → T1.4 → T2.1 → T2.2
- T3.1, T3.2 can run in parallel after T1.4
- T3.3 after T3.1, T3.2
- T4.1 after T2.2

## Estimated Effort
| Task | Hours |
|------|-------|
| T1.1-T1.4 | 3-4 |
| T2.1-T2.2 | 3-4 |
| T3.1-T3.3 | 2-3 |
| T4.1-T4.3 | 1-2 |
| **Total** | **9-13** |

## Notes
- No database migration needed (existing `reminderDays` field)
- Time zone: device local (`ZoneId.systemDefault()`)
- Default `reminderDays = 3` already in entity
- Per product decision: no system push notifications (in-app only)