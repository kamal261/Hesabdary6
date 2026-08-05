// SmsFinance file version: 2 — added CSV import (categories/counterparties), small-amount auto-categorization settings, and a link to the unidentified-SMS review list
package com.kamal.smsfinance.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamal.smsfinance.data.Category
import com.kamal.smsfinance.util.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    webhookUrl: String,
    onWebhookUrlChange: (String) -> Unit,
    onExportCsv: () -> Unit,
    onUploadToSheets: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (android.net.Uri) -> Unit,
    onDeleteAll: () -> Unit,
    onManageCategories: () -> Unit,
    onManageRules: () -> Unit,
    onOpenUnidentifiedSms: () -> Unit,
    unidentifiedSmsCount: Int,
    driveSignedInEmail: String?,
    onDriveSignIn: () -> Unit,
    onDriveSignOut: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    categories: List<Category>,
    smallAmountEnabled: Boolean,
    onSmallAmountEnabledChange: (Boolean) -> Unit,
    smallAmountThreshold: Long,
    onSmallAmountThresholdChange: (Long) -> Unit,
    smallAmountCategoryId: Long?,
    onSmallAmountCategoryChange: (Long?) -> Unit,
    onImportCategoriesCsv: (android.net.Uri) -> Unit,
    onImportCounterpartiesCsv: (android.net.Uri) -> Unit
) {
    var webhookField by remember(webhookUrl) { mutableStateOf(webhookUrl) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onRestoreBackup) }

    val importCategoriesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onImportCategoriesCsv) }

    val importCounterpartiesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onImportCounterpartiesCsv) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSection(title = "ظاهر برنامه") {
            Column(Modifier.selectableGroup()) {
                ThemeOption("پیرو سیستم", ThemeMode.SYSTEM, themeMode, onThemeChange)
                ThemeOption("روشن", ThemeMode.LIGHT, themeMode, onThemeChange)
                ThemeOption("تیره", ThemeMode.DARK, themeMode, onThemeChange)
            }
        }

        SettingsSection(title = "دسته‌بندی‌ها و قوانین هوشمند") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onManageCategories, modifier = Modifier.fillMaxWidth()) {
                    Text("مدیریت دسته‌بندی‌ها")
                }
                OutlinedButton(onClick = onManageRules, modifier = Modifier.fillMaxWidth()) {
                    Text("مدیریت قوانین هوشمند (دسته‌بندی خودکار)")
                }
                OutlinedButton(onClick = onOpenUnidentifiedSms, modifier = Modifier.fillMaxWidth()) {
                    Text(if (unidentifiedSmsCount > 0) "پیامک‌های شناسایی‌نشده ($unidentifiedSmsCount)" else "پیامک‌های شناسایی‌نشده")
                }
            }
        }

        SettingsSection(title = "دسته‌بندی خودکار مبالغ کوچک") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فعال باشد", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = smallAmountEnabled, onCheckedChange = onSmallAmountEnabledChange)
                }
                if (smallAmountEnabled) {
                    var thresholdText by remember(smallAmountThreshold) { mutableStateOf(smallAmountThreshold.toString()) }
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                thresholdText = input
                                input.toLongOrNull()?.let(onSmallAmountThresholdChange)
                            }
                        },
                        label = { Text("سقف مبلغ (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SmallAmountCategoryDropdown(
                        categories = categories,
                        selectedId = smallAmountCategoryId,
                        onSelect = onSmallAmountCategoryChange
                    )
                    Text(
                        "هزینه‌های زیر این مبلغ که پیامک بانکی‌شان دریافت می‌شود، خودکار در دسته انتخابی بالا قرار می‌گیرند (مگر اینکه یک قانون هوشمند قبلاً آن‌ها را دسته‌بندی کرده باشد).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSection(title = "خروجی گرفتن") {
            Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("خروجی CSV / اکسل")
            }
        }

        SettingsSection(title = "وارد کردن از CSV") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "فایل باید هر خط را به شکل «نام,نوع» داشته باشد. موارد تکراری (بر اساس نام) نادیده گرفته می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { importCategoriesLauncher.launch("text/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("وارد کردن دسته‌ها از CSV")
                }
                OutlinedButton(onClick = { importCounterpartiesLauncher.launch("text/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("وارد کردن طرف‌حساب‌ها از CSV")
                }
            }
        }

        SettingsSection(title = "پشتیبان‌گیری محلی") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateBackup, modifier = Modifier.fillMaxWidth()) {
                    Text("ساخت نسخه پشتیبان")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازیابی از فایل پشتیبان")
                }
            }
        }

        SettingsSection(title = "پشتیبان‌گیری در Google Drive (اختیاری)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (driveSignedInEmail != null) {
                    Text(
                        "وارد شده با: $driveSignedInEmail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDriveBackup, modifier = Modifier.weight(1f)) { Text("پشتیبان‌گیری در Drive") }
                        OutlinedButton(onClick = onDriveRestore, modifier = Modifier.weight(1f)) { Text("بازیابی از Drive") }
                    }
                    TextButton(onClick = onDriveSignOut) { Text("خروج از حساب گوگل") }
                } else {
                    Text(
                        "برای پشتیبان‌گیری ابری در فضای خصوصی برنامه روی Google Drive، وارد حساب گوگل خود شوید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onDriveSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("ورود با حساب گوگل")
                    }
                }
            }
        }

        SettingsSection(title = "اتصال به Google Sheets (اختیاری)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "آدرس Web App اسکریپت گوگل خود را وارد کنید تا تراکنش‌ها با یک کلیک به شیت ارسال شوند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = webhookField,
                    onValueChange = { webhookField = it },
                    label = { Text("آدرس Webhook") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onWebhookUrlChange(webhookField) }, modifier = Modifier.weight(1f)) {
                        Text("ذخیره آدرس")
                    }
                    Button(onClick = onUploadToSheets, modifier = Modifier.weight(1f)) {
                        Text("ارسال به Sheets")
                    }
                }
            }
        }

        SettingsSection(title = "منطقه خطر") {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حذف تمام تراکنش‌ها")
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف همه تراکنش‌ها؟") },
            text = { Text("این عمل قابل بازگشت نیست. پیشنهاد می‌شود قبل از حذف، یک نسخه پشتیبان بگیرید.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); showDeleteConfirm = false }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallAmountCategoryDropdown(categories: List<Category>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: "انتخاب دسته"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("دسته پیش‌فرض برای مبالغ کوچک") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(text = { Text(cat.name) }, onClick = { onSelect(cat.id); expanded = false })
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    value: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = value == selected, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
