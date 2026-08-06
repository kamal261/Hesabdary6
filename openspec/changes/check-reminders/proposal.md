# Proposal: Check Reminders (یادآوری چک)

## Problem
Users receive post-dated checks (چک‌های تاریخ‌دار) and need reminders before the check maturity date to ensure funds are available. Currently, the app only tracks checks in-app without proactive notifications.

## Solution
Add configurable in-app reminders for post-dated checks:
- User sets `reminderDays` (e.g., 3 days before maturity)
- App shows reminder in Check list screen and optionally as a notification
- Reminder triggers when `maturityDate - reminderDays <= today`

## Scope
- Extend `Check` entity with `reminderDays` (already exists) and reminder logic
- Add reminder computation in `ChecksScreen` / Check repository
- Show visual indicator (badge/color) for checks nearing maturity
- No system notifications in MVP (per product decision in `SmsFinanceApp.kt`)

## Non-Goals
- System push notifications (explicitly out of scope per `SmsFinanceApp.kt`)
- SMS/Email reminders
- Recurring check patterns
- Integration with calendar apps

## Acceptance Criteria
- Check with `reminderDays=3` and maturity in 2 days shows "⚠️ 2 days left"
- Check with `reminderDays=0` shows on maturity date
- Check past maturity without deposit shows "⚠️ Overdue"
- All existing check tests pass
- New unit tests for reminder logic

## Technical Notes
- `Check.kt` already has `reminderDays: Int` field
- `ChecksScreen` already receives check list and renders UI
- Logic should live in repository/ViewModel layer, not in entity
- Consider time zone: use device local date for comparison