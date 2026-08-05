package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selavie.zhixing.model.Priority
import com.selavie.zhixing.model.TaskItem
import com.selavie.zhixing.model.TaskPhase
import com.selavie.zhixing.model.TaskStatus
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier.size(42.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            ) { Box(contentAlignment = Alignment.Center) { Text("←", style = MaterialTheme.typography.titleLarge) } }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = 1.sp)
            Text(title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        }
        action?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, meta: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        meta?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Muted) }
        action?.invoke()
    }
}

@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth()
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        tonalElevation = 0.dp,
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun Tag(text: String, color: Color = PaperDeep, contentColor: Color = Ink) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor) }
}

@Composable
fun TaskRow(
    task: TaskItem,
    phase: TaskPhase,
    onToggle: () -> Unit,
    compact: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 9.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 24.dp else 28.dp)
                .clip(CircleShape)
                .background(if (task.status == TaskStatus.DONE) Ink else Color.Transparent)
                .border(1.5.dp, if (task.status == TaskStatus.DONE) Ink else Muted, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { if (task.status == TaskStatus.DONE) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).then(
                if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick).padding(vertical = 4.dp) else Modifier,
            ),
        ) {
            Text(
                task.title,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (task.status == TaskStatus.DONE) Muted else Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                task.dueDate?.let { Text(friendlyDate(it), style = MaterialTheme.typography.labelMedium, color = if (phase == TaskPhase.OVERDUE) Coral else Muted) }
                task.scheduledTime?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Blue) }
                Text(task.durationInput.ifBlank { durationText(task.estimateMinutes) }, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
        TaskPhaseTag(phase)
    }
}

@Composable
fun TaskPhaseTag(phase: TaskPhase) {
    val (background, foreground) = when (phase) {
        TaskPhase.IN_PROGRESS -> Success.copy(alpha = .15f) to Success
        TaskPhase.OVERDUE -> Coral.copy(alpha = .15f) to Coral
        TaskPhase.DONE -> PaperDeep to Muted
        TaskPhase.UPCOMING -> Blue.copy(alpha = .1f) to Blue
    }
    Tag(phase.label, background, foreground)
}

@Composable
fun PriorityDot(priority: Priority) {
    val color = when (priority) { Priority.HIGH -> Coral; Priority.MEDIUM -> Blue; Priority.LOW -> Success }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun EmptyState(symbol: String, title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(PaperDeep), contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
    ) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Ink),
    ) { Text(text, color = Ink) }
}

fun friendlyDate(value: String): String = runCatching {
    val date = LocalDate.parse(value.take(10))
    when (date) {
        LocalDate.now() -> "今天"
        LocalDate.now().plusDays(1) -> "明天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    }
}.getOrDefault(value.take(10))

fun durationText(minutes: Int): String {
    val safe = minutes.coerceAtLeast(1)
    val days = safe / 1440
    val hours = safe % 1440 / 60
    val rest = safe % 60
    return buildList {
        if (days > 0) add("$days 天")
        if (hours > 0) add("$hours 小时")
        if (rest > 0 || isEmpty()) add("$rest 分钟")
    }.joinToString(" ")
}
