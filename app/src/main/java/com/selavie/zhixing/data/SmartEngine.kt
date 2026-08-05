package com.selavie.zhixing.data

import com.selavie.zhixing.model.*
import java.time.*
import java.time.temporal.TemporalAdjusters

class SmartEngine {
    fun parseCapture(text: String, today: LocalDate = LocalDate.now()): CaptureDraft {
        val clean = text.trim()
        val taskWords = listOf("完成", "提交", "交作业", "提醒", "记得", "待办", "要做", "复习", "购买")
        val eventWords = listOf("会议", "上课", "预约", "面试", "日程", "见面", "开会")
        val type = when {
            eventWords.any(clean::contains) -> ItemType.EVENT
            taskWords.any(clean::contains) || containsDateWord(clean) -> ItemType.TASK
            else -> ItemType.NOTE
        }
        val date = extractDate(clean, today)
        val reason = when (type) {
            ItemType.TASK -> if (date != null) "识别到行动词和时间信息" else "识别到可执行的行动表达"
            ItemType.EVENT -> "识别到日程或会面表达"
            ItemType.NOTE -> "未识别到明确行动，建议保存为日记"
        }
        return CaptureDraft(clean, type, clean.take(42), date?.toString(), reason)
    }

    fun taskPhase(task: TaskItem, now: LocalDateTime = LocalDateTime.now()): TaskPhase {
        if (task.status == TaskStatus.DONE) return TaskPhase.DONE
        val date = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return TaskPhase.UPCOMING
        val time = task.scheduledTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (time == null) return if (date.isBefore(now.toLocalDate())) TaskPhase.OVERDUE else TaskPhase.UPCOMING
        val start = LocalDateTime.of(date, time)
        val end = start.plusMinutes(task.estimateMinutes.coerceAtLeast(1).toLong())
        return when {
            now.isBefore(start) -> TaskPhase.UPCOMING
            now.isBefore(end) -> TaskPhase.IN_PROGRESS
            else -> TaskPhase.OVERDUE
        }
    }

    fun parseDurationMinutes(input: String): Int? {
        val normalized = input.trim().lowercase()
            .replace("一个半小时", "1.5小时")
            .replace("半小时", "0.5小时")
            .replace("个", "")
        if (normalized.isBlank()) return null
        normalized.toIntOrNull()?.takeIf { it > 0 }?.let { return it }

        val quantity = "([0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百]+)"
        val unitMinutes = mapOf(
            "周" to 7 * 24 * 60, "星期" to 7 * 24 * 60, "天" to 24 * 60, "日" to 24 * 60,
            "小时" to 60, "时" to 60, "h" to 60, "分钟" to 1, "分" to 1, "min" to 1, "m" to 1,
        )
        var total = 0.0
        var matched = false
        Regex("$quantity\\s*(星期|小时|分钟|min|周|天|日|时|h|分|m)").findAll(normalized).forEach { match ->
            val amount = parseQuantity(match.groupValues[1]) ?: return@forEach
            total += amount * unitMinutes.getValue(match.groupValues[2])
            matched = true
        }
        if (!matched || total <= 0.0 || total > Int.MAX_VALUE) return null
        return total.toInt().coerceAtLeast(1)
    }

    fun taskSegmentOn(task: TaskItem, date: LocalDate): TaskTimeSegment? {
        val startDate = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val startTime = task.scheduledTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        val start = LocalDateTime.of(startDate, startTime)
        val end = start.plusMinutes(task.estimateMinutes.coerceAtLeast(1).toLong())
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val overlapStart = if (start.isAfter(dayStart)) start else dayStart
        val overlapEnd = if (end.isBefore(dayEnd)) end else dayEnd
        if (!overlapStart.isBefore(overlapEnd)) return null
        return TaskTimeSegment(
            taskId = task.id,
            date = date,
            startMinute = Duration.between(dayStart, overlapStart).toMinutes().toInt(),
            durationMinutes = Duration.between(overlapStart, overlapEnd).toMinutes().toInt(),
        )
    }

    fun ask(question: String, data: AppData): AssistantReply {
        val captureIntent = listOf("创建任务", "加个任务", "提醒我", "记得要").firstOrNull(question::contains)
        if (captureIntent != null) {
            val content = question.substringAfter(captureIntent).trim('：', ':', ' ', '，', ',').ifBlank { question }
            val draft = parseCapture(content).copy(type = ItemType.TASK)
            return AssistantReply(answer = "我整理了一份任务草稿。确认前不会写入你的任务列表。", taskDraft = draft)
        }

        val sources = mutableListOf<Citation>()
        if (data.preferences.includeDiariesInAi) data.diaries.forEach { diary ->
            score(question, diary.title + diary.content).takeIf { it > 0 }?.let {
                sources += Citation(diary.id, diary.title, "日记", diary.content.take(88), diary.updatedAt)
            }
        }
        if (data.preferences.includeTasksInAi) data.tasks.forEach { task ->
            score(question, task.title + task.content + task.tags.joinToString()).takeIf { it > 0 }?.let {
                sources += Citation(task.id, task.title, "任务", "${taskPhase(task).label} · ${task.dueDate ?: "未设置"} ${task.scheduledTime ?: ""}", task.createdAt)
            }
        }
        if (data.preferences.includeGoalsInAi) data.goals.forEach { goal ->
            score(question, goal.title + goal.content + goal.subtasks.joinToString { it.title }).takeIf { it > 0 }?.let {
                sources += Citation(goal.id, goal.title, "目标", "截止 ${goal.deadline} · ${goalProgress(goal)}%", goal.createdAt)
            }
        }

        val ranked = sources.distinctBy { it.id }.take(4)
        if (ranked.isEmpty()) return AssistantReply("现有数据不足以回答这个问题。我没有找到可引用的日记、任务或目标；可以先记录相关信息，或换一个更具体的关键词。")
        val facts = ranked.joinToString("\n") { "• ${it.title}：${it.snippet}" }
        return AssistantReply("我在你的个人资料中找到这些相关信息：\n$facts\n\n以上是基于现有记录的整理，不包含未经证实的推测。", ranked)
    }

    fun dailyReview(data: AppData, date: LocalDate): String {
        val dateText = date.toString()
        val dayTasks = data.tasks.filter { it.dueDate == dateText }.sortedBy { it.scheduledTime ?: "99:99" }
        val completed = dayTasks.filter { task ->
            task.completedAt?.take(10) == dateText || (task.status == TaskStatus.DONE && task.completedAt == null)
        }
        val totalMinutes = dayTasks.sumOf { it.estimateMinutes.coerceAtLeast(1) }
        return buildString {
            appendLine("每日复盘 · $dateText")
            appendLine()
            appendLine("今日完成 ${completed.size} / ${dayTasks.size} 项")
            if (completed.isEmpty()) appendLine("• 今天还没有标记完成的待办") else completed.forEach { task ->
                appendLine("• ${task.scheduledTime ?: "--:--"} ${task.title}（${durationText(task.estimateMinutes)}）")
                if (task.content.isNotBlank()) appendLine("  ${task.content}")
            }
            val unfinished = dayTasks.filter { it.status == TaskStatus.TODO }
            if (unfinished.isNotEmpty()) {
                appendLine()
                appendLine("待继续 ${unfinished.size} 项")
                unfinished.forEach { appendLine("• ${it.title} · ${taskPhase(it).label}") }
            }
            appendLine()
            appendLine("今日安排总时长：${durationText(totalMinutes)}")
            if (totalMinutes > 480) {
                appendLine()
                append("今天安排超过 8 小时，已经很辛苦了。完成多少都不否定你的努力，记得给自己留一点休息和恢复的空间。")
            }
        }.trim()
    }

    fun goalProgress(goal: GoalItem): Int {
        if (goal.isCompleted) return 100
        if (goal.subtasks.isEmpty()) return 0
        return (goal.subtasks.count { it.completed } * 100f / goal.subtasks.size).toInt()
    }

    fun toggleGoalMain(goal: GoalItem): GoalItem {
        if (goal.isCompleted && goal.completionMode == GoalCompletionMode.AUTOMATIC) return goal
        return if (goal.isCompleted) {
            goal.copy(isCompleted = false, completionMode = GoalCompletionMode.NONE)
        } else {
            goal.copy(isCompleted = true, completionMode = GoalCompletionMode.MANUAL)
        }
    }

    fun toggleGoalSubtask(goal: GoalItem, subtaskId: String): GoalItem {
        val restoringFromManual = goal.isCompleted && goal.completionMode == GoalCompletionMode.MANUAL
        val children = goal.subtasks.map { child ->
            if (child.id != subtaskId) child
            else if (restoringFromManual) child.copy(completed = false)
            else child.copy(completed = !child.completed)
        }
        val allComplete = children.isNotEmpty() && children.all { it.completed }
        return goal.copy(
            subtasks = children,
            isCompleted = allComplete,
            completionMode = if (allComplete) GoalCompletionMode.AUTOMATIC else GoalCompletionMode.NONE,
        )
    }

    private fun score(query: String, content: String): Int {
        val normalized = query.lowercase().replace(Regex("[？?，,。.!！的了我关于最近哪些什么有没有]"), " ")
        val tokens = normalized.split(Regex("\\s+")).filter { it.length >= 2 }
        return tokens.sumOf { token -> if (content.lowercase().contains(token)) token.length else 0 } +
            if (content.lowercase().contains(normalized.trim()) && normalized.isNotBlank()) 6 else 0
    }

    private fun parseQuantity(raw: String): Double? {
        raw.toDoubleOrNull()?.let { return it }
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (raw == "十") return 10.0
        if ('百' in raw) {
            val parts = raw.split('百', limit = 2)
            val hundreds = parts[0].lastOrNull()?.let(digits::get) ?: 1
            return (hundreds * 100 + (parts.getOrNull(1)?.let { parseQuantity(it)?.toInt() } ?: 0)).toDouble()
        }
        if ('十' in raw) {
            val parts = raw.split('十', limit = 2)
            val tens = parts[0].lastOrNull()?.let(digits::get) ?: 1
            val ones = parts.getOrNull(1)?.lastOrNull()?.let(digits::get) ?: 0
            return (tens * 10 + ones).toDouble()
        }
        return raw.mapNotNull(digits::get).takeIf { it.isNotEmpty() }?.joinToString("")?.toDoubleOrNull()
    }

    private fun durationText(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> "$rest 分钟"
            rest == 0 -> "$hours 小时"
            else -> "$hours 小时 $rest 分钟"
        }
    }

    private fun containsDateWord(text: String) = listOf("今天", "明天", "后天", "下周", "周一", "周二", "周三", "周四", "周五", "周六", "周日").any(text::contains)
    private fun extractDate(text: String, today: LocalDate): LocalDate? = when {
        "后天" in text -> today.plusDays(2)
        "明天" in text -> today.plusDays(1)
        "今天" in text -> today
        "下周" in text -> today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(weekday(text) ?: DayOfWeek.MONDAY))
        weekday(text) != null -> today.with(TemporalAdjusters.nextOrSame(weekday(text)!!))
        else -> Regex("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").find(text)?.destructured?.let { (y, m, d) ->
            runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
    }
    private fun weekday(text: String) = mapOf(
        "周一" to DayOfWeek.MONDAY, "周二" to DayOfWeek.TUESDAY, "周三" to DayOfWeek.WEDNESDAY,
        "周四" to DayOfWeek.THURSDAY, "周五" to DayOfWeek.FRIDAY, "周六" to DayOfWeek.SATURDAY,
        "周日" to DayOfWeek.SUNDAY, "周天" to DayOfWeek.SUNDAY,
    ).entries.firstOrNull { it.key in text }?.value
}
