// SmsFinance file version: 2 — refreshed palette: softer coral/amber for expenses instead of harsh pure red, richer emerald for income, warmer neutral surfaces, added category-kind accent colors for scannable UI
package com.kamal.smsfinance.ui.theme

import androidx.compose.ui.graphics.Color

// Income: a richer, slightly warm emerald -- readable on the light surface without being neon.
val GreenIncome = Color(0xFF176B48)
val GreenIncomeLight = Color(0xFF4CAF7D)
val GreenIncomeContainer = Color(0xFFDCF3E6)

// Expense: deep terracotta instead of pure alarm-red -- readable on white while
// still feeling like "money out", not an error state.
val RedExpense = Color(0xFFB84F2F)
val RedExpenseLight = Color(0xFFF08A63)
val RedExpenseContainer = Color(0xFFFBE6DD)

val PrimaryLight = Color(0xFF2E5FA3)
val PrimaryDark = Color(0xFF9EC1F2)

val BackgroundLight = Color(0xFFF7F7FA)
val BackgroundDark = Color(0xFF14161C)

val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E212B)

// Accent colors per category kind, used for small dots/badges so lists stay
// scannable at a glance without reading every label.
val KindIncome = GreenIncome
val KindExpense = RedExpense
val KindDebtCollection = Color(0xFF2E7D8A) // teal -- "owed to me"
val KindDebtPayment = Color(0xFF8A5A2E) // amber-brown -- "I owe"

// Warm accent used for the "today" hero/dashboard card.
val DashboardAccent = Color(0xFF6B4EA0)
val DashboardAccentContainer = Color(0xFFEAE1F7)
