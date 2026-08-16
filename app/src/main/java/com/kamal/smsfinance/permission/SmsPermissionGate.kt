package com.kamal.smsfinance.permission

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Requests only READ_SMS. The app remains usable without it: manual transactions,
 * reports, checks and counterparties continue to work, while SMS scanning is disabled
 * until the user grants permission.
 */
@Composable
fun SmsPermissionGate(
    onGranted: () -> Unit,
    content: @Composable (smsPermissionGranted: Boolean, requestSmsPermission: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var readSmsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var continueWithoutSms by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = true
        val wasGranted = readSmsGranted
        readSmsGranted = granted
        if (granted && !wasGranted) {
            continueWithoutSms = false
            onGranted()
        }
    }

    val requestSmsPermission = {
        permissionRequested = true
        launcher.launch(Manifest.permission.READ_SMS)
    }

    if (readSmsGranted || continueWithoutSms) {
        content(readSmsGranted, requestSmsPermission)
    } else {
        PermissionRationaleScreen(
            alreadyDenied = permissionRequested,
            onRequestClick = requestSmsPermission,
            onContinueWithoutSms = { continueWithoutSms = true }
        )
    }
}

@Composable
private fun PermissionRationaleScreen(
    alreadyDenied: Boolean,
    onRequestClick: () -> Unit,
    onContinueWithoutSms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Sms,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "دسترسی به پیامک‌ها",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "اگر اجازه بدهید، برنامه پیامک‌های بانکی را فقط روی همین گوشی بررسی می‌کند " +
                "تا تراکنش‌ها را خودکار ثبت کند. پیامک‌ها به سرور فرستاده نمی‌شوند و تصمیم مالی " +
                "بدون تأیید شما انجام نمی‌شود.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestClick, modifier = Modifier.fillMaxWidth()) {
            Text("اجازه خواندن پیامک‌ها")
        }
        OutlinedButton(onClick = onContinueWithoutSms, modifier = Modifier.fillMaxWidth()) {
            Text("ادامه بدون پیامک")
        }
        if (alreadyDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "می‌توانید بعداً از تنظیمات گوشی اجازه پیامک را فعال کنید. تا آن زمان، ثبت دستی و گزارش‌ها فعال هستند.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
