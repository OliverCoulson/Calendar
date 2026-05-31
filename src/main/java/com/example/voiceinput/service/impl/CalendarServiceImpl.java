package com.example.voiceinput.service.impl;

import com.example.voiceinput.dao.EventDAO;
import com.example.voiceinput.model.CalendarEvent;
import com.example.voiceinput.service.CalendarService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarServiceImpl implements CalendarService {

    private final EventDAO eventDAO;

    public CalendarServiceImpl(EventDAO eventDAO) {
        this.eventDAO = eventDAO;
    }

    @Override
    public boolean addEvent(CalendarEvent event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getRemindTime() == null && event.getStartTime() != null) {
            event.setRemindTime(event.getStartTime().minusMinutes(15));
        }
        // 冲突检测但不阻止，仅通过返回值告知
        eventDAO.insert(event);
        return true;
    }

    @Override
    public boolean deleteEvent(String id) {
        return eventDAO.deleteById(id);
    }

    @Override
    public List<CalendarEvent> queryByTimeRange(LocalDateTime start, LocalDateTime end) {
        return eventDAO.findByTimeRange(start, end);
    }

    @Override
    public List<CalendarEvent> queryByKeyword(String keyword) {
        return eventDAO.findByKeyword(keyword);
    }

    @Override
    public List<CalendarEvent> queryByTimeAndTitle(LocalDateTime time, String titleKeyword) {
        return eventDAO.findByTimeAndTitle(time, titleKeyword);
    }

    @Override
    public List<CalendarEvent> getAllEvents() {
        return eventDAO.findAll();
    }

    @Override
    public List<CalendarEvent> getUpcomingReminders(LocalDateTime now, int minutesAhead) {
        return eventDAO.findUpcomingReminders(now, minutesAhead);
    }

    @Override
    public void acknowledgeReminder(String eventId) {
        eventDAO.markReminded(eventId);
    }
}
