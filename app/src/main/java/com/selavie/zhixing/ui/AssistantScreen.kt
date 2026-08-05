package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selavie.zhixing.AppController
import com.selavie.zhixing.model.AssistantReply
import com.selavie.zhixing.model.CaptureDraft
import com.selavie.zhixing.model.Citation
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate

private data class ChatMessage(val text: String, val fromUser: Boolean, val reply: AssistantReply? = null)

@Composable
fun AssistantScreen(
    controller: AppController,
    requestedReviewDate: String?,
    onReviewOpened: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("你好，我是知行助手。我只根据你允许的数据回答；没有证据时会直接说明。你也可以让我先生成任务草稿。", false),
        )
    }
    var citation by remember { mutableStateOf<Citation?>(null) }
    var reviewDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(requestedReviewDate) {
        requestedReviewDate?.let {
            reviewDate = runCatching { LocalDate.parse(it) }.getOrDefault(LocalDate.now())
            onReviewOpened()
        }
    }

    fun submit(value: String = input) {
        val question = value.trim()
        if (question.isBlank()) return
        messages += ChatMessage(question, true)
        val reply = controller.smartEngine.ask(question, controller.data)
        messages += ChatMessage(reply.answer, false, reply)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("TRUSTED AI", "助手", "回答有来源，写入先确认", action = {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                Text("AI", fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        })
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ZCard(color = Lime.copy(alpha = .45f), onClick = { reviewDate = LocalDate.now() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("每日复盘", style = MaterialTheme.typography.titleMedium)
                            Text("罗列当天完成待办，整理为可编辑总结", style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        Text("生成 →", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("最近关于安卓记录了什么？", "我有哪些未完成任务？", "创建任务：明天完成周复盘")) { suggestion ->
                        SuggestionChip(onClick = { submit(suggestion) }, label = { Text(suggestion) })
                    }
                }
            }
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    onCitation = { citation = it },
                    onConfirmDraft = { draft ->
                        controller.handleCapture(draft)
                        messages += ChatMessage("已按你的确认创建任务，并记录到执行日志中。", false)
                    },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("询问我的任务、笔记或目标…") },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (input.isBlank()) PaperDeep else Ink).clickable(enabled = input.isNotBlank()) { submit() },
                    contentAlignment = Alignment.Center,
                ) { Text("↑", color = if (input.isBlank()) Muted else Color.White, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
    citation?.let { CitationDialog(it, onDismiss = { citation = null }) }
    reviewDate?.let { date -> DailyReviewDialog(controller, date, onDismiss = { reviewDate = null }) }
}

@Composable
private fun DailyReviewDialog(controller: AppController, date: LocalDate, onDismiss: () -> Unit) {
    val existing = controller.data.reviews.firstOrNull { it.date == date.toString() }
    var content by remember(date, existing?.id) { mutableStateOf(existing?.content ?: controller.smartEngine.dailyReview(controller.data, date)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${friendlyDate(date.toString())} · 每日复盘") },
        text = {
            Column {
                Text("基于当天任务生成，可修改后保存；超过 8 小时的安排会附加休息提醒。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 350.dp),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = { controller.saveDailyReview(date, content); onDismiss() }) { Text(if (existing == null) "保存复盘" else "更新复盘") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCitation: (Citation) -> Unit,
    onConfirmDraft: (CaptureDraft) -> Unit,
) {
    val alignment = if (message.fromUser) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            color = if (message.fromUser) Ink else Color.White,
            shape = if (message.fromUser) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp),
            border = if (message.fromUser) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.fromUser) Color.White else Ink,
                modifier = Modifier.padding(15.dp),
            )
        }
        val reply = message.reply
        if (reply != null && reply.citations.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text("来源 ${reply.citations.size}", style = MaterialTheme.typography.labelMedium, color = Muted)
            Spacer(Modifier.height(5.dp))
            Column(Modifier.widthIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reply.citations.forEachIndexed { index, source ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onCitation(source) },
                        color = Lime.copy(alpha = .38f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(source.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text("${source.type} · ${friendlyDate(source.updatedAt)}", style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            Text("↗", color = Muted)
                        }
                    }
                }
            }
        }
        if (reply?.taskDraft != null) {
            Spacer(Modifier.height(8.dp))
            ZCard(modifier = Modifier.widthIn(max = 330.dp), color = Lime.copy(alpha = .45f)) {
                Tag("操作预览", Ink, Color.White)
                Spacer(Modifier.height(10.dp))
                Text(reply.taskDraft.title, style = MaterialTheme.typography.titleMedium)
                reply.taskDraft.dueDate?.let { Text("截止 ${friendlyDate(it)}", style = MaterialTheme.typography.bodyMedium, color = Muted) }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("确认创建任务", onClick = { onConfirmDraft(reply.taskDraft) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp))
                Text("只有点击确认后才会写入", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun CitationDialog(citation: Citation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(citation.title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(citation.type, Lime)
                    Tag("更新 ${friendlyDate(citation.updatedAt)}")
                }
                Spacer(Modifier.height(14.dp))
                Text(citation.snippet, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
                Text("这是回答所使用的原始片段。知行不会展示不存在于个人资料中的事实。", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
