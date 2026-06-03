package net.kigawa.kalender.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.kigawa.kalender.model.CalendarEvent
import net.kigawa.kalender.ui.theme.KalenderTheme
import net.kigawa.kalender.viewmodel.WeeklyCalendarViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters


private val HourHeight = 64.dp
private val TimeColumnWidth = 48.dp
private const val PAGER_TOTAL_PAGES = 20_000
private const val PAGER_INITIAL_PAGE = PAGER_TOTAL_PAGES / 2

@Composable
fun WeeklyCalendarScreen(
    onEventClick: (Long) -> Unit,
    onNewEvent: () -> Unit,
    viewModel: WeeklyCalendarViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val baseWeek = remember {
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun pageToWeek(page: Int): LocalDate = baseWeek.plusWeeks((page - PAGER_INITIAL_PAGE).toLong())

    val pagerState = rememberPagerState(initialPage = PAGER_INITIAL_PAGE) { PAGER_TOTAL_PAGES }

    LaunchedEffect(pagerState.settledPage) {
        viewModel.setWeek(pageToWeek(pagerState.settledPage))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        WeekNavigationHeader(
            weekStart = pageToWeek(pagerState.currentPage),
            onPrevious = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            onNext = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onToday = { coroutineScope.launch { pagerState.animateScrollToPage(PAGER_INITIAL_PAGE) } },
        )
        HorizontalDivider()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            val weekStart = pageToWeek(page)
            val events = uiState.eventsByWeek[weekStart].orEmpty()

            Column(modifier = Modifier.fillMaxSize()) {
                WeekDayHeaders(weekStart = weekStart)
                HorizontalDivider()
                val allDayEvents = events.filter { it.allDay }
                if (allDayEvents.isNotEmpty()) {
                    AllDayEventsRow(weekStart = weekStart, events = allDayEvents, onEventClick = onEventClick)
                    HorizontalDivider()
                }
                WeekTimeGrid(
                    weekStart = weekStart,
                    events = events.filter { !it.allDay },
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        }
        FloatingActionButton(
            onClick = onNewEvent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Icon(Icons.Default.Add, contentDescription = "新しい予定")
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
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Spacer(modifier = Modifier.width(TimeColumnWidth))
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isToday = date == today
            val isSaturday = i == 5
            val isSunday = i == 6
            val labelColor = when {
                isToday -> MaterialTheme.colorScheme.primary
                isSunday -> MaterialTheme.colorScheme.error
                isSaturday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            // 月初は "M/D" 形式で月を明示する
            val dateLabel = if (date.dayOfMonth == 1) "${date.monthValue}/${date.dayOfMonth}" else date.dayOfMonth.toString()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dayNames[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
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
                        text = dateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            isSunday -> MaterialTheme.colorScheme.error
                            isSaturday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val now = LocalTime.now()

    LaunchedEffect(Unit) {
        val scrollPx = (HourHeight.value * now.hour * 4 - 300).coerceAtLeast(0f).toInt()
        scrollState.scrollTo(scrollPx)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
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

            val columnBg = when {
                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                i % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else -> Color.Transparent
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(HourHeight * 24)
                    .background(columnBg),
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

                val eventLayouts = layoutDayEvents(dayEvents, ZoneId.systemDefault())
                eventLayouts.forEach { layout ->
                    val topOffset = HourHeight * layout.startMinutes / 60f
                    val eventHeight = HourHeight * layout.durationMinutes / 60f
                    val colWidth = maxWidth / layout.totalColumns
                    val leftOffset = colWidth * layout.columnIndex

                    Surface(
                        onClick = { onEventClick(layout.event.id) },
                        modifier = Modifier
                            .offset(x = leftOffset, y = topOffset)
                            .width(colWidth - 2.dp)
                            .height(maxOf(eventHeight, 28.dp)),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(layout.event.color).copy(alpha = 0.85f),
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = layout.event.title,
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
private fun WeekDayHeadersPreview() {
    KalenderTheme {
        WeekDayHeaders(weekStart = LocalDate.now())
    }
}
