package org.duiduidui.calendar.service.impl;

import org.duiduidui.calendar.dao.EventDAO;
import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.service.CalendarService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        if (event.getRemindTime() == null) {
            event.setRemindTime(CalendarEvent.defaultRemindTime(event.getStartTime()));
        }

        // 冲突检测
        List<CalendarEvent> conflicts = eventDAO.findByTimeRange(event.getStartTime(), event.getEndTime());
        if (!conflicts.isEmpty()) {
            return false;
        }

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
