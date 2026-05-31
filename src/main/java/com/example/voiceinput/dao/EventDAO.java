package com.example.voiceinput.dao;

import com.example.voiceinput.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

public interface EventDAO {
    void initialize();
    void insert(CalendarEvent event);
    void insertAll(List<CalendarEvent> events);
    boolean deleteById(String id);
    boolean update(CalendarEvent event);
    CalendarEvent findById(String id);
    List<CalendarEvent> findAll();
    List<CalendarEvent> findByTimeRange(LocalDateTime start, LocalDateTime end);
    List<CalendarEvent> findByKeyword(String keyword);
    List<CalendarEvent> findByTimeAndTitle(LocalDateTime time, String titleKeyword);
    List<CalendarEvent> findUpcomingReminders(LocalDateTime now, int minutesAhead);
    void markReminded(String id);
    int count();
    void shutdown();
}
