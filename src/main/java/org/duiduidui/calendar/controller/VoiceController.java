package org.duiduidui.calendar.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.duiduidui.calendar.config.AppConfig;
import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.model.IntentType;
import org.duiduidui.calendar.model.ParsedResult;
import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.service.*;
import org.duiduidui.calendar.service.impl.MockVoiceService;
import org.duiduidui.calendar.service.impl.NLPProcessorImpl;
import org.duiduidui.calendar.service.impl.VoiceServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语音交互 Controller — 录音 → ASR → NLP → 执行意图（经由 CalendarService）。
 */
public class VoiceController {

    private final VoiceService voiceService;
    private final NLPProcessor nlpProcessor = new NLPProcessorImpl();
    private CalendarService calendarService;

    private Label statusLabel;
    private Label recognizedTextLabel;
    private Label intentLabel;
    private Label responseLabel;

    public VoiceController() {
        AppConfig config = new AppConfig();
        String appKey = config.getAliyunAppKey();
        String accessKeyId = config.getAliyunAccessKeyId();
        String accessKeySecret = config.getAliyunAccessKeySecret();

        if (appKey != null && !appKey.isEmpty()
                && accessKeyId != null && !accessKeyId.isEmpty()
                && accessKeySecret != null && !accessKeySecret.isEmpty()) {
            voiceService = new VoiceServiceImpl(appKey, accessKeyId, accessKeySecret, config.getAliyunGateway());
        } else {
            voiceService = new MockVoiceService();
        }

        voiceService.addRecordingListener(new RecordingListener() {
            @Override public void onRecordingStart() { Platform.runLater(() -> statusLabel.setText("录音中... 请说话")); }
            @Override public void onRecordingEnd()   { Platform.runLater(() -> statusLabel.setText("识别中，请稍候...")); }
            @Override public void onRecordingCancel(){ Platform.runLater(() -> statusLabel.setText("已取消")); }
            @Override public void onError(String m)  { Platform.runLater(() -> statusLabel.setText("错误: " + m)); }
        });

        voiceService.addRecognitionListener(new RecognitionListener() {
            @Override public void onSuccess(String text) {
                Platform.runLater(() -> processText(text));
            }
            @Override public void onError(VoiceException e) {
                Platform.runLater(() -> statusLabel.setText("识别失败: " + e.getMessage()));
            }
        });
    }

    public void setCalendarService(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    public void bindLabels(Label statusLabel, Label recognizedTextLabel, Label intentLabel, Label responseLabel) {
        this.statusLabel = statusLabel;
        this.recognizedTextLabel = recognizedTextLabel;
        this.intentLabel = intentLabel;
        this.responseLabel = responseLabel;
    }

    public void onVoiceButtonClick() {
        new Thread(() -> {
            try {
                voiceService.startListening();
            } catch (VoiceException e) {
                Platform.runLater(() -> statusLabel.setText("错误: " + e.getMessage()));
            }
        }, "voice-thread").start();
    }

    /** 键盘输入入口（debug 模式使用）。 */
    public void onTextSubmit(String text) {
        processText(text);
    }

    /** 处理文本：NLP 解析 → 执行意图。voice 和 keyboard 共用。 */
    private void processText(String text) {
        recognizedTextLabel.setText("识别文本: " + text);
        ParsedResult result = nlpProcessor.parse(text);
        intentLabel.setText(String.format("意图: %s (置信度: %.1f)", result.getIntent(), result.getConfidence()));

        String sysResp = executeIntent(result);
        responseLabel.setText("系统: " + sysResp);
        statusLabel.setText("识别完成");
    }

    /** 根据 NLP 解析结果执行对应操作，返回系统提示文本。 */
    private String executeIntent(ParsedResult result) {
        if (calendarService == null) return "日历服务未初始化";

        if (result.isAmbiguous()) {
            return result.getClarificationQuestion();
        }

        switch (result.getIntent()) {
            case ADD:
                return executeAdd(result);
            case DELETE:
                return executeDelete(result);
            case QUERY:
                return executeQuery(result);
            case MODIFY:
                return "修改功能开发中";
            default:
                return "无法识别的操作";
        }
    }

    @SuppressWarnings("unchecked")
    private String executeAdd(ParsedResult result) {
        Object timeObj = result.getEntities().get("timeRange");
        if (timeObj == null) return "未识别到时间，请重新说话";

        LocalDateTime[] timeRange = (LocalDateTime[]) timeObj;
        String title = (String) result.getEntities().get("title");
        if (title == null || title.isEmpty()) title = "未命名事件";

        CalendarEvent event = new CalendarEvent(title, timeRange[0], timeRange[1]);

        boolean success = calendarService.addEvent(event);
        if (success) {
            return String.format("已添加「%s」%s", title, timeRange[0].toLocalTime().toString().substring(0, 5));
        } else {
            return "时间与现有事件冲突，请换个时间";
        }
    }

    @SuppressWarnings("unchecked")
    private String executeDelete(ParsedResult result) {
        Object timeObj = result.getEntities().get("timeRange");
        LocalDateTime[] timeRange = (LocalDateTime[]) timeObj;
        String keyword = (String) result.getEntities().get("title");

        if (timeRange == null && keyword == null) return "请指定要删除的事件";

        List<CalendarEvent> candidates;
        if (timeRange != null && keyword != null) {
            candidates = calendarService.queryByTimeAndTitle(timeRange[0], keyword);
        } else if (timeRange != null) {
            candidates = calendarService.queryByTimeRange(timeRange[0], timeRange[1]);
        } else {
            candidates = calendarService.queryByKeyword(keyword);
        }

        if (candidates.isEmpty()) return "没有找到匹配的事件";

        if (candidates.size() == 1) {
            calendarService.deleteEvent(candidates.get(0).getId());
            return "已删除「" + candidates.get(0).getTitle() + "」";
        }

        return String.format("找到 %d 个匹配事件，请告诉我要删除哪一个", candidates.size());
    }

    private String executeQuery(ParsedResult result) {
        Object timeObj = result.getEntities().get("timeRange");
        LocalDateTime[] timeRange = (LocalDateTime[]) timeObj;

        List<CalendarEvent> events;
        if (timeRange != null) {
            events = calendarService.queryByTimeRange(timeRange[0], timeRange[1]);
        } else {
            events = calendarService.getAllEvents();
        }

        if (events.isEmpty()) return "没有找到事件";
        if (events.size() == 1) {
            CalendarEvent e = events.get(0);
            return String.format("找到 1 个事件：%s，%s", e.getTitle(),
                    e.getStartTime().toLocalTime().toString().substring(0, 5));
        }
        return String.format("共找到 %d 个事件", events.size());
    }
}
