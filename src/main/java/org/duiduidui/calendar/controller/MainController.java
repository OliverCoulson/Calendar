package org.duiduidui.calendar.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.service.MockVoiceService;
import org.duiduidui.calendar.service.RecognitionListener;
import org.duiduidui.calendar.service.RecordingListener;
import org.duiduidui.calendar.service.VoiceService;

/**
 * 主界面控制器 — 处理语音按钮点击、界面更新。
 */
public class MainController {

    private final VoiceService voiceService = new MockVoiceService();

    private Label statusLabel;
    private Label recognizedTextLabel;
    private Label responseLabel;

    public MainController() {
        voiceService.addRecordingListener(new RecordingListener() {
            @Override public void onRecordingStart() {
                Platform.runLater(() -> statusLabel.setText("录音中... 请说话"));
            }
            @Override public void onRecordingEnd() {
                Platform.runLater(() -> statusLabel.setText("识别中，请稍候..."));
            }
            @Override public void onRecordingCancel() {
                Platform.runLater(() -> statusLabel.setText("已取消"));
            }
            @Override public void onError(String message) {
                Platform.runLater(() -> statusLabel.setText("错误: " + message));
            }
        });

        voiceService.addRecognitionListener(new RecognitionListener() {
            @Override public void onSuccess(String text) {
                Platform.runLater(() -> {
                    recognizedTextLabel.setText("识别文本: " + text);
                    statusLabel.setText("识别完成");
                });
            }
            @Override public void onError(VoiceException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("识别失败: " + e.getMessage());
                });
            }
        });
    }

    public void setStatusLabel(Label label) { this.statusLabel = label; }
    public void setRecognizedTextLabel(Label label) { this.recognizedTextLabel = label; }
    public void setResponseLabel(Label label) { this.responseLabel = label; }

    /** 语音按钮点击事件。 */
    public void onVoiceButtonClick() {
        new Thread(() -> {
            try {
                voiceService.speak("开始录音，请说话");
                String text = voiceService.startListening();
                Platform.runLater(() -> recognizedTextLabel.setText("识别文本: " + text));
            } catch (VoiceException e) {
                Platform.runLater(() -> statusLabel.setText("错误: " + e.getMessage()));
            }
        }, "voice-thread").start();
    }
}
