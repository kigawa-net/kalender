package net.kigawa.kalender.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close

@Composable
fun ErrorMessage(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        IconButton(
            onClick = {
                copyToClipboard(message)
                onDismiss?.invoke()
            },
            modifier = Modifier
                .size(32.dp)
                .padding(start = 8.dp),
            contentDescription = "エラーメッセージをコピー"
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
        onDismiss?.let {
            IconButton(
                onClick = it,
                modifier = Modifier.size(32.dp),
                contentDescription = "閉じる"
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

expect fun copyToClipboard(text: String)