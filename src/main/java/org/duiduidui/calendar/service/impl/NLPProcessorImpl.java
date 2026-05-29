package org.duiduidui.calendar.service.impl;

import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.model.IntentType;
import org.duiduidui.calendar.model.ParsedResult;
import org.duiduidui.calendar.service.NLPProcessor;

import java.util.*;

/**
 * NLP 实现 —— 基于关键词匹配的意图分类 + Natty 时间解析。
 * 当前先实现意图分类部分。
 */
public class NLPProcessorImpl implements NLPProcessor {

    private static final LinkedHashMap<IntentType, List<String>> TRIGGERS = new LinkedHashMap<>();
    static {
        TRIGGERS.put(IntentType.ADD,    Arrays.asList("添加", "新增", "记下", "安排", "提醒我", "创建", "加入"));
        TRIGGERS.put(IntentType.DELETE, Arrays.asList("删除", "取消", "移除", "去掉", "清除","删掉"));
        TRIGGERS.put(IntentType.MODIFY, Arrays.asList("修改", "改到", "推迟", "提前", "改成", "调整", "延后"));
        TRIGGERS.put(IntentType.QUERY,  Arrays.asList("查看", "查询", "列出", "有什么", "找一下", "搜索", "显示"));
    }

    @Override
    public ParsedResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
        }

        String cleaned = text.trim();

        IntentType intent = classifyIntent(cleaned);
        double confidence = computeConfidence(intent, cleaned);

        Map<String, Object> entities = new HashMap<>();

        ParsedResult result = new ParsedResult(intent, entities, confidence);
        if (result.isAmbiguous()) {
            result.setClarificationQuestion("您是要添加、删除、查询还是修改？");
        }
        return result;
    }

    @Override
    public ParsedResult parseConfirmation(String text, List<CalendarEvent> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
        }

        int index = -1;
        String cleaned = text.trim();

        if (cleaned.contains("第一") || cleaned.contains("1")) index = 0;
        else if (cleaned.contains("第二") || cleaned.contains("2")) index = 1;
        else if (cleaned.contains("第三") || cleaned.contains("3")) index = 2;
        else if (cleaned.contains("最后") || cleaned.contains("末")) index = candidates.size() - 1;

        if (index >= 0 && index < candidates.size()) {
            Map<String, Object> entities = new HashMap<>();
            entities.put("selectedIndex", index);
            return new ParsedResult(IntentType.UNKNOWN, entities, 0.9);
        }

        return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
    }

    private IntentType classifyIntent(String text) {
        IntentType best = IntentType.UNKNOWN;
        int bestPos = Integer.MAX_VALUE;

        for (Map.Entry<IntentType, List<String>> entry : TRIGGERS.entrySet()) {
            for (String trigger : entry.getValue()) {
                int pos = text.indexOf(trigger);
                if (pos != -1 && pos < bestPos) {
                    bestPos = pos;
                    best = entry.getKey();
                    break;
                }
            }
        }
        return best;
    }

    private double computeConfidence(IntentType intent, String text) {
        if (intent == IntentType.UNKNOWN) return 0.0;

        boolean explicitVerb = false;
        List<String> triggers = TRIGGERS.get(intent);
        if (triggers != null) {
            for (String t : triggers) {
                if (text.contains(t)) {
                    explicitVerb = true;
                    break;
                }
            }
        }

        return explicitVerb ? 0.9 : 0.5;
    }
}
