package net.kigawa.kalender.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme as MaterialTheme3

@Composable
actual fun ErrorMessage(
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: androidx.compose.ui.Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme3.colorScheme.error,
            style = MaterialTheme3.typography.bodySmall,
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Error Message", message)
                clipboard.setPrimaryClip(clip)
                onDismiss?.invoke()
            },
            modifier = androidx.compose.ui.Modifier
                .width(32.dp)
                .height(32.dp)
                .padding(start = 8.dp),
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.ContentCopy, contentDescription = "エラーメッセージをコピー", tint = MaterialTheme3.colorScheme.error.copy(alpha = 0.7f))
        }
        onDismiss?.let {
            IconButton(
                onClick = it,
                modifier = androidx.compose.ui.Modifier
                    .width(32.dp)
                    .height(32.dp),
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "閉じる", tint = MaterialTheme3.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}
