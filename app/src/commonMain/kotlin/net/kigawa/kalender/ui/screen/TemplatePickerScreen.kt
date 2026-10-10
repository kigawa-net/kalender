package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.kigawa.kalender.di.LocalAppContainer
import net.kigawa.kalender.model.EventTemplate
import net.kigawa.kalender.viewmodel.TemplateListViewModel

/**
 * テンプレート一覧から予定を作成するための選択画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerScreen(
    onBack: () -> Unit,
    onUseTemplate: (Long) -> Unit,
    onManageTemplates: () -> Unit,
) {
    val container = LocalAppContainer.current
    val store = container.templateStore
    if (store == null) {
        TemplateUnavailable(onBack = onBack)
        return
    }

    val vm: TemplateListViewModel = viewModel(factory = viewModelFactory {
        initializer { TemplateListViewModel(store) }
    })
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("テンプレートから作成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = onManageTemplates) {
                        Icon(Icons.Default.Settings, contentDescription = "テンプレート管理")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.templates.isEmpty() -> EmptyTemplates(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onManageTemplates = onManageTemplates,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(uiState.templates, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        onClick = { onUseTemplate(template.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: EventTemplate,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = template.name,
            style = MaterialTheme.typography.titleMedium,
        )
        if (template.title.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "タイトル: ${template.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(if (template.allDay) "終日" else "${template.durationMinutes}分")
                if (template.recurrence != null) {
                    append(" / ")
                    append(recurrenceLabel(template.recurrence))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTemplates(
    modifier: Modifier = Modifier,
    onManageTemplates: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "テンプレートがありません",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "テンプレートを登録すると、よく使う予定をすばやく作成できます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onManageTemplates) {
            Text("テンプレートを管理")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateUnavailable(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("テンプレート") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("この環境ではテンプレート機能を利用できません")
        }
    }
}

internal fun recurrenceLabel(rule: net.kigawa.kalender.model.RecurrenceRule): String =
    when (rule.frequency) {
        net.kigawa.kalender.model.Frequency.NONE -> "繰り返しなし"
        net.kigawa.kalender.model.Frequency.DAILY -> "毎日"
        net.kigawa.kalender.model.Frequency.WEEKLY ->
            if (rule.byDay.size <= 1) "毎週" else "毎週(複数曜日)"
        net.kigawa.kalender.model.Frequency.MONTHLY -> "毎月"
        net.kigawa.kalender.model.Frequency.YEARLY -> "毎年"
    }
