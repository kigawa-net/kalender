package net.kigawa.kalender.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
actual fun ErrorMessage(
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Error Message", message)
                clipboard.setPrimaryClip(clip)
                onDismiss?.invoke()
            },
            modifier = Modifier
                .width(32.dp)
                .height(32.dp)
                .padding(start = 8.dp),
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "エラーメッセージをコピー",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            )
        }
        onDismiss?.let { dismiss ->
            IconButton(
                onClick = dismiss,
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "閉じる",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}
