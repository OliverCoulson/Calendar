package org.duiduidui.calendar.service.impl;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.model.IntentType;
import org.duiduidui.calendar.model.ParsedResult;
import org.duiduidui.calendar.service.NLPProcessor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class NLPProcessorImpl implements NLPProcessor {

    private static final LinkedHashMap<IntentType, List<String>> TRIGGERS = new LinkedHashMap<>();
    static {
        TRIGGERS.put(IntentType.ADD,    Arrays.asList("添加", "新增", "记下", "安排", "提醒我", "创建", "加入"));
        TRIGGERS.put(IntentType.DELETE, Arrays.asList("删除", "取消", "移除", "去掉", "清除", "删掉"));
        TRIGGERS.put(IntentType.MODIFY, Arrays.asList("修改", "改到", "推迟", "提前", "改成", "调整", "延后"));
        TRIGGERS.put(IntentType.QUERY,  Arrays.asList("查看", "查询", "列出", "有什么", "找一下", "搜索", "显示"));
    }

    private static final Map<String, String> CHINESE_WEEK_MAP = new HashMap<>();
    static {
        CHINESE_WEEK_MAP.put("星期一", "Monday");  CHINESE_WEEK_MAP.put("周一", "Monday");
        CHINESE_WEEK_MAP.put("星期二", "Tuesday"); CHINESE_WEEK_MAP.put("周二", "Tuesday");
        CHINESE_WEEK_MAP.put("星期三", "Wednesday"); CHINESE_WEEK_MAP.put("周三", "Wednesday");
        CHINESE_WEEK_MAP.put("星期四", "Thursday"); CHINESE_WEEK_MAP.put("周四", "Thursday");
        CHINESE_WEEK_MAP.put("星期五", "Friday");  CHINESE_WEEK_MAP.put("周五", "Friday");
        CHINESE_WEEK_MAP.put("星期六", "Saturday"); CHINESE_WEEK_MAP.put("周六", "Saturday");
        CHINESE_WEEK_MAP.put("星期日", "Sunday");  CHINESE_WEEK_MAP.put("周日", "Sunday");
        CHINESE_WEEK_MAP.put("星期天", "Sunday");
    }

    private final Parser nattyParser = new Parser();

    @Override
    public ParsedResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
        }

        String cleaned = text.trim();

        // 1. 意图分类
        IntentType intent = classifyIntent(cleaned);
        double confidence = computeConfidence(intent, cleaned);

        // 2. 时间解析
        Map<String, Object> entities = new HashMap<>();
        LocalDateTime[] timeRange = parseTime(cleaned);
        if (timeRange != null) {
            entities.put("timeRange", timeRange);
            // 时间解析成功提升置信度
            if (confidence > 0 && confidence < 1.0) confidence = Math.min(1.0, confidence + 0.1);
        }

        // 3. 标题提取
        String title = extractTitle(cleaned, intent);
        if (title != null && !title.isEmpty()) {
            entities.put("title", title);
        }

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

    // ========== 意图分类 ==========

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
                if (text.contains(t)) { explicitVerb = true; break; }
            }
        }
        return explicitVerb ? 0.9 : 0.5;
    }

    // ========== 时间解析 ==========

    private LocalDateTime[] parseTime(String text) {
        // 1. 用 Natty 解析日期部分（today / tomorrow / Wednesday）
        String engDate = chineseDateToEnglish(text);
        List<DateGroup> groups = nattyParser.parse(engDate);
        if (groups.isEmpty() || groups.get(0).getDates().isEmpty()) return null;

        Date date = groups.get(0).getDates().get(0);
        LocalDateTime dateTime = dateToLocal(date);

        // 2. 从原文提取时分（小时:分钟）
        String digitText = convertChineseDigits(text);
        int hour = extractHour(digitText);
        int minute = extractMinute(digitText);

        if (hour < 0) {
            // 没有找到时间，只返回日期（全天）
            return new LocalDateTime[]{
                    dateTime.toLocalDate().atStartOfDay(),
                    dateTime.toLocalDate().atStartOfDay().plusDays(1)
            };
        }

        // 3. AM/PM 偏移：下午/晚上 → hour += 12（如果 hour < 12）
        boolean isPm = text.contains("下午") || text.contains("晚上");
        if (isPm && hour >= 1 && hour <= 12) {
            hour += 12;
        }

        LocalDateTime start = dateTime.toLocalDate().atTime(hour, minute);
        LocalDateTime end = start.plusHours(1);

        System.out.println("[NLP] 时间解析: " + text + " → " + start);
        return new LocalDateTime[]{start, end};
    }

    private int extractHour(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*点").matcher(text);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h >= 0 && h <= 24) return h;
        }
        return -1;
    }

    private int extractMinute(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("点\\s*(\\d+)\\s*分").matcher(text);
        if (m.find()) {
            int min = Integer.parseInt(m.group(1));
            if (min >= 0 && min < 60) return min;
        }
        // "点半"
        m = java.util.regex.Pattern.compile("点\\s*半").matcher(text);
        if (m.find()) return 30;
        return 0;
    }

    private LocalDateTime dateToLocal(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 仅提取日期部分给 Natty 解析 */
    private String chineseDateToEnglish(String text) {
        String result = text;

        // 1. 星期映射
        for (Map.Entry<String, String> entry : CHINESE_WEEK_MAP.entrySet()) {
            if (result.contains(entry.getKey())) {
                if (result.contains("每" + entry.getKey())) {
                    result = result.replace("每" + entry.getKey(), "every " + entry.getValue());
                } else {
                    result = result.replace(entry.getKey(), entry.getValue());
                }
            }
        }

        // 2. 日期词
        result = result.replace("今天", "today");
        result = result.replace("明天", "tomorrow");
        result = result.replace("后天", "day after tomorrow");
        result = result.replace("大后天", "3 days from now");

        // 3. 中文数字转阿拉伯
        result = convertChineseDigits(result);

        // 4. "每" 开头的周期词
        result = result.replace("每天", "every day");
        result = result.replace("每月", "every month");
        result = result.replace("每年", "every year");

        // 5. 去掉中文字符，仅保留英文和时间数字给 Natty
        result = result.replaceAll("[\\u4e00-\\u9fff\\u3000-\\u30ff]", " ");
        result = result.trim().replaceAll("\\s+", " ");

        System.out.println("[NLP] 中文预处理: " + text + " → " + result);
        return result;
    }

    /** 中文数字转阿拉伯数字 */
    private String convertChineseDigits(String text) {
        String result = text;
        String[][] map = {
                {"十一", "11"}, {"十二", "12"}, {"十三", "13"}, {"十四", "14"},
                {"十五", "15"}, {"十六", "16"}, {"十七", "17"}, {"十八", "18"},
                {"十九", "19"}, {"二十", "20"}, {"二十一", "21"}, {"二十二", "22"},
                {"二十三", "23"}, {"二十四", "24"},
                {"一", "1"}, {"二", "2"}, {"三", "3"}, {"四", "4"},
                {"五", "5"}, {"六", "6"}, {"七", "7"}, {"八", "8"}, {"九", "9"}, {"十", "10"},
        };
        for (String[] pair : map) {
            result = result.replace(pair[0], pair[1]);
        }
        return result;
    }

    // ========== 标题提取 ==========

    private String extractTitle(String text, IntentType intent) {
        String[] stopWords = {"添加", "新增", "记下", "安排", "提醒我", "创建", "加入",
                "删除", "取消", "移除", "去掉", "清除", "删掉",
                "修改", "改到", "推迟", "提前", "改成", "调整", "延后",
                "查看", "查询", "列出", "有什么", "找一下", "搜索", "显示",
                "今天", "明天", "后天", "昨天",
                "早上", "上午", "中午", "下午", "晚上",
                "周一", "周二", "周三", "周四", "周五", "周六", "周日",
                "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "星期天",
                "点", "分", "半",
                "的"};

        String title = text;
        for (String word : stopWords) {
            title = title.replace(word, " ");
        }
        title = title.trim().replaceAll("\\s+", " ");

        if (title.isEmpty()) return null;

        // 如果标题只剩数字或时间，没实际内容，返回 null
        if (title.matches("^[0-9:\\s]+$")) return null;

        return title;
    }
}
