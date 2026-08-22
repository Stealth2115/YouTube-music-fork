package com.agon.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.viewmodel.PlayerViewModel

/**
 * Device-authorization dialog: shows the google.com/device URL + user code while
 * the ViewModel polls Google's token endpoint in the background. No password is
 * ever typed in the app.
 */
@Composable
fun YouTubeLoginDialog(vm: PlayerViewModel) {
    val context = LocalContext.current
    val url = vm.ytLoginUrl ?: "https://www.google.com/device"
    val code = vm.ytLoginCode.orEmpty()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    // Open the browser automatically and copy the code to the clipboard, so the user only
    // has to paste it (or pick their account if the code came pre-filled in the URL).
    LaunchedEffect(code) {
        if (code.isNotEmpty()) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            clipboard?.setPrimaryClip(ClipData.newPlainText("YouTube Music sign-in code", code))
        }
    }

    AlertDialog(
        onDismissRequest = { vm.cancelYtLogin() },
        title = { Text("Sign in to YouTube Music") },
        text = {
            Column {
                Text(
                    "Your browser opened automatically and the code was copied \u2014 just paste it there (or it's already filled in) and choose your account. Your password never touches this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text("If the browser didn't open", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    url,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Code (copied to clipboard)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard?.setPrimaryClip(ClipData.newPlainText("YouTube Music code", code)) },
                        enabled = code.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in browser")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Waiting for authorization\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.cancelYtLogin() }) { Text("Cancel") }
        },
    )
}
