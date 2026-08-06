package com.selavie.zhixing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.selavie.zhixing.data.AppRepository
import com.selavie.zhixing.data.SmartEngine
import com.selavie.zhixing.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AppController(
    private val repository: AppRepository,
    val smartEngine: SmartEngine = SmartEngine(),
) {
    var data by mutableStateOf(repository.load())
        private set

    fun beginEmpty() = commit(AppData(preferences = UserPreferences(onboarded = true)))
    fun beginDemo() = commit(repository.demoData())

    fun addTask(task: TaskItem, audit: Boolean = true) {
        val normalized = normalizeTask(task)
        val entry = AuditEntry(action = "创建任务", summary = normalized.title, beforeJson = "remove-task:${normalized.id}", undoable = true)
        commit(data.copy(tasks = data.tasks + normalized, audits = if (audit) listOf(entry) + data.audits else data.audits))
    }

    fun updateTask(task: TaskItem) {
        val normalized = normalizeTask(task)
        commit(data.copy(
            tasks = data.tasks.map { if (it.id == task.id) normalized else it },
            audits = listOf(AuditEntry(action = "编辑任务", summary = task.title)) + data.audits,
        ))
    }

    fun deleteTask(taskId: String) {
        val task = data.tasks.firstOrNull { it.id == taskId } ?: return
        commit(data.copy(
            tasks = data.tasks.filterNot { it.id == taskId },
            audits = listOf(AuditEntry(action = "删除任务", summary = task.title)) + data.audits,
        ))
    }

    fun addDiary(diary: DiaryEntry) {
        val entry = AuditEntry(action = "创建日记", summary = diary.title, beforeJson = "remove-diary:${diary.id}", undoable = true)
        commit(data.copy(diaries = listOf(diary) + data.diaries, audits = listOf(entry) + data.audits))
    }

    fun updateDiary(diary: DiaryEntry) {
        commit(data.copy(
            diaries = data.diaries.map { if (it.id == diary.id) diary else it },
            audits = listOf(AuditEntry(action = "编辑日记", summary = diary.title)) + data.audits,
        ))
    }

    fun deleteDiary(diaryId: String) {
        val diary = data.diaries.firstOrNull { it.id == diaryId } ?: return
        commit(data.copy(
            diaries = data.diaries.filterNot { it.id == diaryId },
            audits = listOf(AuditEntry(action = "删除日记", summary = diary.title)) + data.audits,
        ))
    }

    fun addGoal(goal: GoalItem) {
        val entry = AuditEntry(action = "创建目标", summary = goal.title, beforeJson = "remove-goal:${goal.id}", undoable = true)
        commit(data.copy(goals = listOf(goal) + data.goals, audits = listOf(entry) + data.audits))
    }

    fun updateGoal(goal: GoalItem) {
        commit(data.copy(
            goals = data.goals.map { if (it.id == goal.id) goal else it },
            audits = listOf(AuditEntry(action = "编辑目标", summary = goal.title)) + data.audits,
        ))
    }

    fun deleteGoal(goalId: String) {
        val goal = data.goals.firstOrNull { it.id == goalId } ?: return
        commit(data.copy(
            goals = data.goals.filterNot { it.id == goalId },
            audits = listOf(AuditEntry(action = "删除目标", summary = goal.title)) + data.audits,
        ))
    }

    fun toggleGoal(goalId: String) {
        val goal = data.goals.firstOrNull { it.id == goalId } ?: return
        val next = smartEngine.toggleGoalMain(goal)
        if (next == goal) return
        commit(data.copy(
            goals = data.goals.map { if (it.id == goalId) next else it },
            audits = listOf(AuditEntry(action = if (next.isCompleted) "完成目标" else "恢复目标", summary = goal.title)) + data.audits,
        ))
    }

    fun toggleGoalSubtask(goalId: String, subtaskId: String) {
        val goal = data.goals.firstOrNull { it.id == goalId } ?: return
        val next = smartEngine.toggleGoalSubtask(goal, subtaskId)
        commit(data.copy(goals = data.goals.map { if (it.id == goalId) next else it }))
    }

    fun addEvent(event: CalendarEvent) {
        val entry = AuditEntry(action = "创建日程", summary = event.title, beforeJson = "remove-event:${event.id}", undoable = true)
        commit(data.copy(events = data.events + event, audits = listOf(entry) + data.audits))
    }

    fun handleCapture(draft: CaptureDraft) {
        when (draft.type) {
            ItemType.TASK -> {
                val date = draft.dueDate ?: LocalDate.now().toString()
                val isTodo = "待办" in draft.rawContent
                addTask(TaskItem(
                    title = draft.title,
                    isTodo = isTodo,
                    dueDate = date,
                    endDate = date,
                    scheduledTime = if (isTodo) null else LocalTime.now().withSecond(0).withNano(0).toString().take(5),
                    durationInput = if (isTodo) "" else "30分钟",
                    content = draft.rawContent,
                ))
            }
            ItemType.NOTE -> addDiary(DiaryEntry(title = draft.title.take(24), content = draft.rawContent))
            ItemType.EVENT -> addEvent(CalendarEvent(
                title = draft.title,
                date = draft.dueDate ?: LocalDate.now().toString(),
                startTime = LocalTime.now().withMinute(0).toString().take(5),
                endTime = LocalTime.now().plusHours(1).withMinute(0).toString().take(5),
                content = draft.rawContent,
            ))
        }
    }

    fun toggleTask(taskId: String) {
        val current = data.tasks.firstOrNull { it.id == taskId } ?: return
        val nextStatus = if (current.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
        val entry = AuditEntry(
            action = if (nextStatus == TaskStatus.DONE) "完成任务" else "恢复任务",
            summary = current.title,
            beforeJson = "task-status:${current.id}:${current.status.name}",
            undoable = true,
        )
        commit(data.copy(
            tasks = data.tasks.map {
                if (it.id == taskId) it.copy(status = nextStatus, completedAt = if (nextStatus == TaskStatus.DONE) LocalDateTime.now().toString() else null) else it
            },
            audits = listOf(entry) + data.audits,
        ))
    }

    fun saveDailyReview(date: LocalDate, content: String) {
        val existing = data.reviews.firstOrNull { it.date == date.toString() }
        val review = ReviewEntry(id = existing?.id ?: java.util.UUID.randomUUID().toString(), date = date.toString(), content = content)
        val entry = AuditEntry(action = if (existing == null) "保存每日复盘" else "更新每日复盘", summary = "${review.date} 每日复盘")
        commit(data.copy(
            reviews = listOf(review) + data.reviews.filterNot { it.date == review.date },
            audits = listOf(entry) + data.audits,
        ))
    }

    fun updatePreferences(value: UserPreferences) = commit(data.copy(preferences = value))

    fun undoLatest(): Boolean {
        val latest = data.audits.firstOrNull { it.undoable && it.beforeJson != null } ?: return false
        val pieces = latest.beforeJson!!.split(":")
        var next = data.copy(audits = data.audits.filterNot { it.id == latest.id })
        next = when (pieces.firstOrNull()) {
            "remove-task" -> next.copy(tasks = next.tasks.filterNot { it.id == pieces.getOrNull(1) })
            "remove-note", "remove-diary" -> next.copy(diaries = next.diaries.filterNot { it.id == pieces.getOrNull(1) })
            "remove-goal" -> next.copy(goals = next.goals.filterNot { it.id == pieces.getOrNull(1) })
            "remove-event" -> next.copy(events = next.events.filterNot { it.id == pieces.getOrNull(1) })
            "task-status" -> next.copy(tasks = next.tasks.map { task ->
                if (task.id == pieces.getOrNull(1)) {
                    val status = runCatching { TaskStatus.valueOf(pieces.getOrNull(2).orEmpty()) }.getOrDefault(task.status)
                    task.copy(status = status, completedAt = if (status == TaskStatus.DONE) task.completedAt ?: LocalDateTime.now().toString() else null)
                } else task
            })
            else -> return false
        }
        commit(next.copy(audits = listOf(AuditEntry(action = "撤销操作", summary = latest.summary)) + next.audits))
        return true
    }

    fun exportJson(): String = repository.export(data)

    fun deleteAllData() {
        repository.clear()
        data = AppData()
    }

    private fun commit(value: AppData) {
        data = value
        repository.save(value)
    }

    private fun normalizeTask(task: TaskItem): TaskItem = task.copy(
        endDate = task.endDate ?: task.dueDate,
        scheduledTime = if (task.isTodo) null else task.scheduledTime,
        estimateMinutes = task.estimateMinutes.coerceAtLeast(1),
        durationInput = if (task.isTodo) "" else task.durationInput,
    )
}
