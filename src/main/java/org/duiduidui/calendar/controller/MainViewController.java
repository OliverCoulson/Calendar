package org.duiduidui.calendar.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.duiduidui.calendar.config.AppConfig;
import org.duiduidui.calendar.dao.SqliteEventDAO;
import org.duiduidui.calendar.service.CalendarService;
import org.duiduidui.calendar.service.impl.CalendarServiceImpl;

/**
 * 主窗口 Controller — 组装子 Controller，初始化各服务。
 * debug 模式显示键盘输入，正常模式显示语音按钮。
 */
public class MainViewController {

    private final AppConfig config = new AppConfig();
    private final boolean debugMode;
    private final CalendarService calendarService;
    private final VoiceController voiceController = new VoiceController();
    private final EventController eventController = new EventController();

    public MainViewController() {
        this.debugMode = config.isDebugMode();
        System.out.println("[App] debug.mode=" + debugMode);

        SqliteEventDAO dao = new SqliteEventDAO("jdbc:sqlite:" + config.getDbPath());
        dao.initialize();
        this.calendarService = new CalendarServiceImpl(dao);
        voiceController.setCalendarService(calendarService);
        eventController.setCalendarService(calendarService);
    }

    public void initUI(Stage primaryStage) {
        Label statusLabel = new Label("点击下方按钮开始语音输入");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Label recognizedTextLabel = new Label("识别文本: ");
        recognizedTextLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label intentLabel = new Label("意图: ");
        intentLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2E7D32;");

        Label responseLabel = new Label("系统响应: ");
        responseLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

        voiceController.bindLabels(statusLabel, recognizedTextLabel, intentLabel, responseLabel);

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        if (debugMode) {
            // debug 模式：键盘输入
            statusLabel.setText("键盘输入模式 — 输入文字后回车或点提交");
            TextField textInput = new TextField();
            textInput.setPromptText("输入指令，例如：添加明天下午三点的会议");
            textInput.setStyle("-fx-font-size: 14px; -fx-padding: 8;");
            textInput.setPrefWidth(400);

            Button submitBtn = new Button("提交");
            submitBtn.setStyle(
                    "-fx-font-size: 14px;" +
                    "-fx-padding: 8 20;" +
                    "-fx-background-color: #4A90D9;" +
                    "-fx-text-fill: white;"
            );

            submitBtn.setOnAction(e -> {
                String text = textInput.getText().trim();
                if (!text.isEmpty()) {
                    voiceController.onTextSubmit(text);
                    textInput.clear();
                }
            });
            textInput.setOnAction(e -> submitBtn.fire());

            HBox inputRow = new HBox(8, textInput, submitBtn);
            inputRow.setAlignment(Pos.CENTER);
            root.getChildren().addAll(statusLabel, recognizedTextLabel, intentLabel, responseLabel, inputRow);

        } else {
            // 正常模式：语音输入
            Button voiceButton = new Button("🎤 语音输入");
            voiceButton.setStyle(
                    "-fx-font-size: 18px;" +
                    "-fx-padding: 12 24;" +
                    "-fx-background-color: #4A90D9;" +
                    "-fx-text-fill: white;" +
                    "-fx-border-radius: 8;" +
                    "-fx-background-radius: 8;"
            );
            voiceButton.setOnAction(e -> voiceController.onVoiceButtonClick());
            root.getChildren().addAll(statusLabel, recognizedTextLabel, intentLabel, responseLabel, voiceButton);
        }

        primaryStage.setTitle("语音日历工具" + (debugMode ? " (调试模式)" : ""));
        primaryStage.setScene(new Scene(root, 560, 380));
        primaryStage.show();
    }
}
