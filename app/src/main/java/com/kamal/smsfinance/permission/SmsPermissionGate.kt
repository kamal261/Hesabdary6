package com.kamal.smsfinance.permission

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shows an explanatory screen and requests READ_SMS + RECEIVE_SMS (and, on
 * Android 13+, POST_NOTIFICATIONS) at runtime. Calls onGranted() once READ_SMS
 * is available -- that's the one the app truly can't function without;
 * RECEIVE_SMS only affects real-time auto-import and notifications degrade
 * gracefully without POST_NOTIFICATIONS.
 *
 * Permission is requested ONLY on first run: once granted, it is remembered
 * by the OS and this gate passes through silently on every later launch --
 * the app never re-asks, and scanning happens through the first-run dialog,
 * not automatically here.
 */
@Composable
fun SmsPermissionGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    var readSmsGranted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    // Only READ_SMS + RECEIVE_SMS are requested -- the app never posts system
    // notifications, so POST_NOTIFICATIONS is intentionally not requested.
    val requiredPermissions = remember {
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionRequested = true
        readSmsGranted = results[Manifest.permission.READ_SMS] == true
        if (readSmsGranted) onGranted()
    }

    if (readSmsGranted) {
        content()
    } else {
        PermissionRationaleScreen(
            alreadyDenied = permissionRequested,
            onRequestClick = { launcher.launch(requiredPermissions.toTypedArray()) }
        )
    }
}

@Composable
private fun PermissionRationaleScreen(
    alreadyDenied: Boolean,
    onRequestClick: () -> Unit
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
            text = "برای شناسایی خودکار تراکنش‌های بانکی از پیامک‌ها، این اپ نیاز به " +
                "دسترسی خواندن پیامک (READ_SMS) دارد. تمام پردازش‌ها فقط روی گوشی شما " +
                "انجام می‌شود و هیچ پیامکی به سرور خارجی ارسال نمی‌شود.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequestClick, modifier = Modifier.fillMaxWidth()) {
            Text("دادن دسترسی")
        }
        if (alreadyDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "اگر دسترسی را رد کرده‌اید، لازم است از تنظیمات گوشی آن را فعال کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
