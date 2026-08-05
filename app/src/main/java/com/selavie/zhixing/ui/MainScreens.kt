@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.selavie.zhixing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selavie.zhixing.AppController
import com.selavie.zhixing.model.*
import com.selavie.zhixing.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OnboardingScreen(onEmpty: () -> Unit, onDemo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Ink), contentAlignment = Alignment.Center) {
                    Text("知", color = Lime, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("知行", style = MaterialTheme.typography.titleLarge)
                    Text("PERSONAL AI OS", style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(54.dp))
            Tag("离线优先 · 数据属于你", Lime)
            Spacer(Modifier.height(18.dp))
            Text("把想法变成\n今天的行动。", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(18.dp))
            Text("在一个地方记录任务、日记与长期目标。知行会帮你整理和复盘，但任何写入都先由你确认。", style = MaterialTheme.typography.bodyLarge, color = Muted)
            Spacer(Modifier.height(34.dp))
            listOf(
                "01" to "用时间轴看见每天的空闲与安排",
                "02" to "任务自动识别进行中与逾期状态",
                "03" to "长期目标保持独立，不挤占日程",
            ).forEach { (number, text) ->
                Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(number, style = MaterialTheme.typography.labelMedium, color = Coral)
                    Spacer(Modifier.width(18.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column {
            PrimaryButton("从空白空间开始", onClick = onEmpty, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            SecondaryButton("先用演示数据体验", onClick = onDemo, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("所有内容仅保存在这台设备中，可随时导出或清空。", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun TodayScreen(
    controller: AppController,
    navigate: (AppScreen) -> Unit,
    now: LocalDateTime,
    onDailyReview: (LocalDate) -> Unit,
) {
    val data = controller.data
    val today = LocalDate.now()
    val tasks = data.tasks.filter { controller.smartEngine.taskSegmentOn(it, today) != null }.sortedBy { task ->
        controller.smartEngine.taskSegmentOn(task, today)?.startMinute ?: Int.MAX_VALUE
    }
    val events = data.events.filter { it.date == today.toString() }.sortedBy { it.startTime }
    val reviewDate = when {
        now.hour >= 23 -> today
        now.hour < 6 -> today.minusDays(1)
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)), style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text("早安，${data.preferences.name}${data.preferences.genderSymbol}", style = MaterialTheme.typography.headlineLarge)
                }
                TextButton(onClick = { navigate(AppScreen.CALENDAR) }) { Text("日历") }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Ink).clickable { navigate(AppScreen.PROFILE) },
                    contentAlignment = Alignment.Center,
                ) { Text(data.preferences.name.take(1), color = Lime, fontWeight = FontWeight.Black) }
            }
        }
        reviewDate?.let { date ->
            item {
                ZCard(color = Lime.copy(alpha = .62f), onClick = { onDailyReview(date) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每日复盘已开放", style = MaterialTheme.typography.titleMedium)
                            Text(if (date == today) "整理今天完成的待办" else "补写昨天的完成记录", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        Text("进入 →", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            SectionTitle("今日待办", "${tasks.count { it.status == TaskStatus.DONE }} / ${tasks.size}")
            Spacer(Modifier.height(8.dp))
            ZCard {
                if (tasks.isEmpty()) {
                    Text("今天没有待办，给自己留一点从容。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                } else tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        phase = controller.smartEngine.taskPhase(task, now),
                        onToggle = { controller.toggleTask(task.id) },
                        compact = true,
                    )
                    if (task != tasks.last()) Divider(color = Line)
                }
            }
        }
        item {
            SectionTitle("今日日程", "${events.size} 项")
            Spacer(Modifier.height(8.dp))
            ZCard {
                if (events.isEmpty()) {
                    Text("今天还没有日程安排。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                } else events.forEach { event ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(62.dp)) {
                            Text(event.startTime, style = MaterialTheme.typography.labelLarge, color = Blue)
                            Text(event.endTime, style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Box(Modifier.width(4.dp).height(46.dp).clip(CircleShape).background(Lime))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            if (event.tags.isNotEmpty()) Text(event.tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

private enum class TaskView(val label: String) { LIST("列表"), DATE("日期"), TAG("标签") }

@Composable
fun TasksScreen(controller: AppController, now: LocalDateTime) {
    var viewName by rememberSaveable { mutableStateOf(TaskView.LIST.name) }
    val view = TaskView.valueOf(viewName)
    var editorTask by remember { mutableStateOf<TaskItem?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<TaskItem?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("EXECUTION", "任务", "记录时间、内容与标签", action = {
            TextButton(onClick = { createOpen = true }) { Text("新建") }
        })
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TaskView.values().toList()) { item ->
                FilterChip(selected = view == item, onClick = { viewName = item.name }, label = { Text(item.label) })
            }
        }
        when (view) {
            TaskView.LIST -> TaskListView(controller, now, expanded, onEdit = { editorTask = it }, onDelete = { deleteTask = it })
            TaskView.DATE -> TaskDateView(controller)
            TaskView.TAG -> TaskTagView(controller, now, expanded, onEdit = { editorTask = it }, onDelete = { deleteTask = it })
        }
    }

    if (createOpen) TaskEditorDialog(task = null, onDismiss = { createOpen = false }) { controller.addTask(it); createOpen = false }
    editorTask?.let { task -> TaskEditorDialog(task = task, onDismiss = { editorTask = null }) { controller.updateTask(it); editorTask = null } }
    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text("删除任务？") },
            text = { Text("“${task.title}”及其内容会被永久删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { controller.deleteTask(task.id); deleteTask = null }) { Text("确认删除", color = Coral) } },
            dismissButton = { TextButton(onClick = { deleteTask = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskListView(
    controller: AppController,
    now: LocalDateTime,
    expanded: MutableMap<String, Boolean>,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    val tasks = controller.data.tasks.sortedWith(compareBy<TaskItem> { it.status }.thenBy { it.dueDate ?: "9999" }.thenBy { it.scheduledTime ?: "99:99" })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (tasks.isEmpty()) item { EmptyState("✓", "还没有任务", "新建一项任务并安排开始时间。") }
        items(tasks, key = { it.id }) { task ->
            ExpandableTaskCard(
                controller = controller,
                now = now,
                task = task,
                expanded = expanded[task.id] == true,
                onExpand = { expanded[task.id] = !(expanded[task.id] == true) },
                onEdit = { onEdit(task) },
                onDelete = { onDelete(task) },
            )
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun ExpandableTaskCard(
    controller: AppController,
    now: LocalDateTime,
    task: TaskItem,
    expanded: Boolean,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val phase = controller.smartEngine.taskPhase(task, now)
    ZCard(color = when (phase) {
        TaskPhase.IN_PROGRESS -> Success.copy(alpha = .07f)
        TaskPhase.OVERDUE -> Coral.copy(alpha = .07f)
        else -> Color.White
    }) {
        TaskRow(task, phase, onToggle = { controller.toggleTask(task.id) }, onTitleClick = onExpand)
        if (expanded) {
            Divider(color = Line)
            Spacer(Modifier.height(12.dp))
            DetailLine("日期与时间", "${task.dueDate ?: "未设置"}  ${task.scheduledTime ?: "未设置"}")
            DetailLine("持续时间", task.durationInput.ifBlank { durationText(task.estimateMinutes) })
            DetailLine("具体内容", task.content.ifBlank { "未填写" })
            DetailLine("标签", task.tags.ifEmpty { listOf("未添加") }.joinToString(" · "))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("编辑", onClick = onEdit, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Coral),
                ) { Text("删除", color = Coral) }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.width(78.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TaskDateView(controller: AppController) {
    val start = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            ZCard(color = Lime.copy(alpha = .38f)) {
                Text("每日时间条", style = MaterialTheme.typography.titleMedium)
                Text("空白代表可用时间，深色区块代表已经安排的日程或待办。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.height(12.dp))
        }
        items((0L..13L).map(start::plusDays)) { date ->
            val tasks = controller.data.tasks.mapNotNull { task -> controller.smartEngine.taskSegmentOn(task, date)?.let { task to it } }
            val events = controller.data.events.filter { it.date == date.toString() }
            ZCard(modifier = Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${tasks.size + events.size} 项", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
                Spacer(Modifier.height(13.dp))
                DayTimeline(tasks, events)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = Muted) }
                }
                (tasks.map { (task, segment) -> "%02d:%02d %s".format(segment.startMinute / 60, segment.startMinute % 60, task.title) } +
                    events.map { "${it.startTime} ${it.title}" }).sorted().take(4).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun DayTimeline(tasks: List<Pair<TaskItem, TaskTimeSegment>>, events: List<CalendarEvent>) {
    Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(PaperDeep)) {
        fun drawSegment(startMinutes: Int, duration: Int, color: Color) {
            val left = size.width * (startMinutes.coerceIn(0, 1440) / 1440f)
            val width = size.width * (duration.coerceIn(1, 1440) / 1440f)
            drawRoundRect(color, Offset(left, 0f), Size(width.coerceAtMost(size.width - left), size.height), CornerRadius(7f, 7f))
        }
        events.forEach { event ->
            val start = timeMinutes(event.startTime)
            val end = timeMinutes(event.endTime)
            drawSegment(start, (end - start).coerceAtLeast(1), Ink.copy(alpha = .72f))
        }
        tasks.forEach { (task, segment) ->
            val color = when (task.status) { TaskStatus.DONE -> Muted; TaskStatus.TODO -> Ink }
            drawSegment(segment.startMinute, segment.durationMinutes, color)
        }
    }
}

private fun timeMinutes(value: String): Int = runCatching {
    val time = LocalTime.parse(value)
    time.hour * 60 + time.minute
}.getOrDefault(0)

private data class TaggedEntry(val title: String, val date: String, val time: String, val tags: List<String>, val task: TaskItem? = null)

@Composable
private fun TaskTagView(
    controller: AppController,
    now: LocalDateTime,
    expanded: MutableMap<String, Boolean>,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    val entries = controller.data.tasks.map { TaggedEntry(it.title, it.dueDate ?: "9999-12-31", it.scheduledTime ?: "99:99", it.tags, task = it) } +
        controller.data.events.map { TaggedEntry(it.title, it.date, it.startTime, it.tags) }
    val labels = entries.flatMap { it.tags }.distinct().sorted() + if (entries.any { it.tags.isEmpty() }) listOf("未添加标签") else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        if (entries.isEmpty()) item { EmptyState("#", "还没有可分类的安排", "为任务或日程添加自定义标签。") }
        labels.forEach { label ->
            val grouped = entries.filter { if (label == "未添加标签") it.tags.isEmpty() else label in it.tags }.sortedWith(compareBy<TaggedEntry> { it.date }.thenBy { it.time })
            item {
                SectionTitle(if (label == "未添加标签") label else "#$label", "${grouped.size} 项")
                Spacer(Modifier.height(8.dp))
            }
            items(grouped, key = { "${label}-${it.task?.id ?: it.title + it.date + it.time}" }) { entry ->
                if (entry.task != null) {
                    ExpandableTaskCard(controller, now, entry.task, expanded[entry.task.id] == true, { expanded[entry.task.id] = !(expanded[entry.task.id] == true) }, { onEdit(entry.task) }, { onDelete(entry.task) })
                } else {
                    ZCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag("日程", Lime)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                Text("${friendlyDate(entry.date)} · ${entry.time}", style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun DiaryScreen(controller: AppController) {
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var createOpen by remember { mutableStateOf(false) }
    var editorDiary by remember { mutableStateOf<DiaryEntry?>(null) }
    var templateOpen by remember { mutableStateOf(false) }
    var deleteDiary by remember { mutableStateOf<DiaryEntry?>(null) }
    val diaryDates = controller.data.diaries.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
    val selectedDiaries = controller.data.diaries.filter { it.date == selectedDate.toString() }.sortedByDescending { it.createdAt }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("DIARY", "日记", "月历与本地记录", action = {
            Row {
                TextButton(onClick = { templateOpen = true }) { Text("模板") }
                TextButton(onClick = { createOpen = true }) { Text("新建") }
            }
        })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ZCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            visibleMonth = visibleMonth.minusMonths(1)
                            selectedDate = visibleMonth.atDay(1)
                        }) { Text("←") }
                        Text(
                            visibleMonth.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        TextButton(onClick = {
                            visibleMonth = visibleMonth.plusMonths(1)
                            selectedDate = visibleMonth.atDay(1)
                        }) { Text("→") }
                    }
                    DiaryMonthCalendar(visibleMonth, selectedDate, diaryDates) { selectedDate = it }
                }
            }
            item {
                SectionTitle(
                    selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    "${selectedDiaries.size} 篇",
                )
            }
            if (selectedDiaries.isEmpty()) {
                item { EmptyState("记", "这一天还没有日记", "点击右上角“新建”，从模板或空白正文开始记录。") }
            }
            items(selectedDiaries, key = { it.id }) { diary ->
                ZCard {
                    Text(diary.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(5.dp))
                    Text("记录于 ${localDiaryTime(diary.createdAt)}", style = MaterialTheme.typography.labelMedium, color = Success)
                    Spacer(Modifier.height(12.dp))
                    Text(diary.content.ifBlank { "（空白正文）" }, style = MaterialTheme.typography.bodyMedium, color = if (diary.content.isBlank()) Muted else Ink)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("编辑", onClick = { editorDiary = diary }, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { deleteDiary = diary },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Coral),
                        ) { Text("删除", color = Coral) }
                    }
                }
            }
        }
    }
    if (createOpen) DiaryEditorDialog(
        diary = null,
        date = selectedDate,
        template = controller.data.preferences.diaryTemplate,
        onDismiss = { createOpen = false },
    ) { controller.addDiary(it); createOpen = false }
    editorDiary?.let { diary -> DiaryEditorDialog(
        diary = diary,
        date = runCatching { LocalDate.parse(diary.date) }.getOrDefault(selectedDate),
        template = controller.data.preferences.diaryTemplate,
        onDismiss = { editorDiary = null },
    ) { controller.updateDiary(it); editorDiary = null } }
    if (templateOpen) DiaryTemplateDialog(
        initial = controller.data.preferences.diaryTemplate,
        onDismiss = { templateOpen = false },
    ) { template ->
        controller.updatePreferences(controller.data.preferences.copy(diaryTemplate = template))
        templateOpen = false
    }
    deleteDiary?.let { diary ->
        AlertDialog(
            onDismissRequest = { deleteDiary = null },
            title = { Text("删除日记？") },
            text = { Text("“${diary.title}”会被永久删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { controller.deleteDiary(diary.id); deleteDiary = null }) { Text("确认删除", color = Coral) } },
            dismissButton = { TextButton(onClick = { deleteDiary = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DiaryMonthCalendar(month: YearMonth, selectedDate: LocalDate, diaryDates: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = (0 until 42).map { index ->
        val day = index - leading + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    Spacer(Modifier.height(4.dp))
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val selected = date == selectedDate
                        val hasDiary = date in diaryDates
                        Surface(
                            modifier = Modifier.fillMaxSize().clickable { onSelect(date) },
                            shape = CircleShape,
                            color = if (selected) PaperDeep else Color.Transparent,
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Ink) else null,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hasDiary) Success else Ink,
                                    fontWeight = if (hasDiary || selected) FontWeight.ExtraBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun localDiaryTime(raw: String): String = runCatching {
    LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
}.getOrDefault(raw.replace('T', ' ').take(16))

@Composable
fun GoalsScreen(controller: AppController) {
    var createOpen by remember { mutableStateOf(false) }
    var editorGoal by remember { mutableStateOf<GoalItem?>(null) }
    var deleteGoal by remember { mutableStateOf<GoalItem?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("DIRECTION", "目标", "独立管理长期方向与子任务", action = { TextButton(onClick = { createOpen = true }) { Text("新建") } })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (controller.data.goals.isEmpty()) item { EmptyState("标", "创建一个长期目标", "目标不会进入日程、待办或逾期提醒。") }
            items(controller.data.goals, key = { it.id }) { goal ->
                val progress = controller.smartEngine.goalProgress(goal)
                ZCard(color = if (goal.isCompleted) Lime.copy(alpha = .25f) else Color.White) {
                    Row(verticalAlignment = Alignment.Top) {
                        GoalCheck(checked = goal.isCompleted, onClick = { controller.toggleGoal(goal.id) })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(goal.title, style = MaterialTheme.typography.titleLarge, color = if (goal.isCompleted) Muted else Ink)
                            Text("截止 ${friendlyDate(goal.deadline)}", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Text("$progress%", style = MaterialTheme.typography.titleLarge)
                    }
                    if (goal.content.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(goal.content, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Ink, trackColor = PaperDeep)
                    if (goal.subtasks.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("子任务", style = MaterialTheme.typography.labelLarge)
                        goal.subtasks.forEach { child ->
                            val effectiveChecked = goal.isCompleted && goal.completionMode == GoalCompletionMode.MANUAL || child.completed
                            Row(
                                Modifier.fillMaxWidth().clickable { controller.toggleGoalSubtask(goal.id, child.id) }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GoalCheck(checked = effectiveChecked, size = 23, onClick = { controller.toggleGoalSubtask(goal.id, child.id) })
                                Spacer(Modifier.width(10.dp))
                                Text(child.title, style = MaterialTheme.typography.bodyMedium, color = if (effectiveChecked) Muted else Ink)
                            }
                        }
                    }
                    if (goal.completionMode == GoalCompletionMode.MANUAL) {
                        Text("取消主目标完成时，子任务会恢复到点击前的真实进度。", style = MaterialTheme.typography.labelMedium, color = Success)
                    }
                    if (goal.completionMode == GoalCompletionMode.AUTOMATIC) {
                        Text("子任务已全部完成；取消任一子任务即可恢复目标。", style = MaterialTheme.typography.labelMedium, color = Success)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("编辑", onClick = { editorGoal = goal }, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { deleteGoal = goal },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Coral),
                        ) { Text("删除", color = Coral) }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
    if (createOpen) GoalEditorDialog(goal = null, onDismiss = { createOpen = false }) { controller.addGoal(it); createOpen = false }
    editorGoal?.let { goal -> GoalEditorDialog(goal = goal, onDismiss = { editorGoal = null }) { controller.updateGoal(it); editorGoal = null } }
    deleteGoal?.let { goal -> AlertDialog(
        onDismissRequest = { deleteGoal = null },
        title = { Text("删除目标？") },
        text = { Text("“${goal.title}”和其中的子任务将被永久删除。") },
        confirmButton = { TextButton(onClick = { controller.deleteGoal(goal.id); deleteGoal = null }) { Text("确认删除", color = Coral) } },
        dismissButton = { TextButton(onClick = { deleteGoal = null }) { Text("取消") } },
    ) }
}

@Composable
private fun GoalCheck(checked: Boolean, size: Int = 28, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (checked) Ink else Color.Transparent)
            .then(if (!checked) Modifier.background(PaperDeep) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (checked) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
}
