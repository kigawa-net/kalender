package net.kigawa.kalender.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.WeeklyCalendarUiState
import net.kigawa.kalender.viewmodel.WeeklyCalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val HourHeight = 64.dp
private val TimeColumnWidth = 48.dp

@Composable
fun WeeklyCalendarScreen(
    onEventClick: (Long) -> Unit,
    viewModel: WeeklyCalendarViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        WeekNavigationHeader(
            weekStart = uiState.weekStart,
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onToday = viewModel::goToToday,
        )
        HorizontalDivider()

        AnimatedContent(
            targetState = uiState.weekStart,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "week_slide",
        ) { weekStart ->
            Column(modifier = Modifier.fillMaxSize()) {
                WeekDayHeaders(
                    weekStart = weekStart,
                    onSwipeLeft = viewModel::nextWeek,
                    onSwipeRight = viewModel::previousWeek,
                )
                HorizontalDivider()
                val allDayEvents = uiState.events.filter { it.allDay }
                if (allDayEvents.isNotEmpty()) {
                    AllDayEventsRow(weekStart = weekStart, events = allDayEvents, onEventClick = onEventClick)
                    HorizontalDivider()
                }
                WeekTimeGrid(
                    weekStart = weekStart,
                    events = uiState.events.filter { !it.allDay },
                    onEventClick = onEventClick,
                    onSwipeLeft = viewModel::nextWeek,
                    onSwipeRight = viewModel::previousWeek,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekNavigationHeader(
    weekStart: java.time.LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前週")
        }
        Text(
            text = "${weekStart.year}年${weekStart.monthValue}月",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次週")
        }
        TextButton(onClick = onToday) { Text("今日") }
    }
}

@Composable
private fun WeekDayHeaders(
    weekStart: LocalDate,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")
    var dragAccumulation by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(onSwipeLeft, onSwipeRight) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulation = 0f },
                    onDragEnd = {
                        if (dragAccumulation > 80f) onSwipeRight()
                        else if (dragAccumulation < -80f) onSwipeLeft()
                        dragAccumulation = 0f
                    },
                    onDragCancel = { dragAccumulation = 0f },
                    onHorizontalDrag = { _, delta -> dragAccumulation += delta },
                )
            }
            .padding(vertical = 4.dp),
    ) {
        Spacer(modifier = Modifier.width(TimeColumnWidth))
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dayNames[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayEventsRow(
    weekStart: LocalDate,
    events: List<CalendarEvent>,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(modifier = Modifier.width(TimeColumnWidth), contentAlignment = Alignment.Center) {
            Text("終日", style = MaterialTheme.typography.labelSmall)
        }
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val dayEvents = events.filter { event ->
                Instant.ofEpochMilli(event.startMs).atZone(ZoneId.of("UTC")).toLocalDate() == date
            }
            Column(modifier = Modifier.weight(1f)) {
                dayEvents.forEach { event ->
                    Surface(
                        onClick = { onEventClick(event.id) },
                        modifier = Modifier.fillMaxWidth().padding(1.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(event.color).copy(alpha = 0.85f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTimeGrid(
    weekStart: LocalDate,
    events: List<CalendarEvent>,
    onEventClick: (Long) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val now = LocalTime.now()
    var dragAccumulation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val scrollPx = (HourHeight.value * now.hour * 4 - 300).coerceAtLeast(0f).toInt()
        scrollState.scrollTo(scrollPx)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onSwipeLeft, onSwipeRight) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulation = 0f },
                    onDragEnd = {
                        if (dragAccumulation > 80f) onSwipeRight()
                        else if (dragAccumulation < -80f) onSwipeLeft()
                        dragAccumulation = 0f
                    },
                    onDragCancel = { dragAccumulation = 0f },
                    onHorizontalDrag = { _, delta -> dragAccumulation += delta },
                )
            }
            .verticalScroll(scrollState),
    ) {
        Column(modifier = Modifier.width(TimeColumnWidth)) {
            for (hour in 0..23) {
                Box(
                    modifier = Modifier.height(HourHeight).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isToday = date == today
            val dayEvents = events.filter { event ->
                Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate() == date
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(HourHeight * 24),
            ) {
                for (hour in 0..23) {
                    HorizontalDivider(
                        modifier = Modifier.offset(y = HourHeight * hour),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                if (isToday) {
                    val nowMinutes = now.hour * 60 + now.minute
                    Box(
                        modifier = Modifier
                            .offset(y = HourHeight * nowMinutes / 60f)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }

                dayEvents.forEach { event ->
                    val startZdt = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault())
                    val startMinutes = startZdt.hour * 60 + startZdt.minute
                    val durationMinutes = maxOf(30, ((event.endMs - event.startMs) / 60_000L).toInt())
                    val topOffset = HourHeight * startMinutes / 60f
                    val eventHeight = HourHeight * durationMinutes / 60f

                    Surface(
                        onClick = { onEventClick(event.id) },
                        modifier = Modifier
                            .offset(y = topOffset)
                            .fillMaxWidth()
                            .height(maxOf(eventHeight, 28.dp))
                            .padding(horizontal = 1.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(event.color).copy(alpha = 0.85f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun WeeklyCalendarScreenPreview() {
    KalenderTheme {
        WeekDayHeaders(
            weekStart = LocalDate.now(),
            onSwipeLeft = {},
            onSwipeRight = {},
        )
    }
}
