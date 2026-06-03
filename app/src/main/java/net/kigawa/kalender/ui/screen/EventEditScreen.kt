package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.EventEditUiState
import net.kigawa.kalender.viewmodel.EventEditViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onBack: () -> Unit,
    viewModel: EventEditViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
    }

    EventEditContent(
        uiState = uiState,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onTitleChange = viewModel::setTitle,
        onDescriptionChange = viewModel::setDescription,
        onLocationChange = viewModel::setLocation,
        onCalendarChange = viewModel::setCalendarId,
        onAllDayChange = viewModel::setAllDay,
        onStartDateChange = viewModel::setStartDate,
        onStartTimeChange = viewModel::setStartTime,
        onEndDateChange = viewModel::setEndDate,
        onEndTimeChange = viewModel::setEndTime,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditContent(
    uiState: EventEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onCalendarChange: (Long) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onStartDateChange: (Long) -> Unit,
    onStartTimeChange: (Int, Int) -> Unit,
    onEndDateChange: (Long) -> Unit,
    onEndTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = uiState.isSaving || uiState.isDeleting

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "新しい予定" else "予定を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !busy) {
                        Text("保存")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = { Text("タイトル") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("終日", modifier = Modifier.weight(1f))
                Switch(checked = uiState.allDay, onCheckedChange = onAllDayChange, enabled = !busy)
            }

            HorizontalDivider()

            DateTimeRow(
                label = "開始",
                ms = uiState.startMs,
                allDay = uiState.allDay,
                enabled = !busy,
                onDateChange = onStartDateChange,
                onTimeChange = onStartTimeChange,
            )

            DateTimeRow(
                label = "終了",
                ms = uiState.endMs,
                allDay = uiState.allDay,
                enabled = !busy,
                onDateChange = onEndDateChange,
                onTimeChange = onEndTimeChange,
            )

            HorizontalDivider()

            CalendarSelector(
                calendars = uiState.calendars,
                selectedId = uiState.calendarId,
                enabled = !busy,
                onSelect = onCalendarChange,
            )

            OutlinedTextField(
                value = uiState.location,
                onValueChange = onLocationChange,
                label = { Text("場所") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text("説明") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                minLines = 3,
            )

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!uiState.isNew) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("予定を削除")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    ms: Long,
    allDay: Boolean,
    enabled: Boolean,
    onDateChange: (Long) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val zdt = remember(ms) { Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()) }
    val dateFmt = DateTimeFormatter.ofPattern("yyyy年M月d日(EEE)", Locale.JAPANESE)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.15f))
        Text(
            text = zdt.format(dateFmt),
            modifier = Modifier
                .weight(if (allDay) 0.85f else 0.55f)
                .clickable(enabled = enabled) { showDatePicker = true },
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!allDay) {
            Text(
                text = zdt.format(timeFmt),
                modifier = Modifier
                    .weight(0.3f)
                    .clickable(enabled = enabled) { showTimePicker = true },
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = ms)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("時刻を選択") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("キャンセル") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSelector(
    calendars: List<net.kigawa.kalender.model.UserCalendar>,
    selectedId: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("カレンダー") },
            leadingIcon = selected?.let { cal ->
                {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(cal.color), CircleShape),
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(calendar.color), CircleShape),
                        )
                    },
                    text = { Text(calendar.name) },
                    onClick = {
                        onSelect(calendar.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventEditScreenPreview() {
    KalenderTheme {
        EventEditContent(
            uiState = EventEditUiState(
                isNew = true,
                isLoading = false,
                title = "会議",
                startMs = System.currentTimeMillis(),
                endMs = System.currentTimeMillis() + 3600_000L,
            ),
            onBack = {},
            onSave = {},
            onDelete = {},
            onTitleChange = {},
            onDescriptionChange = {},
            onLocationChange = {},
            onCalendarChange = {},
            onAllDayChange = {},
            onStartDateChange = {},
            onStartTimeChange = { _, _ -> },
            onEndDateChange = {},
            onEndTimeChange = { _, _ -> },
        )
    }
}
