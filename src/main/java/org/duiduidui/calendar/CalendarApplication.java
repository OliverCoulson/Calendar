package org.duiduidui.calendar;

import javafx.application.Application;
import javafx.stage.Stage;
import org.duiduidui.calendar.controller.MainViewController;

public class CalendarApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainViewController mainController = new MainViewController();
        mainController.initUI(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
