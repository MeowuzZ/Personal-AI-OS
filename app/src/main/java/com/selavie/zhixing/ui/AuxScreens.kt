@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selavie.zhixing.AppController
import com.selavie.zhixing.model.*
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun CalendarScreen(controller: AppController, now: java.time.LocalDateTime, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var eventOpen by remember { mutableStateOf(false) }
    var openedReview by remember { mutableStateOf<ReviewEntry?>(null) }
    val monday = selected.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val events = controller.data.events.filter { it.date == selected.toString() }.sortedBy { it.startTime }
    val tasks = controller.data.tasks.filter { controller.smartEngine.taskOccursOn(it, selected) }.sortedBy {
        controller.smartEngine.taskSegmentOn(it, selected)?.startMinute ?: Int.MAX_VALUE
    }
    val review = controller.data.reviews.firstOrNull { it.date == selected.toString() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TIME", "日历", "查看日程、待办与每日复盘", onBack = onBack, action = {
            TextButton(onClick = { eventOpen = true }) { Text("添加日程") }
        })
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0L..6L).map(monday::plusDays)) { day ->
                val active = day == selected
                Surface(
                    modifier = Modifier.width(58.dp).clickable { selected = day },
                    shape = RoundedCornerShape(17.dp),
                    color = if (active) Ink else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Ink else Line),
                ) {
                    Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.format(DateTimeFormatter.ofPattern("E", Locale.CHINA)), style = MaterialTheme.typography.labelMedium, color = if (active) Lime else Muted)
                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = if (active) Color.White else Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle(if (selected == LocalDate.now()) "今天" else selected.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)), "${events.size + tasks.size} 项") }
            review?.let { saved ->
                item {
                    ZCard(color = Lime.copy(alpha = .42f), onClick = { openedReview = saved }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("每日复盘", style = MaterialTheme.typography.titleMedium)
                                Text("已保存 · 点击回看", style = MaterialTheme.typography.bodyMedium, color = Muted)
                            }
                            Text("查看 →", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (events.isEmpty() && tasks.isEmpty()) item { EmptyState("历", "这一天还没有安排", "保留空白，或添加一个时间块。") }
            items(events, key = { it.id }) { event ->
                ZCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(62.dp)) {
                            Text(event.startTime, style = MaterialTheme.typography.titleMedium, color = Blue)
                            Text(event.endTime, style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Box(Modifier.width(4.dp).height(48.dp).clip(CircleShape).background(Lime))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            if (event.content.isNotBlank()) Text(event.content, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2)
                            if (event.tags.isNotEmpty()) Text(event.tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Blue)
                        }
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                val phase = controller.smartEngine.taskPhase(task, now)
                ZCard(color = when (phase) { TaskPhase.IN_PROGRESS -> Success.copy(alpha = .08f); TaskPhase.OVERDUE -> Coral.copy(alpha = .08f); else -> Color.White }) {
                    TaskRow(task, phase, onToggle = { controller.toggleTask(task.id) }, compact = true)
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
    if (eventOpen) EventEditorDialog(selected, onDismiss = { eventOpen = false }) { controller.addEvent(it); eventOpen = false }
    openedReview?.let { reviewEntry -> AlertDialog(
        onDismissRequest = { openedReview = null },
        title = { Text("${friendlyDate(reviewEntry.date)} · 每日复盘") },
        text = { Text(reviewEntry.content, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { openedReview = null }) { Text("关闭") } },
    ) }
}

@Composable
private fun EventEditorDialog(date: LocalDate, onDismiss: () -> Unit, onSave: (CalendarEvent) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(DateWheelValue.from(date.toString())) }
    var start by remember { mutableStateOf(TimeWheelValue(9, 0)) }
    var end by remember { mutableStateOf(TimeWheelValue(10, 0)) }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var invalidDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加日程") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                DateWheelPicker(selectedDate) { selectedDate = it }
                TimeWheelPicker("开始时间", start) { start = it }
                TimeWheelPicker("结束时间", end) { end = it }
                OutlinedTextField(content, { content = it }, label = { Text("具体内容") })
                OutlinedTextField(tags, { tags = it }, label = { Text("标签") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val validDate = selectedDate.toLocalDateOrNull()
            if (validDate == null) invalidDate = true else onSave(CalendarEvent(
                title = title.trim(), date = validDate.toString(), startTime = start.asText(), endTime = end.asText(), content = content.trim(),
                tags = tags.split(',', '，').map(String::trim).filter(String::isNotBlank),
            ))
        }, enabled = title.isNotBlank()) { Text("确认创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (invalidDate) InvalidDateDialog { invalidDate = false }
}

@Composable
fun ProfileScreen(
    controller: AppController,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onBirthdayChanged: (String) -> Unit,
) {
    val current = controller.data.preferences
    var name by remember(current.name) { mutableStateOf(current.name) }
    var gender by remember(current.genderSymbol) { mutableStateOf(current.genderSymbol) }
    var hasBirthday by remember(current.birthday) { mutableStateOf(current.birthday.isNotBlank()) }
    var birthday by remember(current.birthday) { mutableStateOf(DateWheelValue.from(current.birthday)) }
    var motto by remember(current.motto) { mutableStateOf(current.motto) }
    var saved by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var undoMessage by remember { mutableStateOf<String?>(null) }
    var invalidDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("ME", "个人信息", "资料、隐私与本地数据", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                ZCard(color = Ink) {
                    Text("我的座右铭", style = MaterialTheme.typography.labelMedium, color = Lime, letterSpacing = 1.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(motto.ifBlank { "写下一句提醒自己的话。" }, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            item {
                SectionTitle("基本资料")
                Spacer(Modifier.height(8.dp))
                ZCard {
                    OutlinedTextField(name, { name = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("姓名") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("性别符号（显示在姓名后方）", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "不显示", "♂" to "男性", "♀" to "女性", "⚧" to "其他").forEach { (symbol, label) ->
                            FilterChip(selected = gender == symbol, onClick = { gender = symbol; saved = false }, label = { Text(if (symbol.isBlank()) label else "$symbol $label") })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("设置生日", style = MaterialTheme.typography.labelLarge)
                            Text("年份固定以 2 开头", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        Switch(checked = hasBirthday, onCheckedChange = { hasBirthday = it; saved = false })
                    }
                    if (hasBirthday) {
                        Spacer(Modifier.height(8.dp))
                        DateWheelPicker(birthday) { birthday = it; saved = false }
                    }
                    Text("按本地时间在生日当天上午 8:00 推送祝福；首次保存会请求通知权限。", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(motto, { motto = it; saved = false }, Modifier.fillMaxWidth().heightIn(min = 96.dp), label = { Text("座右铭") }, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(if (saved) "已保存" else "保存个人信息", onClick = {
                        val validBirthday = if (hasBirthday) birthday.toLocalDateOrNull() else null
                        if (hasBirthday && validBirthday == null) {
                            invalidDate = true
                        } else {
                            val birthdayText = validBirthday?.toString().orEmpty()
                            controller.updatePreferences(controller.data.preferences.copy(name = name.trim().ifBlank { "我" }, genderSymbol = gender, birthday = birthdayText, motto = motto.trim()))
                            onBirthdayChanged(birthdayText)
                            saved = true
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !saved)
                }
            }
            item {
                SectionTitle("AI 数据权限")
                Spacer(Modifier.height(8.dp))
                ZCard {
                    SettingsToggle("日记参与检索", "关闭后，助手不会读取日记", controller.data.preferences.includeDiariesInAi) {
                        controller.updatePreferences(controller.data.preferences.copy(includeDiariesInAi = it))
                    }
                    Divider(color = Line)
                    SettingsToggle("任务参与检索", "用于回答完成情况与安排", controller.data.preferences.includeTasksInAi) {
                        controller.updatePreferences(controller.data.preferences.copy(includeTasksInAi = it))
                    }
                    Divider(color = Line)
                    SettingsToggle("目标参与检索", "只用于问答，不进入日程提醒", controller.data.preferences.includeGoalsInAi) {
                        controller.updatePreferences(controller.data.preferences.copy(includeGoalsInAi = it))
                    }
                }
            }
            item {
                SectionTitle("本地数据")
                Spacer(Modifier.height(8.dp))
                ZCard {
                    SettingsAction("导出全部数据", "JSON 格式，包含个人信息、任务、目标与复盘", "导出", onExport)
                    Divider(color = Line)
                    SettingsAction("清空本机数据", "建议先导出备份；清空后不可恢复", "清空", { confirmDelete = true }, danger = true)
                }
            }
            item {
                SectionTitle("执行记录", "最近 ${controller.data.audits.size} 条")
                Spacer(Modifier.height(8.dp))
                ZCard {
                    if (controller.data.audits.isEmpty()) Text("还没有写操作记录", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    controller.data.audits.take(8).forEachIndexed { index, audit ->
                        if (index > 0) Divider(color = Line)
                        Column(Modifier.padding(vertical = 9.dp)) {
                            Text(audit.action, style = MaterialTheme.typography.labelLarge)
                            Text(audit.summary, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1)
                        }
                    }
                    if (controller.data.audits.any { it.undoable }) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("撤销最近一次可撤销操作", onClick = { undoMessage = if (controller.undoLatest()) "已撤销" else "没有可撤销操作" }, modifier = Modifier.fillMaxWidth())
                        undoMessage?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Success, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("清空所有本机数据？") },
        text = { Text("个人信息、任务、日记、目标、日程、复盘和执行记录都会被永久删除。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onBirthdayChanged(""); controller.deleteAllData() }) { Text("确认清空", color = Coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
    if (invalidDate) InvalidDateDialog { invalidDate = false }
}

@Composable
private fun SettingsToggle(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsAction(title: String, detail: String, action: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) Coral else Ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Tag(action, if (danger) Coral.copy(alpha = .12f) else PaperDeep, if (danger) Coral else Ink)
    }
}
