// SmsFinance file version: 2 — redesigned as a real quick-entry flow: amount → type → confirm is the default, everything else (bank/description/category/counterparty/indirect) collapses behind "جزئیات بیشتر"; also switched category picker to the shared CategoryPicker
package com.kamal.smsfinance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.TransactionType
import com.kamal.smsfinance.ui.components.CategoryPicker
import com.kamal.smsfinance.ui.theme.GreenIncome
import com.kamal.smsfinance.ui.theme.RedExpense
import com.kamal.smsfinance.util.normalizeDigits
import com.kamal.smsfinance.util.toPositiveLongOrNull
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    categoryUsageCounts: Map<Long, Int>,
    counterparties: List<Counterparty>,
    onSave: (
        amount: Long, type: TransactionType, bank: String, description: String,
        date: Long, categoryId: Long?, counterpartyId: Long?
    ) -> Unit,
    onSaveIndirectSettlement: (
        amount: Long, type: TransactionType, counterpartyId: Long?,
        description: String, date: Long, categoryId: Long?
    ) -> Unit,
    onCancel: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var bank by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedCounterparty by remember { mutableStateOf<Counterparty?>(null) }
    var isIndirectSettlement by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val amountValue = amountText.toPositiveLongOrNull()
    val amountValid = amountValue?.let { it > 0 } == true

    fun save() {
        val amount = amountValue ?: return
        val date = Calendar.getInstance().timeInMillis
        if (isIndirectSettlement) {
            onSaveIndirectSettlement(amount, type, selectedCounterparty?.id, description, date, selectedCategory?.id)
        } else {
            onSave(amount, type, bank, description, date, selectedCategory?.id, selectedCounterparty?.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودن تراکنش") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("انصراف") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Quick path: amount -> type -> confirm, big and friendly ---
            QuickTypeToggle(selected = type, onSelect = { type = it; selectedCategory = null })

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> if (input.normalizeDigits().all { it.isDigit() || it == ',' || it == '٬' || it == ' ' }) amountText = input },
                label = { Text("مبلغ (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amountText.isNotEmpty() && !amountValid,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { save() },
                enabled = amountValid,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("ثبت تراکنش", style = MaterialTheme.typography.titleMedium)
            }

            // --- Collapsed by default: everything else ---
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "بستن جزئیات بیشتر" else "جزئیات بیشتر (بانک، دسته، طرف‌حساب...)")
                Icon(
                    imageVector = if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(visible = showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    IndirectSettlementReminderCard()

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isIndirectSettlement = !isIndirectSettlement },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isIndirectSettlement, onCheckedChange = { isIndirectSettlement = it })
                        Spacer(Modifier.width(8.dp))
                        Text("این یک تسویه غیرمستقیم است (پرداخت از طریق شخص ثالث)")
                    }

                    if (!isIndirectSettlement) {
                        OutlinedTextField(
                            value = bank,
                            onValueChange = { bank = it },
                            label = { Text("نام بانک / منبع") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیحات") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Column {
                        Text("دسته‌بندی", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        CategoryPicker(
                            categories = categories,
                            usageCounts = categoryUsageCounts,
                            selectedId = selectedCategory?.id,
                            initialKind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                            allowSideToggle = false,
                            onSelect = { id -> selectedCategory = categories.firstOrNull { it.id == id } }
                        )
                    }

                    CounterpartyDropdown(
                        counterparties = counterparties,
                        selected = selectedCounterparty,
                        onSelect = { selectedCounterparty = it }
                    )

                    val needsCounterpartyNudge = selectedCounterparty == null &&
                        (selectedCategory?.kind == CategoryKind.DEBT_COLLECTION || selectedCategory?.kind == CategoryKind.DEBT_PAYMENT)
                    if (needsCounterpartyNudge) {
                        Text(
                            "برای دسته‌های «وصول طلب» و «پرداخت بدهی» بهتر است طرف‌حساب را هم مشخص کنید تا مانده حساب او درست محاسبه شود.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickTypeToggle(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigTypeButton(
            label = "برداشت",
            selected = selected == TransactionType.EXPENSE,
            color = RedExpense,
            onClick = { onSelect(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f)
        )
        BigTypeButton(
            label = "واریز",
            selected = selected == TransactionType.INCOME,
            color = GreenIncome,
            onClick = { onSelect(TransactionType.INCOME) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BigTypeButton(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = modifier.height(56.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IndirectSettlementReminderCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                "برنامه نمی‌تواند واریز غیرمستقیم را به‌طور خودکار تشخیص دهد. اگر فردی که به شما " +
                    "بدهکار بود، به‌جای پرداخت به شما، بدهی خودش را به شخص دیگری پرداخت کرده (و این مبلغ " +
                    "در پیامک بانکی شما ظاهر نمی‌شود)، آن را اینجا به‌عنوان «تسویه غیرمستقیم» ثبت کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyDropdown(
    counterparties: List<Counterparty>,
    selected: Counterparty?,
    onSelect: (Counterparty?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "بدون طرف حساب",
            onValueChange = {},
            readOnly = true,
            label = { Text("طرف حساب") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("بدون طرف حساب") }, onClick = { onSelect(null); expanded = false })
            counterparties.forEach { cp ->
                DropdownMenuItem(text = { Text(cp.name) }, onClick = { onSelect(cp); expanded = false })
            }
        }
    }
}
