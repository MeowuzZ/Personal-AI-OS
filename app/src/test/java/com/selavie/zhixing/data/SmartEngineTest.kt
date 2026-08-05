package com.selavie.zhixing.data

import com.selavie.zhixing.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SmartEngineTest {
    private val engine = SmartEngine()
    private val today = LocalDate.of(2026, 8, 5)

    @Test
    fun `capture identifies dated task without writing data`() {
        val draft = engine.parseCapture("明天提交数据库作业", today)
        assertEquals(ItemType.TASK, draft.type)
        assertEquals("2026-08-06", draft.dueDate)
        assertTrue(draft.reason.contains("时间"))
    }

    @Test
    fun `assistant answers with personal source citation`() {
        val note = DiaryEntry(title = "安卓 MVP", content = "安卓版本采用离线优先设计，并保留操作确认。")
        val reply = engine.ask("安卓版本采用什么设计？", AppData(diaries = listOf(note)))
        assertTrue(reply.answer.contains("安卓 MVP"))
        assertEquals(note.id, reply.citations.single().id)
    }

    @Test
    fun `natural language duration is converted to minutes`() {
        assertEquals(150, engine.parseDurationMinutes("2小时30分钟"))
        assertEquals(1560, engine.parseDurationMinutes("1天2小时"))
        assertEquals(120, engine.parseDurationMinutes("两小时"))
        assertEquals(90, engine.parseDurationMinutes("1.5小时"))
        assertNull(engine.parseDurationMinutes("稍微一会儿"))
    }

    @Test
    fun `assistant states insufficient evidence when no source matches`() {
        val reply = engine.ask("我的旅行计划是什么？", AppData())
        assertTrue(reply.answer.contains("不足"))
        assertTrue(reply.citations.isEmpty())
    }

    @Test
    fun `assistant creates preview instead of executing task`() {
        val reply = engine.ask("创建任务：明天完成周复盘", AppData())
        assertNotNull(reply.taskDraft)
        assertEquals(ItemType.TASK, reply.taskDraft!!.type)
    }

    @Test
    fun `task phase follows start and duration window`() {
        val task = TaskItem(title = "专注工作", dueDate = "2026-08-05", scheduledTime = "10:00", estimateMinutes = 60)
        assertEquals(TaskPhase.UPCOMING, engine.taskPhase(task, LocalDateTime.of(2026, 8, 5, 9, 59)))
        assertEquals(TaskPhase.IN_PROGRESS, engine.taskPhase(task, LocalDateTime.of(2026, 8, 5, 10, 30)))
        assertEquals(TaskPhase.OVERDUE, engine.taskPhase(task, LocalDateTime.of(2026, 8, 5, 11, 1)))
        assertEquals(TaskPhase.DONE, engine.taskPhase(task.copy(status = TaskStatus.DONE), LocalDateTime.of(2026, 8, 5, 11, 1)))
    }

    @Test
    fun `task phase remains in progress after crossing midnight`() {
        val task = TaskItem(title = "跨夜任务", dueDate = "2026-08-05", scheduledTime = "23:00", estimateMinutes = 180, durationInput = "3小时")
        assertEquals(TaskPhase.IN_PROGRESS, engine.taskPhase(task, LocalDateTime.of(2026, 8, 6, 1, 30)))
        assertEquals(TaskPhase.OVERDUE, engine.taskPhase(task, LocalDateTime.of(2026, 8, 6, 2, 1)))
    }

    @Test
    fun `long task is split into daily timeline segments`() {
        val task = TaskItem(title = "多日任务", dueDate = "2026-08-05", scheduledTime = "23:00", estimateMinutes = 3000, durationInput = "2天2小时")
        assertEquals(60, engine.taskSegmentOn(task, LocalDate.of(2026, 8, 5))?.durationMinutes)
        assertEquals(1440, engine.taskSegmentOn(task, LocalDate.of(2026, 8, 6))?.durationMinutes)
        assertEquals(1440, engine.taskSegmentOn(task, LocalDate.of(2026, 8, 7))?.durationMinutes)
        assertEquals(60, engine.taskSegmentOn(task, LocalDate.of(2026, 8, 8))?.durationMinutes)
        assertNull(engine.taskSegmentOn(task, LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun `goal progress is independent from schedule tasks`() {
        val goal = GoalItem(
            title = "完成应用",
            deadline = "2026-10-01",
            subtasks = listOf(GoalSubtask(title = "设计", completed = true), GoalSubtask(title = "发布")),
        )
        assertEquals(50, engine.goalProgress(goal))
        assertEquals(100, engine.goalProgress(goal.copy(isCompleted = true, completionMode = GoalCompletionMode.MANUAL)))
    }

    @Test
    fun `cancelling manually completed goal restores original subtask progress`() {
        val first = GoalSubtask(title = "已完成的部分", completed = true)
        val second = GoalSubtask(title = "尚未完成")
        val original = GoalItem(title = "长期目标", deadline = "2026-12-31", subtasks = listOf(first, second))

        val manuallyCompleted = engine.toggleGoalMain(original)
        assertTrue(manuallyCompleted.isCompleted)
        assertEquals(listOf(true, false), manuallyCompleted.subtasks.map { it.completed })

        val restored = engine.toggleGoalMain(manuallyCompleted)
        assertFalse(restored.isCompleted)
        assertEquals(listOf(true, false), restored.subtasks.map { it.completed })
    }

    @Test
    fun `all completed subtasks automatically complete parent goal`() {
        val first = GoalSubtask(title = "第一步", completed = true)
        val second = GoalSubtask(title = "第二步")
        val goal = GoalItem(title = "长期目标", deadline = "2026-12-31", subtasks = listOf(first, second))

        val result = engine.toggleGoalSubtask(goal, second.id)
        assertTrue(result.isCompleted)
        assertEquals(GoalCompletionMode.AUTOMATIC, result.completionMode)
    }

    @Test
    fun `daily review adds comfort when planned time exceeds eight hours`() {
        val data = AppData(tasks = listOf(
            TaskItem(title = "长时间工作", dueDate = today.toString(), scheduledTime = "08:00", estimateMinutes = 600),
        ))
        val review = engine.dailyReview(data, today)
        assertTrue(review.contains("超过 8 小时"))
        assertTrue(review.contains("休息"))
    }
}
