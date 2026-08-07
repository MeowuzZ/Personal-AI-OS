package com.selavie.zhixing.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class ItemType(val label: String) { TASK("任务"), NOTE("日记"), EVENT("日程") }
enum class TaskStatus { TODO, DONE }
enum class TaskPhase(val label: String) { UPCOMING("待开始"), IN_PROGRESS("进行中"), OVERDUE("逾期"), DONE("已完成") }
enum class TaskQuadrant(val numeral: String, val label: String) {
    IMPORTANT_URGENT("Ⅰ", "重要且紧急"),
    IMPORTANT_NOT_URGENT("Ⅱ", "重要不紧急"),
    NOT_IMPORTANT_URGENT("Ⅲ", "紧急不重要"),
    NOT_IMPORTANT_NOT_URGENT("Ⅳ", "不重要不紧急");

    companion object {
        fun from(urgent: Boolean, important: Boolean): TaskQuadrant = when {
            important && urgent -> IMPORTANT_URGENT
            important -> IMPORTANT_NOT_URGENT
            urgent -> NOT_IMPORTANT_URGENT
            else -> NOT_IMPORTANT_NOT_URGENT
        }
    }
}
enum class GoalCompletionMode { NONE, MANUAL, AUTOMATIC }

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: TaskStatus = TaskStatus.TODO,
    val isTodo: Boolean = false,
    val urgent: Boolean = false,
    val important: Boolean = true,
    val dueDate: String? = LocalDate.now().toString(),
    val endDate: String? = dueDate,
    val scheduledTime: String? = "09:00",
    val estimateMinutes: Int = 30,
    val durationInput: String = "30分钟",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: String = LocalDateTime.now().toString(),
    val completedAt: String? = null,
)

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val title: String,
    val content: String,
    val createdAt: String = LocalDateTime.now().toString(),
    val updatedAt: String = LocalDateTime.now().toString(),
)

data class TaskTimeSegment(
    val taskId: String,
    val date: LocalDate,
    val startMinute: Int,
    val durationMinutes: Int,
)

data class GoalSubtask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val completed: Boolean = false,
)

data class GoalItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val deadline: String,
    val content: String = "",
    val subtasks: List<GoalSubtask> = emptyList(),
    val isCompleted: Boolean = false,
    val completionMode: GoalCompletionMode = GoalCompletionMode.NONE,
    val createdAt: String = LocalDateTime.now().toString(),
)

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "本地",
    val urgent: Boolean = false,
    val important: Boolean = true,
)

data class AuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val summary: String,
    val beforeJson: String? = null,
    val createdAt: String = LocalDateTime.now().toString(),
    val undoable: Boolean = false,
)

data class ReviewEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val content: String,
    val createdAt: String = LocalDateTime.now().toString(),
)

data class UserPreferences(
    val name: String = "我",
    val genderSymbol: String = "",
    val birthday: String = "",
    val motto: String = "让每一天更接近长期目标",
    val diaryTemplate: String = "",
    val includeDiariesInAi: Boolean = true,
    val includeTasksInAi: Boolean = true,
    val includeGoalsInAi: Boolean = true,
    val aiGatewayUrl: String = "",
    val aiAccessToken: String = "",
    val onboarded: Boolean = false,
)

data class AppData(
    val tasks: List<TaskItem> = emptyList(),
    val diaries: List<DiaryEntry> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val audits: List<AuditEntry> = emptyList(),
    val reviews: List<ReviewEntry> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
)

data class CaptureDraft(
    val rawContent: String,
    val type: ItemType,
    val title: String,
    val dueDate: String? = null,
    val reason: String,
    val endDate: String? = dueDate,
    val scheduledTime: String? = null,
    val estimateMinutes: Int = 30,
    val durationInput: String = "30分钟",
    val isTodo: Boolean = scheduledTime == null,
    val urgent: Boolean = false,
    val important: Boolean = true,
    val content: String = rawContent,
    val tags: List<String> = emptyList(),
)

fun CaptureDraft.toTaskItem(): TaskItem {
    val start = dueDate ?: LocalDate.now().toString()
    val safeMinutes = estimateMinutes.coerceAtLeast(1)
    return TaskItem(
        title = title.trim().ifBlank { rawContent.take(42).ifBlank { "未命名任务" } },
        isTodo = isTodo,
        urgent = urgent,
        important = important,
        dueDate = start,
        endDate = endDate ?: start,
        scheduledTime = if (isTodo) null else scheduledTime ?: "09:00",
        estimateMinutes = safeMinutes,
        durationInput = if (isTodo) "" else durationInput.ifBlank { "${safeMinutes}分钟" },
        content = content.ifBlank { rawContent },
        tags = tags,
    )
}

data class Citation(
    val id: String,
    val title: String,
    val type: String,
    val snippet: String,
    val updatedAt: String,
)

data class AssistantReply(
    val answer: String,
    val citations: List<Citation> = emptyList(),
    val taskDraft: CaptureDraft? = null,
)

data class AiOperationResult<T>(
    val value: T,
    val usedRemoteModel: Boolean,
    val notice: String? = null,
)
