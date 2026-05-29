package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarService {

    /** 添加事件（含冲突检测），无冲突返回 true。 */
    boolean addEvent(CalendarEvent event);

    /** 按 ID 删除。 */
    boolean deleteEvent(String id);

    /** 按时间范围查询。 */
    List<CalendarEvent> queryByTimeRange(LocalDateTime start, LocalDateTime end);

    /** 按关键词查询。 */
    List<CalendarEvent> queryByKeyword(String keyword);

    /** 按时间 + 标题模糊查询（用于语音删除定位候选）。 */
    List<CalendarEvent> queryByTimeAndTitle(LocalDateTime time, String titleKeyword);

    /** 获取全部事件。 */
    List<CalendarEvent> getAllEvents();

    /** 获取即将提醒的事件。 */
    List<CalendarEvent> getUpcomingReminders(LocalDateTime now, int minutesAhead);

    /** 标记事件已提醒。 */
    void acknowledgeReminder(String eventId);
}
