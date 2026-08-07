import assert from "node:assert/strict";
import test from "node:test";
import { normalizeReply } from "./index.js";

const payload = {
  mode: "chat",
  localDateTime: "2026-08-07T14:30:00",
  context: {
    records: [
      { id: "task-1", type: "任务", title: "准备报告" },
      { id: "diary-1", type: "日记", title: "今天" },
    ],
  },
};

test("filters invented citations and normalizes editable task draft", () => {
  const reply = normalizeReply({
    answer: "已经整理为任务草稿。",
    citationIds: ["task-1", "invented", "task-1"],
    taskDraft: {
      isTask: true,
      title: "完成周报",
      isTodo: false,
      startDate: "2026-08-08",
      endDate: "2026-08-08",
      startTime: "15:00",
      durationMinutes: 120,
      durationInput: "2小时",
      urgent: true,
      important: true,
      content: "整理本周进展",
      tags: ["工作", "工作", "周报"],
      reason: "识别到明确时间和行动",
    },
  }, payload);

  assert.deepEqual(reply.citationIds, ["task-1"]);
  assert.equal(reply.taskDraft.startTime, "15:00");
  assert.equal(reply.taskDraft.durationMinutes, 120);
  assert.deepEqual(reply.taskDraft.tags, ["工作", "周报"]);
});

test("review can never create a task and invalid dates fall back locally", () => {
  const reply = normalizeReply({
    answer: "每日复盘\n\n一点感受：也许今天更需要休息。",
    citationIds: ["diary-1"],
    taskDraft: {
      isTask: true,
      startDate: "2026-02-31",
      endDate: "not-a-date",
    },
  }, { ...payload, mode: "review" });

  assert.deepEqual(reply.citationIds, []);
  assert.equal(reply.taskDraft.isTask, false);
  assert.equal(reply.taskDraft.startDate, "2026-08-07");
  assert.equal(reply.taskDraft.endDate, "2026-08-07");
});
