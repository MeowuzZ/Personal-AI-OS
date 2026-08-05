package com.selavie.zhixing.data

import android.content.Context
import com.selavie.zhixing.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AppRepository(context: Context) {
    private val preferences = context.getSharedPreferences("zhixing_store", Context.MODE_PRIVATE)
    private val key = "app_data_v1"

    fun load(): AppData {
        val raw = preferences.getString(key, null) ?: return AppData()
        return runCatching { decode(JSONObject(raw)) }.getOrElse { AppData() }
    }

    fun save(data: AppData) {
        preferences.edit().putString(key, encode(data).toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(key).apply()
    }

    fun export(data: AppData): String = encode(data).toString(2)

    fun demoData(): AppData {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val pastTime = now.minusHours(2).format(DateTimeFormatter.ofPattern("HH:mm"))
        val futureTime = now.plusHours(2).format(DateTimeFormatter.ofPattern("HH:mm"))
        val goal = GoalItem(
            title = "完成 Personal AI OS 安卓版",
            deadline = today.plusMonths(2).toString(),
            content = "做一个真正能每天使用的个人信息中枢，并持续根据真实使用体验迭代。",
            subtasks = listOf(
                GoalSubtask(title = "完成核心信息架构", completed = true),
                GoalSubtask(title = "连续使用一周并记录问题"),
                GoalSubtask(title = "完成第一个稳定版本"),
            ),
        )
        return AppData(
            tasks = listOf(
                TaskItem(title = "整理本周最重要的事项", priority = Priority.HIGH, dueDate = today.toString(), scheduledTime = pastTime, estimateMinutes = 30, content = "只保留真正影响本周结果的事项。", tags = listOf("规划")),
                TaskItem(title = "完成安卓端任务视图", priority = Priority.HIGH, dueDate = today.toString(), scheduledTime = futureTime, estimateMinutes = 90, content = "完成列表、日期和标签三种视图。", tags = listOf("Android", "开发")),
                TaskItem(title = "阅读离线 AI 检索资料", priority = Priority.MEDIUM, dueDate = today.plusDays(1).toString(), scheduledTime = "20:00", estimateMinutes = 45, content = "整理关键结论到知识库。", tags = listOf("学习")),
                TaskItem(title = "明确新版交互范围", status = TaskStatus.DONE, priority = Priority.MEDIUM, dueDate = today.toString(), scheduledTime = "08:30", estimateMinutes = 40, content = "按日常使用场景收敛功能。", tags = listOf("产品"), completedAt = LocalDateTime.now().minusHours(1).toString()),
            ),
            notes = listOf(
                NoteItem(title = "产品原则：先可信，再聪明", content = "任何 AI 结论都要能追溯到我的数据；创建、更新和删除必须先预览，再由我确认。", tags = listOf("AI", "产品原则")),
                NoteItem(title = "安卓使用原则", content = "离线优先，Today 只展示今天的日程和待办，目标保持独立。", tags = listOf("Android", "MVP")),
            ),
            goals = listOf(goal),
            events = listOf(
                CalendarEvent(title = "深度工作", date = today.toString(), startTime = "10:00", endTime = "11:30", tags = listOf("专注")),
                CalendarEvent(title = "晚间散步", date = today.toString(), startTime = "21:00", endTime = "21:30", tags = listOf("生活")),
            ),
            preferences = UserPreferences(
                name = "探索者",
                genderSymbol = "♂",
                birthday = "1998-08-05",
                motto = "把模糊的愿望，变成今天能完成的行动。",
                onboarded = true,
            ),
        )
    }

    private fun encode(data: AppData): JSONObject = JSONObject().apply {
        put("tasks", JSONArray().apply { data.tasks.forEach { put(taskJson(it)) } })
        put("notes", JSONArray().apply { data.notes.forEach { put(noteJson(it)) } })
        put("goals", JSONArray().apply { data.goals.forEach { put(goalJson(it)) } })
        put("events", JSONArray().apply { data.events.forEach { put(eventJson(it)) } })
        put("audits", JSONArray().apply { data.audits.forEach { put(auditJson(it)) } })
        put("reviews", JSONArray().apply { data.reviews.forEach { put(reviewJson(it)) } })
        put("preferences", JSONObject().apply {
            put("name", data.preferences.name)
            put("genderSymbol", data.preferences.genderSymbol)
            put("birthday", data.preferences.birthday)
            put("motto", data.preferences.motto)
            put("includeNotesInAi", data.preferences.includeNotesInAi)
            put("includeTasksInAi", data.preferences.includeTasksInAi)
            put("includeGoalsInAi", data.preferences.includeGoalsInAi)
            put("onboarded", data.preferences.onboarded)
        })
        put("exportedAt", LocalDateTime.now().toString())
        put("format", "zhixing-os-v2")
    }

    private fun decode(root: JSONObject): AppData {
        val tasks = root.array("tasks").mapObjects { o -> TaskItem(
            id = o.string("id"),
            title = o.string("title"),
            status = enumValueOr(o.string("status"), TaskStatus.TODO),
            priority = enumValueOr(o.string("priority"), Priority.MEDIUM),
            dueDate = o.nullable("dueDate"),
            scheduledTime = o.nullable("scheduledTime"),
            estimateMinutes = o.optInt("estimateMinutes", 30).coerceIn(1, 1440),
            content = o.optString("content"),
            tags = o.array("tags").mapStrings(),
            createdAt = o.optString("createdAt", LocalDateTime.now().toString()),
            completedAt = o.nullable("completedAt"),
        ) }
        return AppData(
            tasks = tasks,
            notes = root.array("notes").mapObjects { o -> NoteItem(
                id = o.string("id"), title = o.string("title"), content = o.string("content"),
                tags = o.array("tags").mapStrings(), goalId = o.nullable("goalId"),
                updatedAt = o.optString("updatedAt", LocalDateTime.now().toString()),
            ) },
            goals = root.array("goals").mapObjects { o -> GoalItem(
                id = o.string("id"),
                title = o.string("title"),
                deadline = o.string("deadline"),
                content = o.optString("content", o.optString("motivation")),
                subtasks = o.array("subtasks").mapObjects { child -> GoalSubtask(
                    id = child.string("id"), title = child.string("title"), completed = child.optBoolean("completed"),
                ) },
                isCompleted = o.optBoolean("isCompleted"),
                completionMode = enumValueOr(o.optString("completionMode"), GoalCompletionMode.NONE),
                createdAt = o.optString("createdAt", LocalDateTime.now().toString()),
            ) },
            events = root.array("events").mapObjects { o -> CalendarEvent(
                id = o.string("id"), title = o.string("title"), date = o.string("date"),
                startTime = o.string("startTime"), endTime = o.string("endTime"),
                content = o.optString("content"), tags = o.array("tags").mapStrings(), source = o.optString("source", "本地"),
            ) },
            audits = root.array("audits").mapObjects { o -> AuditEntry(
                id = o.string("id"), action = o.string("action"), summary = o.string("summary"),
                beforeJson = o.nullable("beforeJson"), createdAt = o.optString("createdAt", LocalDateTime.now().toString()), undoable = o.optBoolean("undoable"),
            ) },
            reviews = root.array("reviews").mapObjects { o -> ReviewEntry(
                id = o.string("id"), date = o.optString("date", o.optString("weekOf", LocalDate.now().toString())),
                content = o.string("content"), createdAt = o.optString("createdAt", LocalDateTime.now().toString()),
            ) },
            preferences = root.optJSONObject("preferences")?.let { o -> UserPreferences(
                name = o.optString("name", "我"),
                genderSymbol = o.optString("genderSymbol"),
                birthday = o.optString("birthday"),
                motto = o.optString("motto", o.optString("primaryFocus", "让每一天更接近长期目标")),
                includeNotesInAi = o.optBoolean("includeNotesInAi", true),
                includeTasksInAi = o.optBoolean("includeTasksInAi", true),
                includeGoalsInAi = o.optBoolean("includeGoalsInAi", true),
                onboarded = o.optBoolean("onboarded", false),
            ) } ?: UserPreferences(),
        )
    }

    private fun taskJson(t: TaskItem) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("status", t.status.name); put("priority", t.priority.name)
        putNullable("dueDate", t.dueDate); putNullable("scheduledTime", t.scheduledTime); put("estimateMinutes", t.estimateMinutes)
        put("content", t.content); put("tags", JSONArray(t.tags)); put("createdAt", t.createdAt); putNullable("completedAt", t.completedAt)
    }
    private fun noteJson(n: NoteItem) = JSONObject().apply {
        put("id", n.id); put("title", n.title); put("content", n.content); put("tags", JSONArray(n.tags))
        putNullable("goalId", n.goalId); put("updatedAt", n.updatedAt)
    }
    private fun goalJson(g: GoalItem) = JSONObject().apply {
        put("id", g.id); put("title", g.title); put("deadline", g.deadline); put("content", g.content)
        put("subtasks", JSONArray().apply { g.subtasks.forEach { child -> put(JSONObject().apply {
            put("id", child.id); put("title", child.title); put("completed", child.completed)
        }) } })
        put("isCompleted", g.isCompleted); put("completionMode", g.completionMode.name); put("createdAt", g.createdAt)
    }
    private fun eventJson(e: CalendarEvent) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("date", e.date); put("startTime", e.startTime); put("endTime", e.endTime)
        put("content", e.content); put("tags", JSONArray(e.tags)); put("source", e.source)
    }
    private fun auditJson(a: AuditEntry) = JSONObject().apply {
        put("id", a.id); put("action", a.action); put("summary", a.summary); putNullable("beforeJson", a.beforeJson)
        put("createdAt", a.createdAt); put("undoable", a.undoable)
    }
    private fun reviewJson(r: ReviewEntry) = JSONObject().apply {
        put("id", r.id); put("date", r.date); put("content", r.content); put("createdAt", r.createdAt)
    }

    private fun JSONObject.array(name: String) = optJSONArray(name) ?: JSONArray()
    private fun JSONObject.string(name: String) = optString(name, "")
    private fun JSONObject.nullable(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    private fun JSONObject.putNullable(name: String, value: Any?) { put(name, value ?: JSONObject.NULL) }
    private fun JSONArray.mapStrings() = (0 until length()).map { optString(it) }
    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).mapNotNull { optJSONObject(it)?.let(block) }
    private inline fun <reified T : Enum<T>> enumValueOr(raw: String, fallback: T) = runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}
