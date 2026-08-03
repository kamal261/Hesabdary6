package com.kamal.smsfinance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.data.CategoryKind
import com.kamal.smsfinance.data.Counterparty
import com.kamal.smsfinance.data.TransactionType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
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

    val amountValid = amountText.toLongOrNull()?.let { it > 0 } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودن تراکنش دستی") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("انصراف") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IndirectSettlementReminderCard()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isIndirectSettlement = !isIndirectSettlement },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isIndirectSettlement, onCheckedChange = { isIndirectSettlement = it })
                Spacer(Modifier.width(8.dp))
                Text("این یک تسویه غیرمستقیم است (پرداخت از طریق شخص ثالث)")
            }

            SingleChoiceSegment(selected = type, onSelect = { type = it })

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> if (input.all { it.isDigit() }) amountText = input },
                label = { Text("مبلغ (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amountText.isNotEmpty() && !amountValid,
                modifier = Modifier.fillMaxWidth()
            )

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

            CategoryDropdown(
                categories = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )

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

            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val amount = amountText.toLong()
                    val date = Calendar.getInstance().timeInMillis
                    if (isIndirectSettlement) {
                        onSaveIndirectSettlement(
                            amount, type, selectedCounterparty?.id, description, date, selectedCategory?.id
                        )
                    } else {
                        onSave(amount, type, bank, description, date, selectedCategory?.id, selectedCounterparty?.id)
                    }
                },
                enabled = amountValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ذخیره تراکنش")
            }
        }
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

@Composable
private fun SingleChoiceSegment(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == TransactionType.EXPENSE,
            onClick = { onSelect(TransactionType.EXPENSE) },
            label = { Text("برداشت / هزینه") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selected == TransactionType.INCOME,
            onClick = { onSelect(TransactionType.INCOME) },
            label = { Text("واریز / درآمد") },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(categories: List<Category>, selected: Category?, onSelect: (Category?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "بدون دسته",
            onValueChange = {},
            readOnly = true,
            label = { Text("دسته‌بندی") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("بدون دسته") }, onClick = { onSelect(null); expanded = false })
            categories.forEach { cat ->
                DropdownMenuItem(text = { Text(cat.name) }, onClick = { onSelect(cat); expanded = false })
            }
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

