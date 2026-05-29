package org.duiduidui.calendar.dao;

import org.duiduidui.calendar.model.CalendarEvent;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteEventDAOTest {

    private SqliteEventDAO dao;

    @BeforeEach
    void setUp() {
        dao = new SqliteEventDAO("jdbc:sqlite:");
        dao.initialize();
    }

    @AfterEach
    void tearDown() {
        dao.shutdown();
    }

    @Test
    void testInitialize() {
        // initialize() 已在 setUp 调用，验证无异常即可
        assertDoesNotThrow(() -> dao.count());
    }

    @Test
    void testInsertAndFindById() {
        CalendarEvent event = createEvent("测试事件", "15:00", "16:00");
        dao.insert(event);

        CalendarEvent found = dao.findById(event.getId());
        assertNotNull(found);
        assertEquals("测试事件", found.getTitle());
    }

    @Test
    void testInsertAllAndFindAll() {
        dao.insertAll(List.of(
                createEvent("事件1", "10:00", "11:00"),
                createEvent("事件2", "14:00", "15:00")
        ));
        assertEquals(2, dao.count());
        assertEquals(2, dao.findAll().size());
    }

    @Test
    void testDeleteById() {
        CalendarEvent event = createEvent("待删除", "15:00", "16:00");
        dao.insert(event);

        assertTrue(dao.deleteById(event.getId()));
        assertNull(dao.findById(event.getId()));
        assertFalse(dao.deleteById("nonexistent"));
    }

    @Test
    void testUpdate() {
        CalendarEvent event = createEvent("原标题", "15:00", "16:00");
        dao.insert(event);

        event.setTitle("新标题");
        dao.update(event);

        CalendarEvent updated = dao.findById(event.getId());
        assertEquals("新标题", updated.getTitle());
    }

    @Test
    void testFindByTimeRange() {
        // 10:00-11:00 的事件
        CalendarEvent e1 = createEvent("上午", "10:00", "11:00");
        // 14:00-15:00 的事件
        CalendarEvent e2 = createEvent("下午", "14:00", "15:00");
        dao.insertAll(List.of(e1, e2));

        // 查询 12:00-18:00，应只返回下午事件
        List<CalendarEvent> result = dao.findByTimeRange(
                LocalDateTime.of(2026, 5, 30, 12, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0)
        );
        assertEquals(1, result.size());
        assertEquals("下午", result.get(0).getTitle());
    }

    @Test
    void testFindByKeyword() {
        dao.insertAll(List.of(
                createEvent("项目评审", "10:00", "11:00"),
                createEvent("客户会议", "14:00", "15:00"),
                createEvent("周报填写", "16:00", "17:00")
        ));

        List<CalendarEvent> result = dao.findByKeyword("会议");
        assertEquals(1, result.size());
        assertEquals("客户会议", result.get(0).getTitle());
    }

    @Test
    void testFindByTimeAndTitle() {
        dao.insert(createEvent("测试会议", "15:00", "16:00"));

        List<CalendarEvent> result = dao.findByTimeAndTitle(
                LocalDateTime.of(2026, 5, 30, 0, 0), "测试");
        assertEquals(1, result.size());
    }

    @Test
    void testFindUpcomingReminders() {
        CalendarEvent event = createEvent("提醒测试", "16:00", "17:00");
        event.setRemindTime(LocalDateTime.of(2026, 5, 30, 15, 50)); // 10分钟前提醒
        event.setReminded(false);
        dao.insert(event);

        List<CalendarEvent> reminders = dao.findUpcomingReminders(
                LocalDateTime.of(2026, 5, 30, 15, 48), 5);
        assertEquals(1, reminders.size());
    }

    @Test
    void testMarkReminded() {
        CalendarEvent event = createEvent("标记提醒", "15:00", "16:00");
        dao.insert(event);
        assertFalse(dao.findById(event.getId()).isReminded());

        dao.markReminded(event.getId());
        assertTrue(dao.findById(event.getId()).isReminded());
    }

    @Test
    void testCount() {
        assertEquals(0, dao.count());
        dao.insert(createEvent("事件1", "10:00", "11:00"));
        assertEquals(1, dao.count());
        dao.insert(createEvent("事件2", "14:00", "15:00"));
        assertEquals(2, dao.count());
    }

    private CalendarEvent createEvent(String title, String startTime, String endTime) {
        return createEvent(title, LocalDateTime.parse("2026-05-30T" + startTime),
                LocalDateTime.parse("2026-05-30T" + endTime));
    }

    private CalendarEvent createEvent(String title, LocalDateTime start, LocalDateTime end) {
        CalendarEvent event = new CalendarEvent(title, start, end);
        return event;
    }
}
