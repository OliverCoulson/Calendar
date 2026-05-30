package org.duiduidui.calendar.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.duiduidui.calendar.config.AppConfig;
import org.duiduidui.calendar.dao.SqliteEventDAO;
import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.service.CalendarService;
import org.duiduidui.calendar.service.ReminderListener;
import org.duiduidui.calendar.service.ReminderService;
import org.duiduidui.calendar.service.impl.CalendarServiceImpl;
import org.duiduidui.calendar.service.impl.ReminderServiceImpl;

public class MainViewController {

    private final AppConfig config = new AppConfig();
    private final boolean debugMode;
    private final CalendarService calendarService;
    private final VoiceController voiceController = new VoiceController();
    private final EventController eventController = new EventController();
    private final ReminderService reminderService;

    private Stage primaryStage;

    public MainViewController() {
        this.debugMode = config.isDebugMode();
        System.out.println("[App] debug.mode=" + debugMode);

        SqliteEventDAO dao = new SqliteEventDAO("jdbc:sqlite:" + config.getDbPath());
        dao.initialize();
        this.calendarService = new CalendarServiceImpl(dao);
        voiceController.setCalendarService(calendarService);
        eventController.setCalendarService(calendarService);

        // 提醒扫描
        this.reminderService = new ReminderServiceImpl(calendarService);
        reminderService.addReminderListener(event -> {
            Platform.runLater(() -> showReminderPopup(event));
        });
        reminderService.start();
    }

    public void initUI(Stage primaryStage) {
        this.primaryStage = primaryStage;

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

    /** 右下角弹出提醒通知。 */
    private void showReminderPopup(CalendarEvent event) {
        Stage popup = new Stage();
        popup.setTitle("提醒");
        popup.setAlwaysOnTop(true);

        Label titleLabel = new Label(event.getTitle());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #C62828;");

        Label timeLabel = new Label("⏰ " + event.getStartTime().toLocalTime().toString().substring(0, 5));
        timeLabel.setStyle("-fx-font-size: 14px;");

        Label descLabel = new Label(event.getDescription() != null ? event.getDescription() : "");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        Button dismissBtn = new Button("知道了");
        dismissBtn.setOnAction(e -> popup.close());

        VBox popupRoot = new VBox(10, titleLabel, timeLabel, descLabel, dismissBtn);
        popupRoot.setPadding(new Insets(20));
        popupRoot.setStyle("-fx-background-color: #FFF3E0; -fx-border-color: #FFB74D; -fx-border-width: 2;");

        Scene scene = new Scene(popupRoot, 280, 180);
        popup.setScene(scene);
        popup.show();
    }
}
