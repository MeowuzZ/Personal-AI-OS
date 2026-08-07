const outputExample = {
  answer: "回答或完整每日复盘",
  citationIds: ["只能使用 context.records 中存在的 id"],
  taskDraft: {
    isTask: false,
    title: "",
    isTodo: true,
    startDate: "2026-08-07",
    endDate: "2026-08-07",
    startTime: "",
    durationMinutes: 30,
    durationInput: "30分钟",
    urgent: false,
    important: true,
    content: "",
    tags: [],
    reason: "",
  },
};

const instructions = `你是“知行”个人 AI 助手。始终使用简体中文，语气温和、清晰、简洁。

你会收到设备本地时间、时区以及用户明确允许发送的个人记录。
- 回答个人数据问题时，只能依据 context.records；citationIds 只能填写其中真实存在的 id，禁止编造来源。
- 普通常识问答可以直接回答，此时 citationIds 为空；不确定时坦诚说明。
- 绝不声称已经修改数据。应用只会把任务草稿交给用户编辑确认。
- 当用户明确希望创建、规划或解析一项任务时，taskDraft.isTask=true，并从自然语言提取完整草稿。
- 有具体开始时刻的任务是日程：isTodo=false，startTime 使用 HH:mm；只有日期范围而无具体时刻的是待办：isTodo=true，startTime 留空字符串。
- 日期使用 YYYY-MM-DD。缺少日期时使用设备本地当天；缺少时长时默认 30 分钟；合理判断紧急、重要、标签，无法判断时 urgent=false、important=true。
- 非任务请求必须令 taskDraft.isTask=false。

当 mode=review：
- answer 必须是一篇可直接保存的完整每日复盘，保留事实清单和总时长，再加入“一点感受”。
- 主观评论要扎根于记录，用“我感受到”“也许”等措辞明确它是观察，不是事实；帮助用户理解节奏、取舍与生活感受，不做心理或医学诊断。
- 如果当天安排总时间超过 8 小时，结尾必须包含体谅、休息和恢复的提醒。
- citationIds 为空，taskDraft.isTask=false。

必须只输出一个合法 json 对象，不要输出 Markdown 代码块或额外说明。字段必须完整，格式示例：
${JSON.stringify(outputExample)}`;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        provider: "DeepSeek",
        model: env.DEEPSEEK_MODEL || "deepseek-v4-flash",
      });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/assistant") {
      return json({ error: "Not found" }, 404);
    }
    if (!env.DEEPSEEK_API_KEY) return json({ error: "服务端尚未配置 DEEPSEEK_API_KEY" }, 503);
    if (env.APP_ACCESS_TOKEN) {
      const expected = `Bearer ${env.APP_ACCESS_TOKEN}`;
      if (request.headers.get("Authorization") !== expected) return json({ error: "访问令牌无效" }, 401);
    }

    let payload;
    try {
      payload = await request.json();
    } catch {
      return json({ error: "请求不是有效 JSON" }, 400);
    }
    const serialized = JSON.stringify(payload);
    if (serialized.length > 250_000) return json({ error: "发送给模型的个人数据过多" }, 413);

    try {
      let rawReply = await callDeepSeek(serialized, env);
      if (!rawReply) {
        rawReply = await callDeepSeek(`${serialized}\n请严格按照示例返回非空 json。`, env);
      }
      if (!rawReply) return json({ error: "DeepSeek 没有返回文本结果" }, 502);
      const parsed = JSON.parse(rawReply);
      return json(normalizeReply(parsed, payload));
    } catch (error) {
      if (error instanceof DeepSeekError) return json({ error: error.message }, error.status);
      return json({ error: "DeepSeek 返回内容不符合约定结构" }, 502);
    }
  },
};

async function callDeepSeek(input, env) {
  const upstream = await fetch("https://api.deepseek.com/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${env.DEEPSEEK_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: env.DEEPSEEK_MODEL || "deepseek-v4-flash",
      messages: [
        { role: "system", content: instructions },
        { role: "user", content: input },
      ],
      thinking: { type: "disabled" },
      response_format: { type: "json_object" },
      temperature: 0.2,
      max_tokens: 2200,
      stream: false,
    }),
  });
  const responseText = await upstream.text();
  let response;
  try {
    response = JSON.parse(responseText);
  } catch {
    throw new DeepSeekError(`DeepSeek API 返回 HTTP ${upstream.status}`, 502);
  }
  if (!upstream.ok) {
    const message = response?.error?.message || `DeepSeek API 返回 HTTP ${upstream.status}`;
    throw new DeepSeekError(message, 502);
  }
  if (response?.choices?.[0]?.finish_reason === "length") {
    throw new DeepSeekError("DeepSeek 输出过长，请减少发送给 AI 的个人数据", 502);
  }
  return response?.choices?.[0]?.message?.content?.trim() || "";
}

export function normalizeReply(value, payload) {
  if (!value || typeof value !== "object" || typeof value.answer !== "string" || !value.answer.trim()) {
    throw new Error("answer missing");
  }
  const records = Array.isArray(payload?.context?.records) ? payload.context.records : [];
  const validIds = new Set(records.map((record) => record?.id).filter((id) => typeof id === "string"));
  const draft = value.taskDraft && typeof value.taskDraft === "object" ? value.taskDraft : {};
  const fallbackDate = validDate(String(payload?.localDateTime || "").slice(0, 10)) || new Date().toISOString().slice(0, 10);
  const isTask = payload?.mode !== "review" && draft.isTask === true;
  const isTodo = draft.isTodo !== false;
  const durationMinutes = Number.isInteger(draft.durationMinutes) && draft.durationMinutes > 0
    ? draft.durationMinutes
    : 30;

  return {
    answer: value.answer.trim(),
    citationIds: payload?.mode === "review" ? [] : uniqueStrings(value.citationIds)
      .filter((id) => validIds.has(id))
      .slice(0, 4),
    taskDraft: {
      isTask,
      title: isTask ? cleanString(draft.title) : "",
      isTodo,
      startDate: validDate(cleanString(draft.startDate)) || fallbackDate,
      endDate: validDate(cleanString(draft.endDate)) || validDate(cleanString(draft.startDate)) || fallbackDate,
      startTime: isTodo ? "" : validTime(cleanString(draft.startTime)) || "09:00",
      durationMinutes,
      durationInput: cleanString(draft.durationInput) || `${durationMinutes}分钟`,
      urgent: draft.urgent === true,
      important: draft.important !== false,
      content: cleanString(draft.content),
      tags: uniqueStrings(draft.tags).slice(0, 8),
      reason: cleanString(draft.reason),
    },
  };
}

function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function uniqueStrings(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.filter((item) => typeof item === "string").map((item) => item.trim()).filter(Boolean))];
}

function validDate(value) {
  if (!/^2\d{3}-(0[1-9]|1[0-2])-([0-2]\d|3[01])$/.test(value)) return "";
  const [year, month, day] = value.split("-").map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day
    ? value
    : "";
}

function validTime(value) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value) ? value : "";
}

class DeepSeekError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
