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

    // ==================== 添加事件 ====================

    @PostMapping("/events/add")
    public ResponseEntity<Map<String, Object>> addEvent(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @RequestBody Map<String, Object> body) {
        User me = auth(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "请先登录"));

        String title = (String) body.getOrDefault("title", "事项");
        String location = (String) body.get("location");
        String startStr = (String) body.get("startTime");
        if (startStr == null) return ResponseEntity.ok(Map.of("success", false, "message", "缺少时间"));
        LocalDateTime startTime = LocalDateTime.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime endTime = null;
        String endStr = (String) body.get("endTime");
        if (endStr != null && !endStr.isEmpty()) endTime = LocalDateTime.parse(endStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 目标用户：监护人可替老人添加
        long targetUserId = me.getId();
        Object targetObj = body.get("targetUserId");
        if (targetObj != null && targetObj instanceof Number) {
            long tid = ((Number) targetObj).longValue();
            if (tid != me.getId() && canView(me, tid)) targetUserId = tid;
        }

        eventDAO.setCurrentUser(me.getId());
        if (targetUserId != me.getId()) eventDAO.setViewingUser(targetUserId);

        // 去重
        boolean noSpecificTime = startTime.toLocalTime().getHour() == 0 && startTime.toLocalTime().getMinute() == 0;
        if (!noSpecificTime) {
            List<CalendarEvent> sameDay = calendarService.queryByTimeRange(
                startTime.toLocalDate().atStartOfDay(), startTime.toLocalDate().plusDays(1).atStartOfDay());
            if (sameDay.stream().anyMatch(e -> e.getTitle().equals(title)))
                return ResponseEntity.ok(Map.of("success", false, "message", "已存在相同事件"));
        }

        CalendarEvent event = new CalendarEvent(title, startTime, endTime);
        if (location != null) event.setLocation(location.toString());
        calendarService.addEvent(event);

        String date = startTime.toLocalDate().toString();
        String hint = targetUserId != me.getId() ? "（为 " + userDAO.findById(targetUserId).getNickname() + " 添加）" : "";
        return ResponseEntity.ok(Map.of("success", true,
            "message", "已添加「" + title + "」" + date + hint,
            "events", calendarService.getAllEvents()));
    }

    // ==================== 删除事件 ====================

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Map<String, Object>> deleteEvent(
            @RequestHeader(value = "X-Token", defaultValue = "") String token,
            @PathVariable String id) {
        User me = auth(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "请先登录"));
        eventDAO.setCurrentUser(me.getId());
        boolean deleted = calendarService.deleteEvent(id);
        return ResponseEntity.ok(Map.of("success", deleted,
            "message", deleted ? "已删除" : "无法删除（只能删除自己创建的事件）",
            "events", calendarService.getAllEvents()));
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
}
