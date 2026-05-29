package org.duiduidui.calendar.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import org.duiduidui.calendar.model.CalendarEvent;

import java.util.List;

/**
 * 事件列表 Controller — 负责事件的增删查改和列表展示。
 * 后续对接 CalendarService 后填充。
 */
public class EventController {

    private final ObservableList<CalendarEvent> eventList = FXCollections.observableArrayList();
    private ListView<CalendarEvent> listView;

    public void bindListView(ListView<CalendarEvent> listView) {
        this.listView = listView;
        this.listView.setItems(eventList);
    }

    public void loadEvents(List<CalendarEvent> events) {
        eventList.clear();
        eventList.addAll(events);
    }

    public void addEvent(CalendarEvent event) {
        eventList.add(event);
    }

    public void removeEvent(String eventId) {
        eventList.removeIf(e -> e.getId().equals(eventId));
    }
}
