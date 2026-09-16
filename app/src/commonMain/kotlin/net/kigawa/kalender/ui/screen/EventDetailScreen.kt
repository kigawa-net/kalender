package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.util.formatHm
import net.kigawa.kalender.util.formatJp
import net.kigawa.kalender.util.systemZone
import net.kigawa.kalender.util.timeZoneOrNull
import net.kigawa.kalender.util.toLocalDate
import net.kigawa.kalender.util.toLocalTime
import net.kigawa.kalender.viewmodel.EventDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: EventDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.event?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    uiState.event?.let { event ->
                        IconButton(onClick = { onEdit(event.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "編集")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.event == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("イベントが見つかりません")
            }

            else -> EventDetailContent(
                event = uiState.event!!,
                calendarName = uiState.calendarName,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun EventDetailContent(
    event: CalendarEvent,
    calendarName: String,
    modifier: Modifier = Modifier,
) {
    val zoneId = timeZoneOrNull(event.timeZone) ?: systemZone()

    val startDate = event.startMs.toLocalDate(zoneId)
    val startTime = event.startMs.toLocalTime(zoneId)
    val endTime = event.endMs.toLocalTime(zoneId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = event.title, style = MaterialTheme.typography.headlineSmall)

        Text(
            text = if (event.allDay) startDate.formatJp()
            else "${startDate.formatJp()} ${startTime.formatHm()}–${endTime.formatHm()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (calendarName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(12.dp).background(Color(event.color), CircleShape))
                Text(calendarName, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (event.location.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(event.location, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (event.description.isNotEmpty()) {
            HorizontalDivider()
            Text(event.description, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
