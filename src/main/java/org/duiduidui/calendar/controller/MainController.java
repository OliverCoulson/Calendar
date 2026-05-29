package org.duiduidui.calendar.controller;

import javafx.application.Platform;
import javafx.scene.control.Label;
import org.duiduidui.calendar.config.AppConfig;
import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.service.*;

/**
 * 主界面控制器 — 处理语音按钮点击、界面更新。
 * 自动根据配置选择 MockVoiceService 或 VoiceServiceImpl。
 */
public class MainController {

    private final VoiceService voiceService;

    private Label statusLabel;
    private Label recognizedTextLabel;
    private Label responseLabel;

    public MainController() {
        AppConfig config = new AppConfig();
        String appKey = config.getAliyunAppKey();
        String accessKeyId = config.getAliyunAccessKeyId();
        String accessKeySecret = config.getAliyunAccessKeySecret();

        if (appKey != null && !appKey.isEmpty()
                && accessKeyId != null && !accessKeyId.isEmpty()
                && accessKeySecret != null && !accessKeySecret.isEmpty()) {
            voiceService = new VoiceServiceImpl(appKey, accessKeyId, accessKeySecret, config.getAliyunGateway());
            System.out.println("[MainController] 使用阿里云语音服务");
        } else {
            voiceService = new MockVoiceService();
            System.out.println("[MainController] 使用模拟语音服务（未配置阿里云 Key）");
        }

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
