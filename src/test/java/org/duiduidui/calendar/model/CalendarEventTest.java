package org.duiduidui.calendar.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class CalendarEventTest {

    @Test
    void testCreateWithBasicConstructor() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 30, 15, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 30, 16, 0);
        CalendarEvent event = new CalendarEvent("测试会议", start, end);

        assertNotNull(event.getId());
        assertEquals("测试会议", event.getTitle());
        assertEquals(start, event.getStartTime());
        assertEquals(end, event.getEndTime());
        assertEquals(start.minusMinutes(15), event.getRemindTime());
        assertFalse(event.isReminded());
    }

    @Test
    void testDefaultRemindTime() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 30, 15, 0);
        assertEquals(start.minusMinutes(15), CalendarEvent.defaultRemindTime(start));
        assertNull(CalendarEvent.defaultRemindTime(null));
    }

    @Test
    void testFullConstructor() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 30, 15, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 30, 16, 0);
        LocalDateTime remind = start.minusMinutes(10);
        CalendarEvent event = new CalendarEvent("id-1", "会议", start, end,
                "会议室A", "项目评审", remind, true);

        assertEquals("id-1", event.getId());
        assertEquals("会议", event.getTitle());
        assertEquals("会议室A", event.getLocation());
        assertEquals("项目评审", event.getDescription());
        assertEquals(remind, event.getRemindTime());
        assertTrue(event.isReminded());
    }

    @Test
    void testSetters() {
        CalendarEvent event = new CalendarEvent();
        event.setId("abc");
        event.setTitle("新标题");
        event.setReminded(true);

        assertEquals("abc", event.getId());
        assertEquals("新标题", event.getTitle());
        assertTrue(event.isReminded());
    }
}
