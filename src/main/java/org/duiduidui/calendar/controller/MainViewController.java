package org.duiduidui.calendar.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 主窗口 Controller — 组装子 Controller，管理整体布局。
 */
public class MainViewController {

    private final VoiceController voiceController = new VoiceController();
    private final EventController eventController = new EventController();

    public void initUI(Stage primaryStage) {
        // ---- 语音反馈区 ----
        Label statusLabel = new Label("点击下方按钮开始语音输入");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Label recognizedTextLabel = new Label("识别文本: ");
        recognizedTextLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label intentLabel = new Label("意图: ");
        intentLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2E7D32;");

        Label responseLabel = new Label("系统响应: ");
        responseLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

        // ---- 语音按钮 ----
        Button voiceButton = new Button("🎤 语音输入");
        voiceButton.setStyle(
                "-fx-font-size: 18px;" +
                "-fx-padding: 12 24;" +
                "-fx-background-color: #4A90D9;" +
                "-fx-text-fill: white;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;"
        );

        // ---- 组装 ----
        voiceController.bindLabels(statusLabel, recognizedTextLabel, intentLabel, responseLabel);
        voiceButton.setOnAction(e -> voiceController.onVoiceButtonClick());

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getChildren().addAll(statusLabel, recognizedTextLabel, intentLabel, responseLabel, voiceButton);

        Scene scene = new Scene(root, 520, 360);
        primaryStage.setTitle("语音日历工具");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
