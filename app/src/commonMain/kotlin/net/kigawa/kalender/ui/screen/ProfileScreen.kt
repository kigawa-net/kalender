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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kigawa.kalender.data.LinkedCalendarAccount
import net.kigawa.kalender.model.UserCalendar
import net.kigawa.kalender.ui.component.ErrorMessage
import net.kigawa.kalender.viewmodel.ProfileUiState
import net.kigawa.kalender.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileContent(
        uiState = uiState,
        onLink = { provider -> viewModel.linkAccount(provider) },
        onUnlink = { accountId, ownerAccountId -> viewModel.unlinkAccount(accountId, ownerAccountId) },
        onCalendarVisibilityChanged = viewModel::updateCalendarVisibility,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onLink: (String) -> Unit,
    onUnlink: (String, String) -> Unit,
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
            if (uiState.linkError != null) {
                ErrorMessage(message = uiState.linkError!!)
            }

            // プロバイダごとにグループ化
            val accountsByProvider = uiState.linkedCalendarAccounts.groupBy { it.provider }
            val providers = listOf("google", "microsoft")
            val providerLabels = mapOf("google" to "Google", "microsoft" to "Microsoft / Outlook")

            providers.forEach { providerId ->
                val accounts = accountsByProvider[providerId] ?: emptyList()
                val providerLabel = providerLabels[providerId] ?: providerId

                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()

                if (accounts.isNotEmpty()) {
                    accounts.forEach { account ->
                        ConnectedCalendarAccountItem(
                            account = account,
                            onRemove = { onUnlink(account.id, account.providerUserId) },
                        )
                        HorizontalDivider()
                        uiState.calendarsByOwnerAccountId[account.providerUserId].orEmpty().forEach { calendar ->
                            CalendarItem(
                                calendar = calendar,
                                onVisibilityChanged = { isVisible ->
                                    onCalendarVisibilityChanged(calendar.id, isVisible)
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                } else {
                    when {
                        uiState.isLoadingLinkedAccounts -> {
                            AddingAccountItem(label = "確認中")
                            HorizontalDivider()
                        }
                        else -> {
                            AddAccountItem(label = "アカウントを追加", onAdd = { onLink(it) })
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
        modifier = modifier.padding(start = 56.dp),
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
private fun ConnectedCalendarAccountItem(
    account: net.kigawa.kalender.data.LinkedCalendarAccount,
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
        supportingContent = {
            Column {
                if (account.displayName != null) {
                    Text(account.displayName!!, style = MaterialTheme.typography.bodySmall)
                }
                Text("連携済み", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            OutlinedButton(onClick = onRemove) { Text("連携解除") }
        },
    )
}

@Composable
private fun AddAccountItem(
    label: String,
    onAdd: (String) -> Unit,
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
            TextButton(onClick = { onAdd("google") }) { Text("追加") }
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
        supportingContent = { Text("処理中…") },
    )
}