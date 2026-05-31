package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.GoogleAccount
import net.kigawa.kalender.viewmodel.OutlookAccount
import net.kigawa.kalender.viewmodel.ProfileUiState
import net.kigawa.kalender.viewmodel.ProfileViewModel

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ProfileContent(
        uiState = uiState,
        onAddAccount = { context.findActivity()?.let { viewModel.addAccount(it) } },
        onRemoveAccount = viewModel::removeAccount,
        onRetryAddAccount = { context.findActivity()?.let { viewModel.addAccount(it) } },
        onAddGoogleAccount = { viewModel.addGoogleAccount(context) },
        onRemoveGoogleAccount = viewModel::removeGoogleAccount,
        onRetryAddGoogleAccount = { viewModel.addGoogleAccount(context) },
        onCalendarVisibilityChanged = viewModel::updateCalendarVisibility,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onRetryAddAccount: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onRemoveGoogleAccount: () -> Unit,
    onRetryAddGoogleAccount: () -> Unit,
    onCalendarVisibilityChanged: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("アカウント設定") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Google Section
            Text(
                text = "Google",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            val googleAccount = uiState.googleAccount
            if (googleAccount != null) {
                ConnectedGoogleAccountItem(
                    account = googleAccount,
                    onRemove = onRemoveGoogleAccount,
                )
                HorizontalDivider()
                uiState.calendars
                    .filter { it.accountName == googleAccount.email }
                    .forEach { calendar ->
                        CalendarItem(
                            calendar = calendar,
                            onVisibilityChanged = { isVisible ->
                                onCalendarVisibilityChanged(calendar.id, isVisible)
                            },
                        )
                        HorizontalDivider()
                    }
            } else {
                when {
                    uiState.isAddingGoogleAccount -> AddingAccountItem(label = "Googleアカウントを追加")
                    uiState.addGoogleAccountError != null -> AddAccountErrorItem(
                        label = "Googleアカウントを追加",
                        message = uiState.addGoogleAccountError,
                        onRetry = onRetryAddGoogleAccount,
                    )
                    else -> AddAccountItem(label = "Googleアカウントを追加", onAdd = onAddGoogleAccount)
                }
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outlook Section
            Text(
                text = "Microsoft / Outlook",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            uiState.accounts.forEach { account ->
                ConnectedAccountItem(
                    account = account,
                    onRemove = { onRemoveAccount(account.email) },
                )
                HorizontalDivider()
                uiState.calendars
                    .filter { it.accountName == account.email }
                    .forEach { calendar ->
                        CalendarItem(
                            calendar = calendar,
                            onVisibilityChanged = { isVisible ->
                                onCalendarVisibilityChanged(calendar.id, isVisible)
                            },
                        )
                        HorizontalDivider()
                    }
            }

            when {
                uiState.isAddingAccount -> AddingAccountItem(label = "Outlookアカウントを追加")
                uiState.addAccountError != null -> AddAccountErrorItem(
                    label = "Outlookアカウントを追加",
                    message = uiState.addAccountError,
                    onRetry = onRetryAddAccount,
                )
                else -> AddAccountItem(label = "Outlookアカウントを追加", onAdd = onAddAccount)
            }
        }
    }
}

@Composable
private fun CalendarItem(
    calendar: UserCalendar,
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(calendar.color), CircleShape),
            )
        },
        headlineContent = { Text(calendar.name) },
        trailingContent = {
            Switch(
                checked = calendar.isVisible,
                onCheckedChange = onVisibilityChanged,
            )
        },
    )
}

@Composable
private fun ConnectedGoogleAccountItem(
    account: GoogleAccount,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(account.displayName ?: account.email) },
        supportingContent = { if (account.displayName != null) Text(account.email) else Text("接続済み") },
        trailingContent = {
            OutlinedButton(onClick = onRemove) { Text("サインアウト") }
        },
    )
}

@Composable
private fun ConnectedAccountItem(
    account: OutlookAccount,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(account.email) },
        supportingContent = { Text("接続済み") },
        trailingContent = {
            OutlinedButton(onClick = onRemove) { Text("サインアウト") }
        },
    )
}

@Composable
private fun AddAccountItem(
    label: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(label) },
        trailingContent = {
            TextButton(onClick = onAdd) { Text("サインイン") }
        },
    )
}

@Composable
private fun AddingAccountItem(
    label: String,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        headlineContent = { Text(label) },
        supportingContent = { Text("サインイン中…") },
    )
}

@Composable
private fun AddAccountErrorItem(
    label: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        headlineContent = { Text(label) },
        supportingContent = { Text(message, color = MaterialTheme.colorScheme.error) },
        trailingContent = {
            TextButton(onClick = onRetry) { Text("再試行") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenEmptyPreview() {
    KalenderTheme {
        ProfileContent(
            uiState = ProfileUiState(),
            onAddAccount = {},
            onRemoveAccount = {},
            onRetryAddAccount = {},
            onAddGoogleAccount = {},
            onRemoveGoogleAccount = {},
            onRetryAddGoogleAccount = {},
            onCalendarVisibilityChanged = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenWithAccountsPreview() {
    KalenderTheme {
        ProfileContent(
            uiState = ProfileUiState(
                googleAccount = GoogleAccount("user@gmail.com", "Google User"),
                accounts = listOf(
                    OutlookAccount("work@example.com"),
                ),
                calendars = listOf(
                    UserCalendar(1L, "仕事", 0xFF4285F4.toInt(), "user@gmail.com"),
                    UserCalendar(2L, "個人", 0xFF34A853.toInt(), "user@gmail.com", isVisible = false),
                    UserCalendar(3L, "会議", 0xFFEA4335.toInt(), "work@example.com"),
                ),
            ),
            onAddAccount = {},
            onRemoveAccount = {},
            onRetryAddAccount = {},
            onAddGoogleAccount = {},
            onRemoveGoogleAccount = {},
            onRetryAddGoogleAccount = {},
            onCalendarVisibilityChanged = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenAddingPreview() {
    KalenderTheme {
        ProfileContent(
            uiState = ProfileUiState(
                accounts = listOf(OutlookAccount("work@example.com")),
                isAddingAccount = true,
                isAddingGoogleAccount = false,
            ),
            onAddAccount = {},
            onRemoveAccount = {},
            onRetryAddAccount = {},
            onAddGoogleAccount = {},
            onRemoveGoogleAccount = {},
            onRetryAddGoogleAccount = {},
            onCalendarVisibilityChanged = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenErrorPreview() {
    KalenderTheme {
        ProfileContent(
            uiState = ProfileUiState(addAccountError = "認証に失敗しました"),
            onAddAccount = {},
            onRemoveAccount = {},
            onRetryAddAccount = {},
            onAddGoogleAccount = {},
            onRemoveGoogleAccount = {},
            onRetryAddGoogleAccount = {},
            onCalendarVisibilityChanged = { _, _ -> },
        )
    }
}
