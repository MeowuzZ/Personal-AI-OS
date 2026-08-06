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
                TaskItem(title = "整理本周最重要的事项", isTodo = true, urgent = true, important = true, dueDate = today.toString(), endDate = today.plusDays(2).toString(), scheduledTime = null, durationInput = "", content = "只保留真正影响本周结果的事项。", tags = listOf("规划")),
                TaskItem(title = "完成安卓端任务视图", urgent = false, important = true, dueDate = today.toString(), endDate = today.toString(), scheduledTime = futureTime, estimateMinutes = 90, durationInput = "1小时30分钟", content = "完成列表、日期和标签三种视图。", tags = listOf("Android", "开发")),
                TaskItem(title = "阅读离线 AI 检索资料", isTodo = true, urgent = false, important = true, dueDate = today.toString(), endDate = today.plusDays(1).toString(), scheduledTime = null, durationInput = "", content = "整理关键结论到日记。", tags = listOf("学习")),
                TaskItem(title = "明确新版交互范围", status = TaskStatus.DONE, urgent = false, important = false, dueDate = today.toString(), endDate = today.toString(), scheduledTime = "08:30", estimateMinutes = 40, durationInput = "40分钟", content = "按日常使用场景收敛功能。", tags = listOf("产品"), completedAt = LocalDateTime.now().minusHours(1).toString()),
            ),
            diaries = listOf(
                DiaryEntry(date = today.toString(), title = "产品原则：先可信，再聪明", content = "任何 AI 结论都要能追溯到我的数据；创建、更新和删除必须先预览，再由我确认。"),
                DiaryEntry(date = today.minusDays(2).toString(), title = "安卓使用记录", content = "离线优先，今日只展示日程和待办，目标保持独立。"),
            ),
            goals = listOf(goal),
            events = listOf(
                CalendarEvent(title = "深度工作", date = today.toString(), startTime = "10:00", endTime = "11:30", tags = listOf("专注")),
                CalendarEvent(title = "晚间散步", date = today.toString(), startTime = "21:00", endTime = "21:30", tags = listOf("生活")),
            ),
            preferences = UserPreferences(
                name = "探索者",
                genderSymbol = "♂",
                birthday = "2000-08-05",
                motto = "把模糊的愿望，变成今天能完成的行动。",
                onboarded = true,
            ),
        )
    }

    private fun encode(data: AppData): JSONObject = JSONObject().apply {
        put("tasks", JSONArray().apply { data.tasks.forEach { put(taskJson(it)) } })
        put("diaries", JSONArray().apply { data.diaries.forEach { put(diaryJson(it)) } })
        put("goals", JSONArray().apply { data.goals.forEach { put(goalJson(it)) } })
        put("events", JSONArray().apply { data.events.forEach { put(eventJson(it)) } })
        put("audits", JSONArray().apply { data.audits.forEach { put(auditJson(it)) } })
        put("reviews", JSONArray().apply { data.reviews.forEach { put(reviewJson(it)) } })
        put("preferences", JSONObject().apply {
            put("name", data.preferences.name)
            put("genderSymbol", data.preferences.genderSymbol)
            put("birthday", data.preferences.birthday)
            put("motto", data.preferences.motto)
            put("diaryTemplate", data.preferences.diaryTemplate)
            put("includeDiariesInAi", data.preferences.includeDiariesInAi)
            put("includeTasksInAi", data.preferences.includeTasksInAi)
            put("includeGoalsInAi", data.preferences.includeGoalsInAi)
            put("onboarded", data.preferences.onboarded)
        })
        put("exportedAt", LocalDateTime.now().toString())
        put("format", "zhixing-os-v4")
    }

    private fun decode(root: JSONObject): AppData {
        val tasks = root.array("tasks").mapObjects { o ->
            val legacyPriority = o.optString("priority", "MEDIUM")
            val dueDate = o.nullable("dueDate")
            val isTodo = if (o.has("isTodo")) o.optBoolean("isTodo") else o.nullable("scheduledTime") == null
            TaskItem(
            id = o.string("id"),
            title = o.string("title"),
            status = enumValueOr(o.string("status"), TaskStatus.TODO),
            isTodo = isTodo,
            urgent = if (o.has("urgent")) o.optBoolean("urgent") else legacyPriority == "HIGH",
            important = if (o.has("important")) o.optBoolean("important") else legacyPriority != "LOW",
            dueDate = dueDate,
            endDate = o.nullable("endDate") ?: dueDate,
            scheduledTime = if (isTodo) null else o.nullable("scheduledTime"),
            estimateMinutes = o.optInt("estimateMinutes", 30).coerceAtLeast(1),
            durationInput = if (isTodo) "" else o.optString("durationInput").ifBlank { durationLabel(o.optInt("estimateMinutes", 30)) },
            content = o.optString("content"),
            tags = o.array("tags").mapStrings(),
            createdAt = o.optString("createdAt", LocalDateTime.now().toString()),
            completedAt = o.nullable("completedAt"),
        ) }
        val diaries = if (root.optJSONArray("diaries") != null) {
            root.array("diaries").mapObjects(::decodeDiary)
        } else {
            root.array("notes").mapObjects { o ->
                val updatedAt = o.optString("updatedAt", LocalDateTime.now().toString())
                DiaryEntry(
                    id = o.string("id"),
                    date = runCatching { LocalDateTime.parse(updatedAt).toLocalDate().toString() }
                        .getOrElse { LocalDate.now().toString() },
                    title = o.string("title"),
                    content = o.string("content"),
                    createdAt = updatedAt,
                    updatedAt = updatedAt,
                )
            }
        }
        return AppData(
            tasks = tasks,
            diaries = diaries,
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
                urgent = o.optBoolean("urgent", false), important = o.optBoolean("important", true),
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
                diaryTemplate = o.optString("diaryTemplate"),
                includeDiariesInAi = o.optBoolean("includeDiariesInAi", o.optBoolean("includeNotesInAi", true)),
                includeTasksInAi = o.optBoolean("includeTasksInAi", true),
                includeGoalsInAi = o.optBoolean("includeGoalsInAi", true),
                onboarded = o.optBoolean("onboarded", false),
            ) } ?: UserPreferences(),
        )
    }

    private fun taskJson(t: TaskItem) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("status", t.status.name)
        put("isTodo", t.isTodo); put("urgent", t.urgent); put("important", t.important)
        putNullable("dueDate", t.dueDate); putNullable("endDate", t.endDate); putNullable("scheduledTime", t.scheduledTime); put("estimateMinutes", t.estimateMinutes)
        put("durationInput", t.durationInput)
        put("content", t.content); put("tags", JSONArray(t.tags)); put("createdAt", t.createdAt); putNullable("completedAt", t.completedAt)
    }
    private fun diaryJson(d: DiaryEntry) = JSONObject().apply {
        put("id", d.id); put("date", d.date); put("title", d.title); put("content", d.content)
        put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
    }
    private fun decodeDiary(o: JSONObject) = DiaryEntry(
        id = o.string("id"),
        date = o.optString("date", LocalDate.now().toString()),
        title = o.string("title"),
        content = o.string("content"),
        createdAt = o.optString("createdAt", LocalDateTime.now().toString()),
        updatedAt = o.optString("updatedAt", LocalDateTime.now().toString()),
    )
    private fun durationLabel(minutes: Int): String {
        val safe = minutes.coerceAtLeast(1)
        val days = safe / 1440
        val hours = safe % 1440 / 60
        val mins = safe % 60
        return buildList {
            if (days > 0) add("${days}天")
            if (hours > 0) add("${hours}小时")
            if (mins > 0 || isEmpty()) add("${mins}分钟")
        }.joinToString("")
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
        put("urgent", e.urgent); put("important", e.important)
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
