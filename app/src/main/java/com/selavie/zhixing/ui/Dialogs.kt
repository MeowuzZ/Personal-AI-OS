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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.selavie.zhixing.data.SmartEngine
import com.selavie.zhixing.model.*
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate

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
fun TaskEditorDialog(task: TaskItem?, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var due by remember(task?.id) { mutableStateOf(task?.dueDate ?: LocalDate.now().toString()) }
    var startTime by remember(task?.id) { mutableStateOf(task?.scheduledTime ?: "09:00") }
    var estimate by remember(task?.id) { mutableStateOf((task?.estimateMinutes ?: 30).toString()) }
    var content by remember(task?.id) { mutableStateOf(task?.content.orEmpty()) }
    var tags by remember(task?.id) { mutableStateOf(task?.tags?.joinToString("，").orEmpty()) }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: Priority.MEDIUM) }
    val duration = estimate.toIntOrNull()
    val formValid = title.isNotBlank() &&
        runCatching { LocalDate.parse(due) }.isSuccess &&
        runCatching { java.time.LocalTime.parse(startTime) }.isSuccess &&
        duration != null && duration in 1..1440
    DialogShell(if (task == null) "新建任务" else "编辑任务", "填写完整时间信息；持续时间上限为 24 小时。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("任务标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(due, { due = it }, Modifier.fillMaxWidth(), label = { Text("日期（YYYY-MM-DD）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(startTime, { startTime = it.take(5) }, Modifier.fillMaxWidth(), label = { Text("开始时间（HH:mm）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            estimate,
            { estimate = it.filter(Char::isDigit).take(4) },
            Modifier.fillMaxWidth(),
            label = { Text("持续时间（分钟，1—1440）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp), label = { Text("具体内容") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("标签（用逗号分隔）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(14.dp))
        Text("优先级", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.values().forEach { item -> FilterChip(selected = priority == item, onClick = { priority = item }, label = { Text(item.label) }) }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            if (task == null) "创建任务" else "保存修改",
            onClick = { onSave((task ?: TaskItem(title = title.trim())).copy(
                title = title.trim(),
                dueDate = due.takeIf { it.isNotBlank() },
                scheduledTime = startTime.takeIf { it.isNotBlank() },
                estimateMinutes = (estimate.toIntOrNull() ?: 30).coerceIn(1, 1440),
                content = content.trim(),
                tags = tags.split(',', '，').map(String::trim).filter(String::isNotBlank).distinct(),
                priority = priority,
            )) },
            modifier = Modifier.fillMaxWidth(),
            enabled = formValid,
        )
    }
}

@Composable
fun NoteEditorDialog(goals: List<GoalItem>, onDismiss: () -> Unit, onSave: (NoteItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var goalId by remember { mutableStateOf<String?>(null) }
    DialogShell("写笔记", "内容自动保存在本机知识库。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("正文") }, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("标签（用逗号分隔）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("关联目标（可选）", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(7.dp))
            goals.forEach { goal ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { goalId = if (goalId == goal.id) null else goal.id }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = goalId == goal.id, onClick = { goalId = if (goalId == goal.id) null else goal.id })
                    Text(goal.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "保存笔记",
            onClick = {
                onSave(NoteItem(
                    title = title.trim().ifBlank { content.trim().take(24) },
                    content = content.trim(),
                    tags = tags.split(',', '，').map(String::trim).filter(String::isNotBlank),
                    goalId = goalId,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() || content.isNotBlank(),
        )
    }
}

@Composable
fun GoalEditorDialog(goal: GoalItem?, onDismiss: () -> Unit, onSave: (GoalItem) -> Unit) {
    var title by remember(goal?.id) { mutableStateOf(goal?.title.orEmpty()) }
    var deadline by remember(goal?.id) { mutableStateOf(goal?.deadline ?: LocalDate.now().plusMonths(3).toString()) }
    var content by remember(goal?.id) { mutableStateOf(goal?.content.orEmpty()) }
    val subtasks = remember(goal?.id) { mutableStateListOf<String>().apply { addAll(goal?.subtasks?.map { it.title } ?: listOf("")) } }
    val goalValid = title.isNotBlank() && runCatching { LocalDate.parse(deadline) }.isSuccess
    DialogShell(if (goal == null) "创建长期目标" else "编辑长期目标", "目标独立于日程与待办；子任务可随时增删。", onDismiss) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("目标标题") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(deadline, { deadline = it }, Modifier.fillMaxWidth(), label = { Text("截止时间（YYYY-MM-DD）") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
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
                val oldByTitle = goal?.subtasks?.associateBy { it.title }.orEmpty()
                onSave((goal ?: GoalItem(title = title.trim(), deadline = deadline)).copy(
                    title = title.trim(),
                    deadline = deadline,
                    content = content.trim(),
                    subtasks = subtasks.map(String::trim).filter(String::isNotBlank).map { text -> oldByTitle[text] ?: GoalSubtask(title = text) },
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = goalValid,
        )
    }
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
                            Text("${task.dueDate} ${task.scheduledTime ?: ""} · ${durationText(task.estimateMinutes)}", style = MaterialTheme.typography.labelMedium, color = Coral)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("稍后处理") } },
    )
}
