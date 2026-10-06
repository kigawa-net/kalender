package net.kigawa.kalender.ui.component

import org.jetbrains.compose.web.dom.window

@Composable
actual fun ErrorMessage(
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: androidx.compose.ui.Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text(
            text = message,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        androidx.compose.material3.IconButton(
            onClick = {
                copyToClipboard(message)
                onDismiss?.invoke()
            },
            modifier = androidx.compose.ui.Modifier
                .width(32.dp)
                .height(32.dp)
                .padding(start = 8.dp),
        ) {
            androidx.compose.material.icons.Icons.Default.ContentCopy
        }
        onDismiss?.let {
            androidx.compose.material3.IconButton(
                onClick = it,
                modifier = androidx.compose.ui.Modifier
                    .width(32.dp)
                    .height(32.dp),
            ) {
                androidx.compose.material.icons.Icons.Default.Close
            }
        }
    }
}

@Composable
actual fun copyToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}
