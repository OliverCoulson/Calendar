package com.example.voiceinput.controller;

import com.example.voiceinput.dao.SqliteEventDAO;
import com.example.voiceinput.dao.UserDAO;
import com.example.voiceinput.model.*;
import com.example.voiceinput.service.CalendarService;
import com.example.voiceinput.service.impl.LLMNLPProcessor;
import com.example.voiceinput.service.impl.NLPProcessorImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final LLMNLPProcessor llmProcessor;
    private final NLPProcessorImpl ruleProcessor;
    private final SqliteEventDAO eventDAO;
    private final UserDAO userDAO;

    public CalendarController(CalendarService cs, LLMNLPProcessor llm, NLPProcessorImpl rule,
                              SqliteEventDAO ed, UserDAO ud) {
        this.calendarService = cs; this.llmProcessor = llm; this.ruleProcessor = rule;
        this.eventDAO = ed; this.userDAO = ud;
    }

    // ==================== 语音解析（公开） ====================

    @PostMapping("/voice")
    public ResponseEntity<Map<String, Object>> processVoice(@RequestBody VoiceRequest request) {
        String text = request.getText();
        if (text == null || text.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文本为空"));
        String cleaned = text.trim().replaceAll("[。，！？、；：\\s]", "");
        if (cleaned.length() <= 2 || cleaned.matches("^[嗯啊哦呢吧嘛呀嘿哈呵哟呗啦哇哎唉呃喔呐咚滴]+$"))
            return ResponseEntity.ok(Map.of("success", false, "message", "未识别到有效内容"));

        ParsedResult result = llmProcessor.parse(text);
        String engine = "llm";
        if (!llmProcessor.isEnabled() || result.getIntent() == IntentType.UNKNOWN) {
            result = ruleProcessor.parse(text); engine = "rule";
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true); resp.put("intent", result.getIntent().name());
        resp.put("confidence", result.getConfidence()); resp.put("engine", engine);
        extractEntities(result, resp);
        System.out.println("[日历] \"" + text + "\" → " + engine + " → " + result.getIntent());
        return ResponseEntity.ok(resp);
    }

    // ==================== 事件查询 ====================

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEvent>> getAllEvents(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestParam(value = "viewUser", defaultValue = "0") long viewUser) {
        User me = auth(token);
        if (me == null) return ResponseEntity.ok(List.of());
        eventDAO.setCurrentUser(me.getId());
        if (viewUser > 0 && canView(me, viewUser)) eventDAO.setViewingUser(viewUser);
        return ResponseEntity.ok(calendarService.getAllEvents());
    }

    @GetMapping("/events/range")
    public ResponseEntity<List<CalendarEvent>> getEventsByRange(
            @RequestParam String start, @RequestParam String end,
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestParam(value = "viewUser", defaultValue = "0") long viewUser) {
        User me = auth(token);
        if (me == null) return ResponseEntity.ok(List.of());
        eventDAO.setCurrentUser(me.getId());
        if (viewUser > 0 && canView(me, viewUser)) eventDAO.setViewingUser(viewUser);
        LocalDateTime st = LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime ed = LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ResponseEntity.ok(calendarService.queryByTimeRange(st, ed));
    }

    // ==================== TODO: 语音执行（ADD / DELETE / QUERY） ====================

    /**
     * TODO: POST /api/calendar/voice/execute
     * 接收语音文本 → NLP 解析 → 根据 intent 自动执行 ADD / DELETE / QUERY。
     * 这是核心接口，把 /voice 的解析结果直接落地为日历操作。
     *
     * 入参：{ text: "明天下午三点开会" }
     *
     * 实现步骤：
     *   1. 调用 /voice 的 NLP 解析逻辑获取 intent + entities
     *   2. intent == ADD    → 执行添加（参考 events/add TODO）
     *   3. intent == DELETE → 执行删除（搜索候选 → 单候选直接删 / 多候选返回列表）
     *   4. intent == QUERY  → 按时间/关键词查询，格式化返回
     *   5. 返回 { success, intent, message, events, candidates? }
     *
     * 参考：voice-input-backend/CalendarController.processVoice()
     *       voice-input-backend/CalendarController.executeAdd/Delete/Query()
     */
    @PostMapping("/voice/execute")
    public ResponseEntity<Map<String, Object>> voiceExecute(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestBody VoiceRequest request) {
        User me = auth(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));

        String text = request.getText();
        if (text == null || text.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文本为空"));

        String cleaned = text.trim().replaceAll("[。，！？、；：\\s]", "");
        if (cleaned.length() <= 2 || cleaned.matches("^[嗯啊哦呢吧嘛呀嘿哈呵哟呗啦哇哎唉呃喔呐咚滴]+$"))
            return ResponseEntity.ok(Map.of("success", false, "message", "未识别到有效内容"));

        eventDAO.setCurrentUser(me.getId());

        // NLP 解析：LLM → rule 回退
        ParsedResult result = llmProcessor.parse(text);
        String engine = "llm";
        if (!llmProcessor.isEnabled() || result.getIntent() == IntentType.UNKNOWN) {
            result = ruleProcessor.parse(text);
            engine = "rule";
        }

        if (result.getIntent() == IntentType.UNKNOWN) {
            return ResponseEntity.ok(Map.of(
                "success", false, "message", "未能识别意图",
                "engine", engine
            ));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("intent", result.getIntent().name());
        resp.put("engine", engine);
        System.out.println("[语音执行] \"" + text + "\" → " + engine + " → " + result.getIntent());

        switch (result.getIntent()) {
            case ADD -> {
                Map<String, Object> addResult = executeAdd(result);
                resp.putAll(addResult);
            }
            case DELETE -> {
                Map<String, Object> delResult = executeDelete(result);
                resp.putAll(delResult);
            }
            case QUERY -> {
                List<CalendarEvent> events = executeQuery(result);
                resp.put("message", events.isEmpty() ? "暂无事件" : "找到 " + events.size() + " 个事件");
                resp.put("events", events);
            }
            case MODIFY -> {
                Map<String, Object> modResult = executeModify(result);
                resp.putAll(modResult);
            }
            default -> {
                resp.put("success", false);
                resp.put("message", "不支持的意图");
            }
        }

        return ResponseEntity.ok(resp);
    }

    // ==================== TODO: 添加事件 ====================

    /**
     * TODO: POST /api/calendar/events/add
     * 接收 /voice 返回的 NLP 实体，写入数据库。
     *
     * 入参：{ title, startTime, endTime?, location?, targetUserId? }
     *   - targetUserId: 监护人为被监护人添加事件时传入
     *
     * 需要实现：
     *   1. 解析 token → 当前用户（参考 auth() 方法）
     *   2. 如果 targetUserId 存在且用户是监护人，设置 eventDAO.setViewingUser(targetUserId)
     *   3. 冲突检测：查询同时间段已有事件（eventDAO.findByTimeRange），有冲突返回提醒
     *   4. 去重：同日期同标题事件不重复添加
     *   5. calendarService.addEvent(event)
     *   6. 返回 { success, message, events }
     *
     * 参考：voice-input-backend/CalendarController.executeAdd()
     */
    @PostMapping("/events/add")
    public ResponseEntity<Map<String, Object>> addEvent(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestBody Map<String, Object> body) {
        // TODO: 实现添加事件逻辑
        return ResponseEntity.ok(Map.of("success", false, "message", "添加功能开发中"));
    }

    // ==================== TODO: 删除事件 ====================

    /**
     * TODO: DELETE /api/calendar/events/{id}
     * 删除指定事件。
     *
     * 需要实现：
     *   1. 解析 token → 当前用户
     *   2. eventDAO.setCurrentUser(userId) 设置当前用户
     *   3. calendarService.deleteEvent(id) — DAO 层已限制 created_by = 当前用户
     *   4. 返回 { success, message, events }
     *
     * 额外（进阶）：
     *   - 支持模糊匹配删除（POST /events/delete-by-voice）
     *     接收 /voice 的 DELETE intent 实体（keyword + date）
     *     搜索候选事件列表，单候选直接删，多候选返回列表让用户选
     *     参考：voice-input-backend/CalendarController.executeDelete()
     */
    @DeleteMapping("/events/{id}")
    public ResponseEntity<Map<String, Object>> deleteEvent(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @PathVariable String id) {
        // TODO: 实现删除事件逻辑
        return ResponseEntity.ok(Map.of("success", false, "message", "删除功能开发中"));
    }

    // ==================== TODO: 查看事件（语音驱动） ====================

    /**
     * TODO: POST /api/calendar/events/query
     * 根据 NLP 解析的实体查询事件，返回格式化结果。
     *
     * 入参：{ keyword?, date?, timeRange? }（来自 /voice 返回的 entities）
     *
     * 需要实现：
     *   1. 如果有 date   → calendarService.queryByTimeRange(当天 00:00, 次日 00:00)
     *   2. 如果有 keyword → calendarService.queryByKeyword(keyword)
     *   3. 都没有         → calendarService.getAllEvents()
     *   4. 格式化返回：单事件显示详情，多事件列出概要
     *
     * 参考：voice-input-backend/CalendarController.executeQuery()
     */
    @PostMapping("/events/query")
    public ResponseEntity<Map<String, Object>> queryEvents(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestBody Map<String, Object> body) {
        // TODO: 实现语音驱动的查询
        return ResponseEntity.ok(Map.of("success", false, "message", "查询功能开发中"));
    }

    // ==================== TODO: 删除事件（语音驱动） ====================

    /**
     * TODO: POST /api/calendar/events/delete-by-voice
     * 根据 NLP 实体模糊搜索并删除事件。
     *
     * 入参：{ keyword, date? }（来自 /voice 返回的 DELETE intent entities）
     *
     * 需要实现：
     *   1. 按 keyword + date 搜索候选事件
     *   2. 单候选 → 直接删除
     *   3. 多候选 → 返回 candidates 列表让用户选择
     *   4. 无候选 → 返回提示
     *
     * 参考：voice-input-backend/CalendarController.executeDelete()
     */
    @PostMapping("/events/delete-by-voice")
    public ResponseEntity<Map<String, Object>> deleteByVoice(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestBody Map<String, Object> body) {
        // TODO: 实现语音驱动的删除
        return ResponseEntity.ok(Map.of("success", false, "message", "语音删除功能开发中"));
    }

    // ==================== 辅助 ====================

    private User auth(String token) {
        if (token == null || token.isEmpty()) return null;
        return userDAO.findByToken(token);
    }

    /** 仅监护人可查看被监护人日历，老人无权查看监护人日历 */
    private boolean canView(User me, long targetId) {
        if (!"guardian".equals(me.getType())) return false;
        for (Map<String, Object> r : userDAO.approvedRelations(me.getId())) {
            if (r.get("id") != null && ((Number) r.get("id")).longValue() == targetId) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void extractEntities(ParsedResult result, Map<String, Object> resp) {
        Object timeObj = result.getEntities().get("timeRange");
        if (timeObj != null) {
            LocalDateTime[] tr = (LocalDateTime[]) timeObj;
            if (tr[0] != null) {
                resp.put("startTime", tr[0].format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                if (tr.length > 1 && tr[1] != null) resp.put("endTime", tr[1].format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        resp.put("title", result.getEntities().getOrDefault("title", null));
        resp.put("location", result.getEntities().getOrDefault("location", null));
        resp.put("period", result.getEntities().getOrDefault("period", null));
    }

    // ==================== 语音执行：ADD / DELETE / QUERY ====================

    /** 执行 ADD 意图：构建事件、去重、冲突检测、写入 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeAdd(ParsedResult result) {
        Map<String, Object> entities = result.getEntities();
        String title = (String) entities.get("title");
        LocalDateTime[] timeRange = (LocalDateTime[]) entities.get("timeRange");

        Map<String, Object> resp = new LinkedHashMap<>();
        if (title == null || title.isBlank() || timeRange == null || timeRange[0] == null) {
            resp.put("success", false);
            resp.put("message", "未能识别事件标题或时间");
            return resp;
        }

        LocalDateTime start = timeRange[0];
        LocalDateTime end = timeRange.length > 1 ? timeRange[1] : null;

        // 去重：同日期同标题不重复添加
        List<CalendarEvent> existing = calendarService.queryByTimeAndTitle(start, title);
        boolean isDuplicate = existing.stream().anyMatch(e -> e.getTitle().equals(title));
        if (isDuplicate) {
            resp.put("success", false);
            resp.put("message", "该事件已存在，请勿重复添加");
            return resp;
        }

        // 冲突检测：查询同时间段已有事件
        LocalDateTime checkEnd = end != null ? end : start.plusHours(1);
        List<CalendarEvent> conflicts = calendarService.queryByTimeRange(start, checkEnd);
        if (!conflicts.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "该时间段已有 " + conflicts.size() + " 个事件，请确认");
            resp.put("conflicts", conflicts);
            return resp;
        }

        // 创建并保存事件
        CalendarEvent event = new CalendarEvent(title, start, end);
        String location = (String) entities.get("location");
        if (location != null && !location.isBlank()) event.setLocation(location);
        event.setDescription((String) entities.get("description"));

        calendarService.addEvent(event);
        resp.put("success", true);
        resp.put("message", "已添加: " + title);
        resp.put("event", event);
        return resp;
    }

    /** 执行 DELETE 意图：搜索候选 → 单删 / 多候选返回 */
    private Map<String, Object> executeDelete(ParsedResult result) {
        Map<String, Object> entities = result.getEntities();
        String keyword = (String) entities.get("title");
        LocalDateTime[] timeRange = (LocalDateTime[]) entities.get("timeRange");

        Map<String, Object> resp = new LinkedHashMap<>();
        List<CalendarEvent> candidates;

        if (keyword != null && timeRange != null) {
            candidates = calendarService.queryByTimeAndTitle(timeRange[0], keyword);
        } else if (keyword != null) {
            candidates = calendarService.queryByKeyword(keyword);
        } else if (timeRange != null) {
            LocalDateTime end = timeRange.length > 1 && timeRange[1] != null ? timeRange[1] : timeRange[0].plusDays(1);
            candidates = calendarService.queryByTimeRange(timeRange[0], end);
        } else {
            resp.put("success", false);
            resp.put("message", "未能识别删除条件");
            return resp;
        }

        if (candidates.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "未找到匹配的事件");
            return resp;
        }

        if (candidates.size() == 1) {
            CalendarEvent evt = candidates.get(0);
            calendarService.deleteEvent(evt.getId());
            resp.put("success", true);
            resp.put("message", "已删除: " + evt.getTitle());
            return resp;
        }

        // 多候选，返回列表让用户选择
        resp.put("success", true);
        resp.put("message", "找到多个匹配事件，请选择");
        resp.put("candidates", candidates);
        return resp;
    }

    /** 执行 QUERY 意图：按时间/关键词/全部查询 */
    private List<CalendarEvent> executeQuery(ParsedResult result) {
        Map<String, Object> entities = result.getEntities();
        String keyword = (String) entities.get("title");
        LocalDateTime[] timeRange = (LocalDateTime[]) entities.get("timeRange");

        if (timeRange != null) {
            LocalDateTime end = timeRange.length > 1 && timeRange[1] != null ? timeRange[1] : timeRange[0].plusDays(1);
            return calendarService.queryByTimeRange(timeRange[0], end);
        }
        if (keyword != null && !keyword.isBlank()) {
            return calendarService.queryByKeyword(keyword);
        }
        return calendarService.getAllEvents();
    }

    /** 执行 MODIFY 意图：搜索候选 → 单改 / 多候选返回 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeModify(ParsedResult result) {
        Map<String, Object> entities = result.getEntities();
        String keyword = (String) entities.get("title");
        LocalDateTime[] timeRange = (LocalDateTime[]) entities.get("timeRange");

        Map<String, Object> resp = new LinkedHashMap<>();
        List<CalendarEvent> candidates;

        if (keyword != null && timeRange != null) {
            candidates = calendarService.queryByTimeAndTitle(timeRange[0], keyword);
        } else if (keyword != null) {
            candidates = calendarService.queryByKeyword(keyword);
        } else {
            resp.put("success", false);
            resp.put("message", "未能识别要修改的事件");
            return resp;
        }

        if (candidates.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "未找到匹配的事件");
            return resp;
        }

        if (candidates.size() == 1) {
            CalendarEvent evt = candidates.get(0);
            // 更新字段
            String newTitle = (String) entities.get("newTitle");
            if (newTitle != null && !newTitle.isBlank()) evt.setTitle(newTitle);
            String nst = (String) entities.get("newStartTime");
            if (nst != null) {
                try {
                    evt.setStartTime(LocalDateTime.parse(nst, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    evt.setRemindTime(evt.getStartTime().minusMinutes(10));
                } catch (Exception ignored) {}
            }
            String net = (String) entities.get("newEndTime");
            if (net != null) {
                try { evt.setEndTime(LocalDateTime.parse(net, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))); }
                catch (Exception ignored) {}
            }
            String nl = (String) entities.get("newLocation");
            if (nl != null && !nl.isBlank()) evt.setLocation(nl);

            calendarService.updateEvent(evt);
            resp.put("success", true);
            resp.put("message", "已修改: " + evt.getTitle());
            resp.put("event", evt);
            return resp;
        }

        // 多候选返回列表
        resp.put("success", true);
        resp.put("message", "找到多个匹配事件，请选择要修改的");
        resp.put("candidates", candidates);
        return resp;
    }
}
