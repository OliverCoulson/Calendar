package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.CalendarEvent;

public interface ReminderListener {
    void onReminderTriggered(CalendarEvent event);
}
