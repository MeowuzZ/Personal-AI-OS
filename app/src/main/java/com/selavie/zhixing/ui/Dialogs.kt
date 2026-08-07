@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.selavie.zhixing.data.SmartEngine
import com.selavie.zhixing.model.*
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
private fun DialogShell(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding(),
            shape = RoundedCornerShape(26.dp),
            color = Paper,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
                    }
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(PaperDeep).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Text("×", style = MaterialTheme.typography.titleMedium) }
                }
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

@Composable
fun CaptureDialog(engine: SmartEngine, onDismiss: () -> Unit, onConfirm: (CaptureDraft) -> Unit) {
    var raw by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<CaptureDraft?>(null) }
    DialogShell(
        title = if (draft == null) "随手记录" else "确认解析结果",
        subtitle = if (draft == null) "写下想法、任务或日程，保存前不会写入正式模块。" else "这是本次写入预览，你可以调整类型。",
        onDismiss = onDismiss,
    ) {
        if (draft == null) {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
                placeholder = { Text("例如：下周三交数据库作业") },
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("智能整理", onClick = { draft = engine.parseCapture(raw) }, modifier = Modifier.fillMaxWidth(), enabled = raw.isNotBlank())
            Spacer(Modifier.height(10.dp))
            Text("离线识别 · 不会上传任何内容", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            val current = draft!!
            ZCard(color = Color.White) {
                Text("将要创建", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(7.dp))
                Text(current.title, style = MaterialTheme.typography.titleLarge)
                current.dueDate?.let { Text("日期 · ${friendlyDate(it)}", style = MaterialTheme.typography.bodyMedium, color = Blue) }
                Spacer(Modifier.height(12.dp))
                Text(current.reason, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.height(14.dp))
            Text("保存到", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(ItemType.TASK, ItemType.NOTE, ItemType.EVENT).forEach { type ->
                    FilterChip(
                        selected = current.type == type,
                        onClick = { draft = current.copy(type = type) },
                        label = { Text(type.label) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("返回修改", onClick = { draft = null }, modifier = Modifier.weight(1f))
                PrimaryButton("确认写入", onClick = { onConfirm(current) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun TaskEditorDialog(task: TaskItem?, onDismiss: () -> Unit, isNew: Boolean = task == null, onSave: (TaskItem) -> Unit) {
    val engine = remember { SmartEngine() }
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var isTodo by remember(task?.id) { mutableStateOf(task?.isTodo ?: false) }
    var due by remember(task?.id) { mutableStateOf(DateWheelValue.from(task?.dueDate)) }
    var endDate by remember(task?.id) { mutableStateOf(DateWheelValue.from(task?.endDate ?: task?.dueDate)) }
    var startTime by remember(task?.id) { mutableStateOf(TimeWheelValue.from(task?.scheduledTime, java.time.LocalTime.of(9, 0))) }
    var durationInput by remember(task?.id) { mutableStateOf(task?.durationInput?.ifBlank { null } ?: durationText(task?.estimateMinutes ?: 30)) }
    var content by remember(task?.id) { mutableStateOf(task?.content.orEmpty()) }
    var tags by remember(task?.id) { mutableStateOf(task?.tags?.joinToString("，").orEmpty()) }
    var urgent by remember(task?.id) { mutableStateOf(task?.urgent ?: false) }
    var important by remember(task?.id) { mutableStateOf(task?.important ?: true) }
    var invalidDate by remember { mutableStateOf(false) }
    var invalidRange by remember { mutableStateOf(false) }
    var overdueTask by remember { mutableStateOf<TaskItem?>(null) }
    val duration = engine.parseDurationMinutes(durationInput)
    val formValid = title.isNotBlank() && (isTodo || duration != null)
    fun result(date: LocalDate, end: LocalDate): TaskItem = (task ?: TaskItem(title = title.trim())).copy(
        title = title.trim(),
        isTodo = isTodo,
        urgent = urgent,
        important = important,
        dueDate = date.toString(),
        endDate = end.toString(),
        scheduledTime = if (isTodo) null else startTime.asText(),
        estimateMinutes = if (isTodo) 30 else duration ?: 30,
        durationInput = if (isTodo) "" else durationInput.trim(),
        content = content.trim(),
        tags = tags.split(',', '，').map(String::trim).filter(String::isNotBlank).distinct(),
    )
    DialogShell(if (isNew) "新建任务" else "编辑任务", "勾选待办后只需设置日期范围；未勾选则作为占用时间条的日程。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("任务标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { isTodo = !isTodo }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isTodo, onCheckedChange = { isTodo = it })
            Column(Modifier.weight(1f)) {
                Text("待办", style = MaterialTheme.typography.titleMedium)
                Text("无需具体时间，只设置开始与结束日期", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        DateWheelPicker(due, if (isTodo) "开始日期" else "日期") { due = it }
        if (isTodo) {
            Spacer(Modifier.height(14.dp))
            DateWheelPicker(endDate, "结束日期") { endDate = it }
        } else {
            Spacer(Modifier.height(14.dp))
            TimeWheelPicker("开始时间", startTime) { startTime = it }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                durationInput,
                { durationInput = it },
                Modifier.fillMaxWidth(),
                label = { Text("持续时间") },
                supportingText = { Text(if (duration == null) "请输入有效时长，如 1天2小时" else "将按 ${durationText(duration)} 计算，可自动延续至后续日期") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("具体内容") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("标签（用逗号分隔）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        Text("选择紧急程度", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "紧急", false to "不紧急").forEach { (value, label) ->
                FilterChip(selected = urgent == value, onClick = { urgent = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("选择重要程度", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "重要", false to "不重要").forEach { (value, label) ->
                FilterChip(selected = important == value, onClick = { important = value }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            if (isNew) "创建任务" else "保存修改",
            onClick = {
                val date = due.toLocalDateOrNull()
                val end = if (isTodo) endDate.toLocalDateOrNull() else date
                if (date == null || end == null) {
                    invalidDate = true
                } else if (end.isBefore(date)) {
                    invalidRange = true
                } else {
                    val value = result(date, end)
                    if (engine.taskPhase(value, LocalDateTime.now()) == TaskPhase.OVERDUE) overdueTask = value
                    else onSave(value)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = formValid,
        )
    }
    if (invalidDate) InvalidDateDialog { invalidDate = false }
    if (invalidRange) AlertMessageDialog("日期范围无效", "结束日期不能早于开始日期。") { invalidRange = false }
    overdueTask?.let { value ->
        AlertDialog(
            onDismissRequest = { overdueTask = null },
            title = { Text("任务已逾期") },
            text = { Text("所选任务已经超过预计结束时间，保存后会立即显示为逾期。") },
            confirmButton = { TextButton(onClick = { overdueTask = null; onSave(value) }) { Text("仍然保存") } },
            dismissButton = { TextButton(onClick = { overdueTask = null }) { Text("返回修改") } },
        )
    }
}

@Composable
fun DiaryEditorDialog(
    diary: DiaryEntry?,
    date: LocalDate,
    template: String,
    onDismiss: () -> Unit,
    onSave: (DiaryEntry) -> Unit,
) {
    var title by remember(diary?.id, date) { mutableStateOf(diary?.title.orEmpty()) }
    var content by remember(diary?.id, date) { mutableStateOf(diary?.content ?: template) }
    DialogShell(if (diary == null) "写日记" else "编辑日记", "记录时间会自动读取本机时间，标题可自由编辑。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("正文") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "保存日记",
            onClick = {
                val now = LocalDateTime.now().toString()
                onSave((diary ?: DiaryEntry(date = date.toString(), title = title.trim(), content = content.trim(), createdAt = now)).copy(
                    date = date.toString(),
                    title = title.trim(),
                    content = content.trim(),
                    updatedAt = now,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank(),
        )
    }
}

@Composable
fun DiaryTemplateDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var template by remember(initial) { mutableStateOf(initial) }
    DialogShell("日记文本模板", "新建日记时会自动带入，可继续编辑；清空后将新建空白正文。", onDismiss) {
        OutlinedTextField(template, { template = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("模板正文") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(18.dp))
        PrimaryButton("保存模板", onClick = { onSave(template) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun GoalEditorDialog(goal: GoalItem?, onDismiss: () -> Unit, onSave: (GoalItem) -> Unit) {
    var title by remember(goal?.id) { mutableStateOf(goal?.title.orEmpty()) }
    var deadline by remember(goal?.id) { mutableStateOf(DateWheelValue.from(goal?.deadline, LocalDate.now().plusMonths(3))) }
    var content by remember(goal?.id) { mutableStateOf(goal?.content.orEmpty()) }
    val subtasks = remember(goal?.id) { mutableStateListOf<String>().apply { addAll(goal?.subtasks?.map { it.title } ?: listOf("")) } }
    var invalidDate by remember { mutableStateOf(false) }
    val goalValid = title.isNotBlank()
    DialogShell(if (goal == null) "创建长期目标" else "编辑长期目标", "目标独立于日程与待办；子任务可随时增删。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("目标标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        DateWheelPicker(deadline) { deadline = it }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("具体内容") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("子任务列表", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { subtasks.add("") }) { Text("+ 添加") }
        }
        subtasks.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { subtasks[index] = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("子任务 ${index + 1}") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                TextButton(onClick = { subtasks.removeAt(index) }) { Text("删除", color = Coral) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            if (goal == null) "创建目标" else "保存修改",
            onClick = {
                val validDeadline = deadline.toLocalDateOrNull()
                if (validDeadline == null) {
                    invalidDate = true
                    return@PrimaryButton
                }
                val oldByTitle = goal?.subtasks?.associateBy { it.title }.orEmpty()
                onSave((goal ?: GoalItem(title = title.trim(), deadline = validDeadline.toString())).copy(
                    title = title.trim(),
                    deadline = validDeadline.toString(),
                    content = content.trim(),
                    subtasks = subtasks.map(String::trim).filter(String::isNotBlank).map { text -> oldByTitle[text] ?: GoalSubtask(title = text) },
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = goalValid,
        )
    }
    if (invalidDate) InvalidDateDialog { invalidDate = false }
}

@Composable
fun OverdueReminderDialog(
    tasks: List<TaskItem>,
    onComplete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("有 ${tasks.size} 项任务已逾期") },
        text = {
            Column {
                Text("这些任务已超过预定结束时间。若已经完成，可直接勾选并停止后续提醒。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(12.dp))
                tasks.take(8).forEach { task ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onComplete(task.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = false, onCheckedChange = { onComplete(task.id) })
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text("${task.dueDate} ${task.scheduledTime ?: ""} · ${task.durationInput.ifBlank { durationText(task.estimateMinutes) }}", style = MaterialTheme.typography.labelMedium, color = Coral)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("稍后处理") } },
    )
}
