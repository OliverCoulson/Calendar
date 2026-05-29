package org.duiduidui.calendar;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.duiduidui.calendar.controller.MainController;

public class CalendarApplication extends Application {

    private final MainController controller = new MainController();

    @Override
    public void start(Stage primaryStage) {
        // 状态提示
        Label statusLabel = new Label("点击下方按钮开始语音输入");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        // 识别文本
        Label recognizedTextLabel = new Label("识别文本: ");
        recognizedTextLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 系统响应
        Label responseLabel = new Label("系统响应: ");
        responseLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

        // 语音按钮
        Button voiceButton = new Button("🎤 语音输入");
        voiceButton.setStyle(
                "-fx-font-size: 18px;" +
                "-fx-padding: 12 24;" +
                "-fx-background-color: #4A90D9;" +
                "-fx-text-fill: white;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;"
        );
        voiceButton.setOnAction(e -> controller.onVoiceButtonClick());

        // 连接 controller
        controller.setStatusLabel(statusLabel);
        controller.setRecognizedTextLabel(recognizedTextLabel);
        controller.setResponseLabel(responseLabel);

        // 布局
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getChildren().addAll(statusLabel, recognizedTextLabel, responseLabel, voiceButton);

        Scene scene = new Scene(root, 480, 320);
        primaryStage.setTitle("语音日历工具");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
