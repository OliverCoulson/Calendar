package com.example.voiceinput.service.impl;

import com.example.voiceinput.model.CalendarEvent;
import com.example.voiceinput.model.IntentType;
import com.example.voiceinput.model.ParsedResult;
import com.example.voiceinput.service.AIService;
import com.example.voiceinput.service.NLPProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class LLMNLPProcessor implements NLPProcessor {

    private final AIService aiService;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> PERIODS = Set.of("凌晨","早上","上午","中午","下午","晚上","傍晚","夜里","全天");

    public LLMNLPProcessor(AIService aiService) { this.aiService = aiService; }
    public boolean isEnabled() { return aiService.isEnabled(); }

    @Override
    public ParsedResult parse(String text) {
        if (!aiService.isEnabled()) return empty();
        Map<String, Object> fc = aiService.functionCall(text);
        if (fc == null) return empty();
        return functionToResult(fc, text);
    }

    @Override
    public ParsedResult parseConfirmation(String text, List<CalendarEvent> candidates) {
        return empty();
    }

    @SuppressWarnings("unchecked")
    private ParsedResult functionToResult(Map<String, Object> fc, String originalText) {
        String fn = (String) fc.getOrDefault("function", "unknown");
        Map<String, Object> p = (Map<String, Object>) fc.getOrDefault("parameters", Map.of());
        Map<String, Object> entities = new HashMap<>();
        IntentType intent;

        switch (fn) {
            case "add_event" -> {
                intent = IntentType.ADD;
                populateAddEntities(p, entities, originalText);
            }
            case "delete_event" -> {
                intent = IntentType.DELETE;
                String kw = str(p, "keyword");
                String date = str(p, "date");
                if (kw != null) entities.put("title", kw);
                if (date != null) entities.put("timeRange", dayRange(date));
            }
            case "query_events" -> {
                intent = IntentType.QUERY;
                String d = str(p, "date");
                if (d != null) entities.put("timeRange", dayRange(d));
            }
            default -> intent = IntentType.UNKNOWN;
        }

        System.out.println("[AI] → " + fn + " intent=" + intent);
        return new ParsedResult(intent, entities, 0.9);
    }

    private void populateAddEntities(Map<String, Object> p, Map<String, Object> entities, String text) {
        String title = str(p, "title");
        String location = str(p, "location");
        if (location != null) {
            location = location.trim().replaceFirst("^在", "");
            if (title != null) title = title.replace(location, "").trim();
        }
        if (title != null && !title.isBlank()) entities.put("title", title.trim());
        if (location != null && !location.isBlank()) entities.put("location", location.trim());
        String desc = str(p, "description");
        if (desc != null && !desc.isBlank()) entities.put("description", desc.trim());

        String st = str(p, "startTime");
        if (st != null) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(st, FMT);
                String et = str(p, "endTime");
                LocalDateTime endTime = (et != null) ? LocalDateTime.parse(et, FMT) : null;

                String period = detectPeriod(text);
                boolean hasClock = text.matches(".*\\d+\\s*点.*");
                if (period != null && !hasClock && startTime.toLocalTime().getHour() != 0) {
                    startTime = startTime.toLocalDate().atStartOfDay();
                    endTime = null;
                    if (title != null && !title.startsWith(period)) {
                        title = period + " " + title;
                        entities.put("title", title);
                    }
                }
                entities.put("timeRange", new LocalDateTime[]{startTime, endTime});
            } catch (Exception ignored) {}
        }
    }

    private LocalDateTime[] dayRange(String date) {
        try {
            LocalDateTime d = LocalDateTime.parse(date + " 00:00:00",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return new LocalDateTime[]{d, d.plusDays(1)};
        } catch (Exception e) { return null; }
    }

    private String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return (v != null && !v.toString().isBlank() && !"null".equals(v.toString())) ? v.toString() : null;
    }

    private String detectPeriod(String text) {
        for (String p : PERIODS) if (text.contains(p)) return p;
        return null;
    }

    private ParsedResult empty() {
        return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
    }
}
