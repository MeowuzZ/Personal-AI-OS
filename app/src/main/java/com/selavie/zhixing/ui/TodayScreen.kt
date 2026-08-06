@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.selavie.zhixing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selavie.zhixing.AppController
import com.selavie.zhixing.model.CalendarEvent
import com.selavie.zhixing.model.TaskItem
import com.selavie.zhixing.model.TaskQuadrant
import com.selavie.zhixing.model.TaskStatus
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class TodayView(val label: String) { LIST("列表"), QUADRANT("四象限") }

private data class TodayEntry(
    val id: String,
    val title: String,
    val type: String,
    val timeText: String,
    val urgent: Boolean,
    val important: Boolean,
    val task: TaskItem? = null,
    val event: CalendarEvent? = null,
)

@Composable
fun TodayScreen(
    controller: AppController,
    navigate: (AppScreen) -> Unit,
    now: LocalDateTime,
    onDailyReview: (LocalDate) -> Unit,
) {
    val data = controller.data
    val today = now.toLocalDate()
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(today)
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val visibleMonth = runCatching { YearMonth.parse(visibleMonthText) }.getOrDefault(YearMonth.from(selected))
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    var viewName by rememberSaveable { mutableStateOf(TodayView.LIST.name) }
    val view = TodayView.valueOf(viewName)
    val reviewDate = when {
        !now.toLocalTime().isBefore(LocalTime.of(23, 30)) -> today
        now.toLocalTime().isBefore(LocalTime.of(6, 0)) -> today.minusDays(1)
        else -> null
    }
    val taskEntries = data.tasks.filter { controller.smartEngine.taskOccursOn(it, selected) }.map { task ->
        val segment = controller.smartEngine.taskSegmentOn(task, selected)
        TodayEntry(
            id = task.id,
            title = task.title,
            type = if (task.isTodo) "待办" else "日程",
            timeText = if (task.isTodo) {
                val end = task.endDate ?: task.dueDate
                if (end == task.dueDate) friendlyDate(task.dueDate.orEmpty()) else "${friendlyDate(task.dueDate.orEmpty())}—${friendlyDate(end.orEmpty())}"
            } else {
                val minute = segment?.startMinute ?: 0
                "%02d:%02d · %s".format(minute / 60, minute % 60, task.durationInput.ifBlank { durationText(task.estimateMinutes) })
            },
            urgent = task.urgent,
            important = task.important,
            task = task,
        )
    }
    val eventEntries = data.events.filter { it.date == selected.toString() }.map { event ->
        TodayEntry(
            id = event.id,
            title = event.title,
            type = "日程",
            timeText = "${event.startTime}—${event.endTime}",
            urgent = event.urgent,
            important = event.important,
            event = event,
        )
    }
    val entries = (taskEntries + eventEntries).sortedWith(compareBy<TodayEntry> { it.type == "待办" }.thenBy { it.timeText })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        reviewDate?.let { date ->
            item {
                Spacer(Modifier.height(2.dp))
                ZCard(color = Lime.copy(alpha = .62f), onClick = { onDailyReview(date) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每日复盘", style = MaterialTheme.typography.titleMedium)
                            Text(if (date == today) "整理今天完成的记录" else "回顾昨天完成的记录", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        Text("进入 →", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = if (reviewDate == null) 18.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)), style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text("你好，${data.preferences.name}${data.preferences.genderSymbol}", style = MaterialTheme.typography.headlineLarge)
                }
                TextButton(onClick = { navigate(AppScreen.CALENDAR) }) { Text("日历详情") }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Ink).clickable { navigate(AppScreen.PROFILE) },
                    contentAlignment = Alignment.Center,
                ) { Text(data.preferences.name.take(1), color = Lime, fontWeight = FontWeight.Black) }
            }
        }
        item {
            HomeCalendar(
                selected = selected,
                visibleMonth = visibleMonth,
                expanded = calendarExpanded,
                onToggleExpanded = { calendarExpanded = !calendarExpanded },
                onMonthChange = { visibleMonthText = it.toString() },
                onSelect = { date -> selectedDate = date.toString(); visibleMonthText = YearMonth.from(date).toString() },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayView.values().forEach { item ->
                    FilterChip(selected = view == item, onClick = { viewName = item.name }, label = { Text(item.label) })
                }
            }
        }
        when (view) {
            TodayView.LIST -> {
                val schedules = entries.filter { it.type == "日程" }
                val todos = entries.filter { it.type == "待办" }
                item { TodayListSection("日程", schedules, controller, now) }
                item { TodayListSection("待办", todos, controller, now) }
            }
            TodayView.QUADRANT -> item {
                QuadrantGrid(entries, controller)
            }
        }
    }
}

@Composable
private fun HomeCalendar(
    selected: LocalDate,
    visibleMonth: YearMonth,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    ZCard(modifier = Modifier.animateContentSize(tween(300))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(if (selected == LocalDate.now()) "今天" else selected.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)), style = MaterialTheme.typography.labelLarge, color = Blue)
        }
        Spacer(Modifier.height(12.dp))
        WeekHeader()
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            (0L..6L).map(weekStart::plusDays).forEach { date ->
                CalendarDay(date, selected, Modifier.weight(1f)) { onSelect(date) }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggleExpanded),
            contentAlignment = Alignment.Center,
        ) { Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge, color = Muted) }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(250)),
        ) {
            Column {
                Divider(color = Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonthChange(visibleMonth.minusMonths(1)) }) { Text("←") }
                    Text(visibleMonth.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { onMonthChange(visibleMonth.plusMonths(1)) }) { Text("→") }
                }
                WeekHeader()
                val first = visibleMonth.atDay(1)
                val leading = first.dayOfWeek.value - 1
                (0 until 42).map { index ->
                    val day = index - leading + 1
                    if (day in 1..visibleMonth.lengthOfMonth()) visibleMonth.atDay(day) else null
                }.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                            else CalendarDay(date, selected, Modifier.weight(1f)) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (index >= 5) Coral else Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, selected: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val active = date == selected
    val today = date == LocalDate.now()
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = CircleShape,
            color = when { active -> Ink; today -> Lime.copy(alpha = .5f); else -> Color.Transparent },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = if (active) Color.White else if (date.dayOfWeek.value >= 6) Coral else Ink)
            }
        }
    }
}

@Composable
private fun TodayListSection(title: String, entries: List<TodayEntry>, controller: AppController, now: LocalDateTime) {
    SectionTitle(title, "${entries.size} 项")
    Spacer(Modifier.height(8.dp))
    ZCard {
        if (entries.isEmpty()) Text("暂无$title", style = MaterialTheme.typography.bodyMedium, color = Muted)
        entries.forEachIndexed { index, entry ->
            if (index > 0) Divider(color = Line)
            TodayEntryRow(entry, controller, now)
        }
    }
}

@Composable
private fun TodayEntryRow(entry: TodayEntry, controller: AppController, now: LocalDateTime) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        entry.task?.let { task -> MiniTaskCheck(task.status == TaskStatus.DONE) { controller.toggleTask(task.id) } }
            ?: Box(Modifier.size(20.dp).clip(CircleShape).background(Blue.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(CircleShape).background(Blue)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.type} · ${entry.timeText}", style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        entry.task?.let { TaskPhaseTag(controller.smartEngine.taskPhase(it, now)) }
    }
}

@Composable
private fun QuadrantGrid(entries: List<TodayEntry>, controller: AppController) {
    val quadrants = TaskQuadrant.values().toList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quadrants.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { quadrant ->
                    QuadrantCard(
                        quadrant = quadrant,
                        entries = entries.filter { TaskQuadrant.from(it.urgent, it.important) == quadrant },
                        controller = controller,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(quadrant: TaskQuadrant, entries: List<TodayEntry>, controller: AppController, modifier: Modifier = Modifier) {
    val accent = when (quadrant) {
        TaskQuadrant.IMPORTANT_URGENT -> Coral
        TaskQuadrant.IMPORTANT_NOT_URGENT -> Color(0xFFE0A127)
        TaskQuadrant.NOT_IMPORTANT_URGENT -> Success
        TaskQuadrant.NOT_IMPORTANT_NOT_URGENT -> Blue
    }
    Surface(modifier = modifier.height(188.dp), shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f))) {
        Column {
            Column(Modifier.fillMaxWidth().background(accent.copy(alpha = .12f)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text("${quadrant.numeral} ${quadrant.label}", style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(accent.copy(alpha = .75f)))
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无内容", fontSize = 12.sp, color = accent.copy(alpha = .45f)) }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(132.dp), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)) {
                    items(entries, key = { "${quadrant.name}-${it.id}" }) { entry ->
                        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            entry.task?.let { task -> MiniTaskCheck(task.status == TaskStatus.DONE, 17) { controller.toggleTask(task.id) } }
                                ?: Box(Modifier.size(17.dp).clip(CircleShape).background(accent.copy(alpha = .18f)))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.type} · ${entry.timeText}", fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTaskCheck(checked: Boolean, size: Int = 20, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (checked) Ink else PaperDeep).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (checked) Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
