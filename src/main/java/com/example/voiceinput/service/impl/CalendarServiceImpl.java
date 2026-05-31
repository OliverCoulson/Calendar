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

    // ==================== TODO: 添加事件 ====================
    /**
     * TODO: 实现添加事件逻辑
     * 1. 生成 UUID 作为 event.id
     * 2. 设置默认提醒时间（startTime 前 10 分钟）
     * 3. eventDAO.insert(event) 写入数据库
     * 参考：voice-input-backend/CalendarServiceImpl.addEvent()
     */
    @Override
    public boolean addEvent(CalendarEvent event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getRemindTime() == null && event.getStartTime() != null) {
            event.setRemindTime(event.getStartTime().minusMinutes(10));
        }
        eventDAO.insert(event);
        return true;
    }

    // ==================== TODO: 删除事件 ====================
    /**
     * TODO: 实现删除事件逻辑
     * 1. eventDAO.deleteById(id) 删除
     * DAO 层已限制 created_by = 当前用户，只能删自己创建的
     */
    @Override
    public boolean deleteEvent(String id) {
        return eventDAO.deleteById(id);
    }

    @Override
    public boolean updateEvent(CalendarEvent event) {
        return eventDAO.update(event);
    }

    // ==================== 查询事件（已实现） ====================

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
