// SmsFinance file version: 1
package com.kamal.smsfinance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.ui.theme.DashboardAccentContainer
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense

/**
 * The one card meant to answer, without any scrolling or navigation:
 * 1. امروز چقدر پول وارد شده و چقدر خرج شده؟
 * 2. سود تقریبی این ماه چقدر بوده؟
 * 3. چه کسی به من بدهکار است و من به چه کسی بدهکارم؟
 * 4. وضعیت چک‌های نزدیک سررسید چیست؟
 */
@Composable
fun TodayDashboardCard(
    todayIncome: Long,
    todayExpense: Long,
    estimatedProfitThisMonth: Long,
    totalOwedToMe: Long,
    totalIOwe: Long,
    checksDueSoonCount: Int,
    onOpenChecks: () -> Unit,
    onOpenCounterparties: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardAccentContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("وضعیت امروز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DashboardStat(label = "ورودی امروز", value = todayIncome, color = GreenIncome)
                DashboardStat(label = "خروجی امروز", value = todayExpense, color = RedExpense)
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("سود تقریبی این ماه", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${"%,d".format(estimatedProfitThisMonth)} ت",
                    fontWeight = FontWeight.Bold,
                    color = if (estimatedProfitThisMonth >= 0) GreenIncome else RedExpense
                )
            }

            if (totalOwedToMe != 0L || totalIOwe != 0L) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCounterparties),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("طلب از دیگران / بدهی به دیگران", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${"%,d".format(totalOwedToMe)} / ${"%,d".format(totalIOwe)} ت",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (checksDueSoonCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChecks),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("چک‌های نزدیک سررسید", style = MaterialTheme.typography.bodyMedium)
                    Text("$checksDueSoonCount مورد", fontWeight = FontWeight.Medium, color = RedExpense)
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: Long, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "${"%,d".format(value)} ت",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
