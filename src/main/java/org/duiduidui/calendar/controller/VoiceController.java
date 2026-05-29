package org.duiduidui.calendar.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.duiduidui.calendar.config.AppConfig;
import org.duiduidui.calendar.model.ParsedResult;
import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.service.NLPProcessor;
import org.duiduidui.calendar.service.RecognitionListener;
import org.duiduidui.calendar.service.RecordingListener;
import org.duiduidui.calendar.service.VoiceService;
import org.duiduidui.calendar.service.impl.MockVoiceService;
import org.duiduidui.calendar.service.impl.NLPProcessorImpl;
import org.duiduidui.calendar.service.impl.VoiceServiceImpl;

/**
 * 语音交互 Controller — 负责录音、识别、NLP 解析的完整流程。
 */
public class VoiceController {

    private final VoiceService voiceService;
    private final NLPProcessor nlpProcessor = new NLPProcessorImpl();

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
                Platform.runLater(() -> {
                    recognizedTextLabel.setText("识别文本: " + text);
                    ParsedResult result = nlpProcessor.parse(text);
                    intentLabel.setText(String.format("意图: %s (置信度: %.1f)", result.getIntent(), result.getConfidence()));
                    responseLabel.setText(result.isAmbiguous()
                            ? "系统: " + result.getClarificationQuestion()
                            : "系统: 解析完成，准备执行" + translateIntent(result.getIntent()));
                    statusLabel.setText("识别完成");
                });
            }
            @Override public void onError(VoiceException e) {
                Platform.runLater(() -> statusLabel.setText("识别失败: " + e.getMessage()));
            }
        });
    }

    public void bindLabels(Label statusLabel, Label recognizedTextLabel, Label intentLabel, Label responseLabel) {
        this.statusLabel = statusLabel;
        this.recognizedTextLabel = recognizedTextLabel;
        this.intentLabel = intentLabel;
        this.responseLabel = responseLabel;
    }

    /** 语音按钮点击事件。 */
    public void onVoiceButtonClick() {
        new Thread(() -> {
            try {
                voiceService.startListening();
            } catch (VoiceException e) {
                Platform.runLater(() -> statusLabel.setText("错误: " + e.getMessage()));
            }
        }, "voice-thread").start();
    }

    private String translateIntent(org.duiduidui.calendar.model.IntentType intent) {
        switch (intent) {
            case ADD:    return "添加操作";
            case DELETE: return "删除操作";
            case QUERY:  return "查询操作";
            case MODIFY: return "修改操作";
            default:     return "";
        }
    }
}
