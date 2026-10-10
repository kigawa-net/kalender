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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kigawa.kalender.model.EventTemplate
import net.kigawa.kalender.model.Frequency
import net.kigawa.kalender.model.RecurrenceRule
import net.kigawa.kalender.ui.component.ErrorMessage
import net.kigawa.kalender.viewmodel.Optional
import net.kigawa.kalender.viewmodel.TemplateListViewModel

/**
 * テンプレートの一覧・作成・編集・削除を行う管理画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    onBack: () -> Unit,
    viewModel: TemplateListViewModel,
    onUseTemplate: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("テンプレート管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Default.Add, contentDescription = "新規テンプレート")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            uiState.error?.let { errorMessage ->
                ErrorMessage(
                    message = errorMessage,
                    onDismiss = viewModel::dismissError,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.templates.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onCreate = viewModel::startCreate,
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.templates, key = { it.id }) { template ->
                        TemplateListItem(
                            template = template,
                            onEdit = { viewModel.startEdit(template) },
                            onDelete = { viewModel.delete(template.id) },
                            onUse = { onUseTemplate(template.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    uiState.editingTemplate?.let { editing ->
        TemplateEditDialog(
            template = editing,
            isSaving = uiState.isSaving,
            onDismiss = viewModel::cancelEditing,
            onSave = viewModel::saveEditing,
            onNameChange = { viewModel.updateEditing(name = it) },
            onTitleChange = { viewModel.updateEditing(title = it) },
            onDescriptionChange = { viewModel.updateEditing(description = it) },
            onLocationChange = { viewModel.updateEditing(location = it) },
            onDurationChange = { viewModel.updateEditing(durationMinutes = it) },
            onAllDayChange = { viewModel.updateEditing(allDay = it) },
            onRecurrenceChange = { viewModel.updateEditing(recurrence = Optional.Present(it)) },
        )
    }
}

@Composable
private fun TemplateListItem(
    template: EventTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUse)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = template.name, style = MaterialTheme.typography.titleMedium)
            if (template.title.isNotBlank()) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = buildString {
                    append(if (template.allDay) "終日" else "${template.durationMinutes}分")
                    template.recurrence?.let { append(" / ").append(recurrenceLabel(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "編集")
        }
        IconButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Default.Delete, contentDescription = "削除")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("テンプレートを削除") },
            text = { Text("「${template.name}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditDialog(
    template: EventTemplate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onRecurrenceChange: (RecurrenceRule?) -> Unit,
) {
    var durationText by remember(template.id) {
        mutableStateOf(template.durationMinutes.toString())
    }
    var showRecurrence by remember(template.id) {
        mutableStateOf(template.recurrence != null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.id == 0L) "新しいテンプレート" else "テンプレートを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = template.name,
                    onValueChange = onNameChange,
                    label = { Text("テンプレート名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template.title,
                    onValueChange = onTitleChange,
                    label = { Text("予定のタイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("説明") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template.location,
                    onValueChange = onLocationChange,
                    label = { Text("場所") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("終日", modifier = Modifier.weight(1f))
                    Switch(checked = template.allDay, onCheckedChange = onAllDayChange)
                }
                if (!template.allDay) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { text ->
                            durationText = text.filter { it.isDigit() }
                            durationText.toIntOrNull()?.let(onDurationChange)
                        },
                        label = { Text("所要時間(分)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("繰り返し", modifier = Modifier.weight(1f))
                    Switch(
                        checked = showRecurrence,
                        onCheckedChange = { enabled ->
                            showRecurrence = enabled
                            onRecurrenceChange(
                                if (enabled) RecurrenceRule.weekly(byDay = listOf(1)) else null,
                            )
                        },
                    )
                }
                if (showRecurrence) {
                    RecurrenceSelector(
                        rule = template.recurrence ?: RecurrenceRule.weekly(byDay = listOf(1)),
                        onChange = onRecurrenceChange,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !isSaving && template.name.isNotBlank() && template.durationMinutes > 0,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun RecurrenceSelector(
    rule: RecurrenceRule,
    onChange: (RecurrenceRule?) -> Unit,
) {
    Column {
        Text("頻度", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FrequencyChip("毎日", rule.frequency == Frequency.DAILY) {
                onChange(rule.withFrequency(Frequency.DAILY))
            }
            FrequencyChip("毎週", rule.frequency == Frequency.WEEKLY) {
                onChange(rule.withFrequency(Frequency.WEEKLY))
            }
            FrequencyChip("毎月", rule.frequency == Frequency.MONTHLY) {
                onChange(rule.withFrequency(Frequency.MONTHLY))
            }
            FrequencyChip("毎年", rule.frequency == Frequency.YEARLY) {
                onChange(rule.withFrequency(Frequency.YEARLY))
            }
        }
    }
}

@Composable
private fun FrequencyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("テンプレートがありません", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCreate) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("作成する")
        }
    }
}
