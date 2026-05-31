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

    public CalendarController(CalendarService calendarService,
                              LLMNLPProcessor llmProcessor,
                              NLPProcessorImpl ruleProcessor,
                              SqliteEventDAO eventDAO, UserDAO userDAO) {
        this.calendarService = calendarService;
        this.llmProcessor = llmProcessor;
        this.ruleProcessor = ruleProcessor;
        this.eventDAO = eventDAO;
        this.userDAO = userDAO;
    }

    // ==================== 语音解析（公开，无需登录） ====================

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
            result = ruleProcessor.parse(text);
            engine = "rule";
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("recognizedText", text);
        resp.put("intent", result.getIntent().name());
        resp.put("confidence", result.getConfidence());
        resp.put("engine", engine);

        if (result.isAmbiguous()) {
            resp.put("message", result.getClarificationQuestion());
            return ResponseEntity.ok(resp);
        }

        extractEntities(result, resp);
        System.out.println("[日历] \"" + text + "\" → " + engine + " → " + result.getIntent());
        return ResponseEntity.ok(resp);
    }

    // ==================== 查询事件（公开，按登录用户过滤） ====================

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEvent>> getAllEvents(@RequestHeader(value = "X-Token", defaultValue = "") String token) {
        setUser(token);
        return ResponseEntity.ok(calendarService.getAllEvents());
    }

    @GetMapping("/events/range")
    public ResponseEntity<List<CalendarEvent>> getEventsByRange(
            @RequestParam String start, @RequestParam String end,
            @RequestHeader(value = "X-Token", defaultValue = "") String token) {
        setUser(token);
        LocalDateTime st = LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime ed = LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ResponseEntity.ok(calendarService.queryByTimeRange(st, ed));
    }

    // ==================== TODO: 增删（需要登录） ====================

    /**
     * TODO: POST /api/calendar/events/add
     * 1. 从 header 取 X-Token，解析用户 → eventDAO.setCurrentUser(phone)
     * 2. 从 body 取 /voice 返回的实体（title, startTime, endTime, location）
     * 3. 冲突检测 + 去重
     * 4. calendarService.addEvent()
     * 参考旧版 CalendarController.executeAdd()
     */
    // TODO

    /**
     * TODO: DELETE /api/calendar/events/{id}
     * 1. 鉴权：eventDAO.setCurrentUser(phone)
     * 2. calendarService.deleteEvent(id)
     */
    // TODO

    // ==================== 辅助 ====================

    /** 从 token 解析用户并设置到 DAO */
    private void setUser(String token) {
        if (token != null && !token.isEmpty()) {
            User user = userDAO.findByToken(token);
            if (user != null) eventDAO.setCurrentUser(user.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private void extractEntities(ParsedResult result, Map<String, Object> resp) {
        Object timeObj = result.getEntities().get("timeRange");
        if (timeObj != null) {
            LocalDateTime[] tr = (LocalDateTime[]) timeObj;
            if (tr[0] != null) {
                resp.put("startTime", tr[0].format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                if (tr.length > 1 && tr[1] != null)
                    resp.put("endTime", tr[1].format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        resp.put("title", result.getEntities().getOrDefault("title", null));
        resp.put("location", result.getEntities().getOrDefault("location", null));
        resp.put("description", result.getEntities().getOrDefault("description", null));
        resp.put("period", result.getEntities().getOrDefault("period", null));
    }
}
