package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Transaction
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense

@Composable
fun StatisticsScreen(
    transactions: List<Transaction>,
    totalIncome: Long,
    totalExpense: Long,
    estimatedProfitThisMonth: Long,
    debtCollected: Long,
    debtPaid: Long,
    byBank: Map<String, Long>,
    byCategory: Map<String, Long>,
    recurring: List<Transaction>
) {
    val balance = totalIncome - totalExpense

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("درآمد کل", totalIncome, GreenIncome, Modifier.weight(1f))
                SummaryCard("هزینه کل", totalExpense, RedExpense, Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("مانده", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${"%,d".format(balance)} تومان",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (balance >= 0) GreenIncome else RedExpense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("سود تقریبی این ماه", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${"%,d".format(estimatedProfitThisMonth)} تومان",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (estimatedProfitThisMonth >= 0) GreenIncome else RedExpense,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "این مقدار فقط از اختلاف درآمد و هزینه واقعی (بدون وصول طلب/پرداخت بدهی) محاسبه می‌شود و معادل سود حسابداری یا سود مشمول مالیات نیست.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (debtCollected != 0L || debtPaid != 0L) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard("وصول طلب", debtCollected, GreenIncome, Modifier.weight(1f))
                    SummaryCard("پرداخت بدهی", debtPaid, RedExpense, Modifier.weight(1f))
                }
            }
        }

        if (byBank.isNotEmpty()) {
            item { Text("تفکیک بر اساس بانک", style = MaterialTheme.typography.titleLarge) }
            items(byBank.entries.sortedByDescending { it.value }.toList()) { (bank, amount) ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(bank, style = MaterialTheme.typography.bodyLarge)
                        Text("${"%,d".format(amount)} ت", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (byCategory.isNotEmpty()) {
            item { Text("تفکیک بر اساس دسته‌بندی", style = MaterialTheme.typography.titleLarge) }
            items(byCategory.entries.sortedByDescending { it.value }.toList()) { (cat, amount) ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat, style = MaterialTheme.typography.bodyLarge)
                        Text("${"%,d".format(amount)} ت", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (recurring.isNotEmpty()) {
            item { Text("تراکنش‌های تکراری (قسط / قبض)", style = MaterialTheme.typography.titleLarge) }
            items(recurring.distinctBy { "${it.bankName}-${it.amountToman}-${it.type}" }) { txn ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(txn.bankName, style = MaterialTheme.typography.bodyLarge)
                            Text(txn.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${"%,d".format(txn.amountToman)} ت", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun SummaryCard(label: String, amount: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "${"%,d".format(amount)} ت",
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
