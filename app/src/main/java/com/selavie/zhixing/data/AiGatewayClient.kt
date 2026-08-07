package com.selavie.zhixing.data

import com.selavie.zhixing.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

interface AiGatewayClient {
    suspend fun ask(question: String, data: AppData): AssistantReply
    suspend fun review(data: AppData, date: LocalDate, localReview: String): String
}

class HttpAiGatewayClient : AiGatewayClient {
    override suspend fun ask(question: String, data: AppData): AssistantReply {
        val response = post(
            preferences = data.preferences,
            payload = JSONObject().apply {
                put("mode", "chat")
                put("message", question)
                put("localDateTime", LocalDateTime.now().toString())
                put("timeZone", ZoneId.systemDefault().id)
                put("context", contextJson(data))
            },
        )
        return parseReply(response, data, question)
    }

    override suspend fun review(data: AppData, date: LocalDate, localReview: String): String {
        val response = post(
            preferences = data.preferences,
            payload = JSONObject().apply {
                put("mode", "review")
                put("message", "请分析并完善这份每日复盘")
                put("reviewDate", date.toString())
                put("localDateTime", LocalDateTime.now().toString())
                put("timeZone", ZoneId.systemDefault().id)
                put("localReview", localReview)
                put("context", contextJson(data, date))
            },
        )
        return response.optString("answer").trim().ifBlank {
            throw AiGatewayException("模型没有返回复盘内容")
        }
    }

    private suspend fun post(preferences: UserPreferences, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = preferences.aiGatewayUrl.trim()
        if (endpoint.isBlank()) throw AiGatewayException("尚未配置 AI 网关")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            preferences.aiAccessToken.trim().takeIf(String::isNotBlank)?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw AiGatewayException(detail.ifBlank { "AI 网关返回 HTTP $status" })
            }
            runCatching { JSONObject(body) }.getOrElse { throw AiGatewayException("AI 网关返回了无法识别的数据") }
        } catch (error: AiGatewayException) {
            throw error
        } catch (error: Exception) {
            throw AiGatewayException(error.message?.take(100) ?: "无法连接 AI 网关")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReply(response: JSONObject, data: AppData, rawQuestion: String): AssistantReply {
        val answer = response.optString("answer").trim().ifBlank { "模型没有返回可显示的回答。" }
        val citationIds = response.optJSONArray("citationIds") ?: JSONArray()
        val sources = sourceIndex(data)
        val citations = (0 until citationIds.length())
            .mapNotNull { sources[citationIds.optString(it)] }
            .distinctBy(Citation::id)
            .take(4)
        val draftJson = response.optJSONObject("taskDraft")
        val taskDraft = draftJson?.takeIf { it.optBoolean("isTask") }?.let { draft ->
            val title = draft.optString("title").trim()
            if (title.isBlank()) null else {
                val isTodo = draft.optBoolean("isTodo", draft.nullableString("startTime") == null)
                val minutes = draft.optInt("durationMinutes", 30).coerceAtLeast(1)
                val startDate = validDateOrNull(draft.nullableString("startDate")) ?: LocalDate.now().toString()
                CaptureDraft(
                    rawContent = rawQuestion,
                    type = ItemType.TASK,
                    title = title,
                    dueDate = startDate,
                    reason = draft.optString("reason", "由真实模型从自然语言整理"),
                    endDate = validDateOrNull(draft.nullableString("endDate")) ?: startDate,
                    scheduledTime = if (isTodo) null else validTimeOrNull(draft.nullableString("startTime")) ?: "09:00",
                    estimateMinutes = minutes,
                    durationInput = draft.optString("durationInput").ifBlank { "${minutes}分钟" },
                    isTodo = isTodo,
                    urgent = draft.optBoolean("urgent"),
                    important = draft.optBoolean("important", true),
                    content = draft.optString("content").ifBlank { rawQuestion },
                    tags = draft.optJSONArray("tags").toStringList(),
                )
            }
        }
        return AssistantReply(answer = answer, citations = citations, taskDraft = taskDraft)
    }

    private fun contextJson(data: AppData, reviewDate: LocalDate? = null): JSONObject = JSONObject().apply {
        put("profile", JSONObject().apply {
            put("name", data.preferences.name)
            put("motto", data.preferences.motto)
        })
        put("records", JSONArray().apply {
            if (data.preferences.includeTasksInAi) {
                data.tasks.filter { reviewDate == null || SmartEngine().taskOccursOn(it, reviewDate) }.forEach { task ->
                    put(JSONObject().apply {
                        put("id", task.id)
                        put("type", if (task.isTodo) "待办" else "日程")
                        put("title", task.title)
                        put("content", task.content)
                        put("status", task.status.name)
                        put("startDate", task.dueDate)
                        put("endDate", task.endDate)
                        put("startTime", task.scheduledTime)
                        put("durationMinutes", task.estimateMinutes)
                        put("urgent", task.urgent)
                        put("important", task.important)
                        put("tags", JSONArray(task.tags))
                    })
                }
                data.events.filter { reviewDate == null || it.date == reviewDate.toString() }.forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("type", "日历日程")
                        put("title", event.title)
                        put("content", event.content)
                        put("date", event.date)
                        put("startTime", event.startTime)
                        put("endTime", event.endTime)
                        put("tags", JSONArray(event.tags))
                    })
                }
            }
            if (reviewDate == null && data.preferences.includeDiariesInAi) data.diaries.forEach { diary ->
                put(JSONObject().apply {
                    put("id", diary.id)
                    put("type", "日记")
                    put("title", diary.title)
                    put("content", diary.content)
                    put("date", diary.date)
                })
            }
            if (reviewDate == null && data.preferences.includeGoalsInAi) data.goals.forEach { goal ->
                put(JSONObject().apply {
                    put("id", goal.id)
                    put("type", "目标")
                    put("title", goal.title)
                    put("content", goal.content)
                    put("deadline", goal.deadline)
                    put("completed", goal.isCompleted)
                    put("subtasks", JSONArray().apply { goal.subtasks.forEach { child ->
                        put(JSONObject().apply { put("title", child.title); put("completed", child.completed) })
                    } })
                })
            }
        })
    }

    private fun sourceIndex(data: AppData): Map<String, Citation> = buildList {
        if (data.preferences.includeDiariesInAi) data.diaries.forEach {
            add(Citation(it.id, it.title, "日记", it.content.take(120), it.updatedAt))
        }
        if (data.preferences.includeTasksInAi) {
            data.tasks.forEach {
                val schedule = if (it.isTodo) "${it.dueDate}—${it.endDate}" else "${it.dueDate} ${it.scheduledTime.orEmpty()}"
                add(Citation(it.id, it.title, if (it.isTodo) "待办" else "日程", "${it.content}\n$schedule".trim().take(120), it.createdAt))
            }
            data.events.forEach {
                add(Citation(it.id, it.title, "日历日程", "${it.date} ${it.startTime}—${it.endTime} ${it.content}".trim().take(120), it.date))
            }
        }
        if (data.preferences.includeGoalsInAi) data.goals.forEach {
            add(Citation(it.id, it.title, "目标", "截止 ${it.deadline} · ${it.content}".take(120), it.createdAt))
        }
    }.associateBy(Citation::id)

    private fun validDateOrNull(value: String?): String? = value?.let {
        runCatching { LocalDate.parse(it).toString() }.getOrNull()
    }

    private fun validTimeOrNull(value: String?): String? = value?.let {
        runCatching { java.time.LocalTime.parse(it).toString().take(5) }.getOrNull()
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).trim().takeIf(String::isNotBlank)

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }.distinct()
}

class AiGatewayException(message: String) : Exception(message)
