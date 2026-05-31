package com.example.voiceinput.service.impl;

import com.example.voiceinput.dao.NlpConfigDAO;
import com.example.voiceinput.model.CalendarEvent;
import com.example.voiceinput.model.IntentType;
import com.example.voiceinput.model.ParsedResult;
import com.example.voiceinput.service.NLPProcessor;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NLPProcessorImpl implements NLPProcessor {

    private final NlpConfigDAO configDAO;
    private final Parser nattyParser = new Parser();

    // 启动时从 DB 加载
    private List<String> triggerAdd;
    private List<String> triggerDelete;
    private List<String> triggerQuery;
    private List<String> stopWords;
    private List<String> fillers;

    private static final Map<String, String> CHINESE_WEEK_MAP = new LinkedHashMap<>();
    static {
        CHINESE_WEEK_MAP.put("星期一", "Monday"); CHINESE_WEEK_MAP.put("周一", "Monday");
        CHINESE_WEEK_MAP.put("星期二", "Tuesday"); CHINESE_WEEK_MAP.put("周二", "Tuesday");
        CHINESE_WEEK_MAP.put("星期三", "Wednesday"); CHINESE_WEEK_MAP.put("周三", "Wednesday");
        CHINESE_WEEK_MAP.put("星期四", "Thursday"); CHINESE_WEEK_MAP.put("周四", "Thursday");
        CHINESE_WEEK_MAP.put("星期五", "Friday"); CHINESE_WEEK_MAP.put("周五", "Friday");
        CHINESE_WEEK_MAP.put("星期六", "Saturday"); CHINESE_WEEK_MAP.put("周六", "Saturday");
        CHINESE_WEEK_MAP.put("星期日", "Sunday"); CHINESE_WEEK_MAP.put("周日", "Sunday");
        CHINESE_WEEK_MAP.put("星期天", "Sunday");
    }

    private static final String[] CN_DIGITS = {
        "零","一","二","三","四","五","六","七","八","九","十",
        "十一","十二","十三","十四","十五","十六","十七","十八","十九","二十",
        "二十一","二十二","二十三","二十四"
    };

    public NLPProcessorImpl(NlpConfigDAO configDAO) {
        this.configDAO = configDAO;
        reloadConfig();
    }

    /** 重新从 DB 加载配置词（支持运行时刷新） */
    public void reloadConfig() {
        Map<String, List<String>> map = configDAO.loadByCategory();
        this.triggerAdd    = map.getOrDefault("trigger_add", List.of());
        this.triggerDelete = map.getOrDefault("trigger_delete", List.of());
        this.triggerQuery  = map.getOrDefault("trigger_query", List.of());
        this.stopWords     = map.getOrDefault("stop_word", List.of());
        this.fillers       = map.getOrDefault("filler", List.of());
        System.out.println("[NLP-Rule] 已加载 " +
            (triggerAdd.size()+triggerDelete.size()+triggerQuery.size()) + " 触发词, " +
            stopWords.size() + " 停用词, " + fillers.size() + " 语气词");
    }

    @Override
    public ParsedResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
        }

        String cleaned = text.trim();

        // 1. 意图分类
        IntentType intent = classifyIntent(cleaned);
        double confidence = computeConfidence(intent);

        // 2. 时间+时长解析
        Map<String, Object> entities = new HashMap<>();
        LocalDateTime[] timeRange = parseTime(cleaned);
        if (timeRange != null) {
            entities.put("timeRange", timeRange);
            if (confidence > 0 && confidence < 1.0) confidence = Math.min(1.0, confidence + 0.1);
        }

        // 3. 时间段提取（早上/上午/下午/晚上）
        String period = extractPeriod(cleaned, timeRange);
        if (period != null) entities.put("period", period);

        // 4. 地点提取（在标题提取之前，因为地点会影响标题）
        String location = extractLocation(cleaned);
        if (location != null) entities.put("location", location);

        // 5. 标题提取（去掉地点及其前后缀 "在/于" + 地点 + "的"）
        String textWithoutLoc = cleaned;
        if (location != null) {
            textWithoutLoc = cleaned
                .replace("在" + location, " ")
                .replace("于" + location, " ")
                .replace(location + "的", " ")
                .replace(location, " ");
        }
        String title = extractTitle(textWithoutLoc);
        if (title != null && !title.isEmpty()) {
            entities.put("title", title);
        }

        // 5. 隐式意图
        if (intent == IntentType.UNKNOWN && timeRange != null && title != null) {
            intent = IntentType.ADD; confidence = 0.75;
        } else if (intent == IntentType.UNKNOWN && timeRange != null) {
            intent = IntentType.QUERY; confidence = 0.6;
        }

        ParsedResult result = new ParsedResult(intent, entities, confidence);
        if (result.isAmbiguous()) {
            result.setClarificationQuestion("您是要添加、删除、查询还是修改？");
        }
        return result;
    }

    @Override
    public ParsedResult parseConfirmation(String text, List<CalendarEvent> candidates) {
        if (candidates == null || candidates.isEmpty())
            return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
        String cleaned = text.trim();
        int index = -1;
        if (cleaned.contains("第一") || cleaned.contains("1")) index = 0;
        else if (cleaned.contains("第二") || cleaned.contains("2")) index = 1;
        else if (cleaned.contains("第三") || cleaned.contains("3")) index = 2;
        if (index >= 0 && index < candidates.size()) {
            Map<String, Object> entities = new HashMap<>();
            entities.put("selectedIndex", index);
            return new ParsedResult(IntentType.UNKNOWN, entities, 0.9);
        }
        return new ParsedResult(IntentType.UNKNOWN, Collections.emptyMap(), 0.0);
    }

    // ====== 意图分类 ======

    private IntentType classifyIntent(String text) {
        IntentType best = IntentType.UNKNOWN;
        int bestPos = Integer.MAX_VALUE;
        // 按优先级: add > delete > query
        Map<IntentType, List<String>> triggers = new LinkedHashMap<>();
        triggers.put(IntentType.ADD, triggerAdd);
        triggers.put(IntentType.DELETE, triggerDelete);
        triggers.put(IntentType.QUERY, triggerQuery);
        for (Map.Entry<IntentType, List<String>> e : triggers.entrySet()) {
            for (String t : e.getValue()) {
                int pos = text.indexOf(t);
                if (pos != -1 && pos < bestPos) {
                    bestPos = pos; best = e.getKey(); break;
                }
            }
        }
        return best;
    }

    private double computeConfidence(IntentType intent) {
        return intent == IntentType.UNKNOWN ? 0.0 : 0.9;
    }

    // ====== 时间解析（含时长） ======

    private LocalDateTime[] parseTime(String text) {
        String engDate = chineseDateToEnglish(text);
        List<DateGroup> groups = nattyParser.parse(engDate);
        if (groups.isEmpty() || groups.get(0).getDates().isEmpty()) return null;

        Date date = groups.get(0).getDates().get(0);
        LocalDateTime base = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        String digitText = convertChineseDigits(text);
        int hour = extractHour(digitText);
        int minute = extractMinute(digitText);

        // 处理上午/下午/晚上
        boolean isPm = text.contains("下午") || text.contains("晚上");
        if (isPm && hour >= 1 && hour <= 12) hour += 12;

        if (hour < 0) {
            // 没有具体时间 → 仅记录日期，不强制时间
            return new LocalDateTime[]{base.toLocalDate().atStartOfDay(), null};
        }

        LocalDateTime start = base.toLocalDate().atTime(hour, minute);

        // ---- 检测结束时间或时长 ----
        LocalDateTime end = detectEndTime(text, digitText, start);
        // end 可能为 null（用户没说明时长）

        System.out.println("[NLP-Rule] 时间解析: " + text + " → start=" + start + " end=" + end);
        return new LocalDateTime[]{start, end};
    }

    /** 检测显式结束时间或时长 */
    private LocalDateTime detectEndTime(String text, String digitText, LocalDateTime start) {
        // 1. 检测 "到X点"、"到X点X分"（支持跨天：晚上十点到凌晨两点）
        Matcher m = Pattern.compile("到\\s*(\\d+)\\s*点\\s*(\\d+)?\\s*分?").matcher(digitText);
        if (m.find()) {
            int endHour = Integer.parseInt(m.group(1));
            int endMin = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            // 跨天检测：凌晨/早上的结束时间 + 开始时间是晚上 → 结束时间+1天
            boolean isEndAm = text.contains("凌晨") || (text.contains("早上") && endHour <= 6);
            boolean isStartPm = text.contains("晚上") || (text.contains("下午") && start.getHour() >= 17);
            if (!isEndAm && (text.contains("下午") || text.contains("晚上"))) {
                if (endHour >= 1 && endHour <= 12) endHour += 12;
            }
            LocalDateTime end = start.toLocalDate().atTime(endHour, endMin);
            // 跨天：结束时间 < 开始时间 → +1天
            if (!end.isAfter(start)) end = end.plusDays(1);
            return end;
        }

        // 2. 检测时长 "X小时"、"X个小时"、"半小时"、"X分钟"
        m = Pattern.compile("(\\d+)\\s*个?\\s*小时").matcher(digitText);
        if (m.find()) {
            int hours = Integer.parseInt(m.group(1));
            return start.plusHours(hours);
        }
        m = Pattern.compile("半\\s*小?\\s*时").matcher(text);
        if (m.find()) return start.plusMinutes(30);
        m = Pattern.compile("(\\d+)\\s*分\\s*钟").matcher(digitText);
        if (m.find()) {
            int mins = Integer.parseInt(m.group(1));
            return start.plusMinutes(mins);
        }

        return null;
    }

    private int extractHour(String text) {
        Matcher m = Pattern.compile("(\\d+)\\s*点").matcher(text);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h >= 0 && h <= 24) return h;
        }
        return -1;
    }

    private int extractMinute(String text) {
        Matcher m = Pattern.compile("点\\s*(\\d+)\\s*分").matcher(text);
        if (m.find()) { int min = Integer.parseInt(m.group(1)); if (min >= 0 && min < 60) return min; }
        m = Pattern.compile("点\\s*半").matcher(text);
        if (m.find()) return 30;
        return 0;
    }

    private String chineseDateToEnglish(String text) {
        String result = text;
        for (Map.Entry<String, String> e : CHINESE_WEEK_MAP.entrySet())
            if (result.contains(e.getKey())) result = result.replace(e.getKey(), " " + e.getValue() + " ");
        result = result.replace("今天", "today");
        result = result.replace("明天", "tomorrow");
        result = result.replace("后天", "day after tomorrow");
        result = result.replace("大后天", "3 days from now");
        result = convertChineseDigits(result);
        result = result.replaceAll("[\\u4e00-\\u9fff\\u3000-\\u30ff]", " ");
        return result.trim().replaceAll("\\s+", " ");
    }

    private String convertChineseDigits(String text) {
        String result = text;
        for (int i = CN_DIGITS.length - 1; i >= 0; i--)
            result = result.replace(CN_DIGITS[i], String.valueOf(i));
        return result;
    }

    // ====== 标题提取 ======

    // ====== 时间段提取 ======

    private String extractPeriod(String text, LocalDateTime[] timeRange) {
        // 只在没有具体时间时返回时间段描述
        if (timeRange == null || timeRange[0] == null) return null;
        // 有时间点（非00:00）说明用户说了具体时间，不需要时间段
        if (timeRange[0].toLocalTime().getHour() != 0 || timeRange[0].toLocalTime().getMinute() != 0) return null;

        if (text.contains("全天") || text.contains("整天") || text.contains("一整天")) return "全天";
        if (text.contains("凌晨")) return "凌晨";
        if (text.contains("早上")) return "早上";
        if (text.contains("上午")) return "上午";
        if (text.contains("中午")) return "中午";
        if (text.contains("下午")) return "下午";
        if (text.contains("晚上")) return "晚上";
        if (text.contains("傍晚")) return "傍晚";
        if (text.contains("夜里")) return "夜里";
        return null;
    }

    // ====== 地点提取 ======

    private String extractLocation(String text) {
        // 1. "在XXX" 模式
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "在\\s*([\\u4e00-\\u9fff\\d\\w]{1,20}?)\\s*(?:的|[。，！？、]|$)").matcher(text);
        if (m.find()) {
            String loc = cleanLocation(m.group(1));
            if (loc != null) { System.out.println("[NLP-Rule] 地点(在): " + loc); return loc; }
        }

        // 2. 数字+地点关键词: 401会议室 / 3楼 / 2号厅 (非贪婪)
        m = java.util.regex.Pattern.compile(
            "(\\d{1,6}(?:号|号楼|栋|单元|层)?[\\u4e00-\\u9fff]{0,6}?(?:会议室|房间|室|厅|楼|层|号|公司|酒店|咖啡厅|星巴克|餐厅|大厦|中心|广场|园区|校区|医院|银行|前台|接待处|大堂))").matcher(text);
        if (m.find()) {
            String candidate = m.group(1);
            if (!candidate.matches(".*[点分钟秒].*")) {
                String loc = cleanLocation(candidate);
                if (loc != null) { System.out.println("[NLP-Rule] 地点(号): " + loc); return loc; }
            }
        }

        return null;
    }

    private String cleanLocation(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("[的了吧吗呢。，！？、]$", "");
        if (s.isEmpty()) return null;
        if (s.matches("^[0-9]+$")) return null;  // 纯数字不是地点
        if (s.matches("^(点|分|半|个|[上下中早晚]午|早上|晚上|凌晨|今天|明天|后天)$")) return null;
        return s;
    }

    // ====== 标题提取 ======

    private String extractTitle(String text) {
        String title = text;
        // 标点
        title = title.replaceAll("[。，！？、；：\"\"''【】《》（）()…,.!?;:'\"\\[\\]{}]", " ");
        // 中文数字
        for (String d : CN_DIGITS) title = title.replace(d, " ");
        // 停用词 + 所有意图触发词（触发词也是事件无关词）
        Set<String> allRemoves = new LinkedHashSet<>();
        allRemoves.addAll(stopWords);
        allRemoves.addAll(triggerAdd);
        allRemoves.addAll(triggerDelete);
        allRemoves.addAll(triggerQuery);
        allRemoves.addAll(fillers);
        allRemoves.add("钟"); // o'clock
        for (String w : allRemoves) title = title.replace(w, " ");
        // 剩余数字
        title = title.replaceAll("\\b\\d+\\b", " ");
        // 单个中文字符的残留
        title = title.trim().replaceAll("\\s+", " ");
        if (title.isEmpty()) return null;
        if (title.matches("^[0-9:\\s]+$")) return null;
        return title.trim();
    }
}
