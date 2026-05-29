package org.duiduidui.calendar.dao;

import org.duiduidui.calendar.model.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据访问接口 —— 对标 Spring Boot 的 Mapper 层。
 */
public interface EventDAO {

    /** 初始化数据库连接，建表。 */
    void initialize();

    /** 插入事件。 */
    void insert(CalendarEvent event);

    /** 批量插入（同一事务）。 */
    void insertAll(List<CalendarEvent> events);

    /** 按 ID 删除。 */
    boolean deleteById(String id);

    /** 更新事件。 */
    boolean update(CalendarEvent event);

    /** 按 ID 查询。 */
    CalendarEvent findById(String id);

    /** 查询全部。 */
    List<CalendarEvent> findAll();

    /** 按时间范围查询（半开区间 [start, end)）。 */
    List<CalendarEvent> findByTimeRange(LocalDateTime start, LocalDateTime end);

    /** 关键词模糊匹配。 */
    List<CalendarEvent> findByKeyword(String keyword);

    /** 按时间 + 标题模糊匹配。 */
    List<CalendarEvent> findByTimeAndTitle(LocalDateTime time, String titleKeyword);

    /** 查询未提醒且在时间窗口内的事件。 */
    List<CalendarEvent> findUpcomingReminders(LocalDateTime now, int minutesAhead);

    /** 标记已提醒。 */
    void markReminded(String id);

    /** 事件总数。 */
    int count();

    /** 关闭连接。 */
    void shutdown();
}
