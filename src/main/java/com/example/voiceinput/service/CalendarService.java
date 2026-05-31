package com.example.voiceinput.service;

import com.example.voiceinput.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

public interface CalendarService {
    boolean addEvent(CalendarEvent event);
    boolean deleteEvent(String id);
    List<CalendarEvent> queryByTimeRange(LocalDateTime start, LocalDateTime end);
    List<CalendarEvent> queryByKeyword(String keyword);
    List<CalendarEvent> queryByTimeAndTitle(LocalDateTime time, String titleKeyword);
    List<CalendarEvent> getAllEvents();
    List<CalendarEvent> getUpcomingReminders(LocalDateTime now, int minutesAhead);
    void acknowledgeReminder(String eventId);
}
