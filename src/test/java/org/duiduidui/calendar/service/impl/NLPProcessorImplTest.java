package org.duiduidui.calendar.service.impl;

import org.duiduidui.calendar.model.IntentType;
import org.duiduidui.calendar.model.ParsedResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NLPProcessorImplTest {

    private final NLPProcessorImpl processor = new NLPProcessorImpl();

    @Test
    void testAddIntent() {
        ParsedResult result = processor.parse("添加明天下午三点的会议");
        assertEquals(IntentType.ADD, result.getIntent());
        assertTrue(result.isConfident());

        LocalDateTime[] timeRange = (LocalDateTime[]) result.getEntities().get("timeRange");
        assertNotNull(timeRange);
        assertEquals(2, timeRange.length);
        // 验证时间是明天下午3点到4点
        LocalDateTime now = LocalDateTime.now();
        assertEquals(now.getDayOfYear() + 1, timeRange[0].getDayOfYear()); // 明天
        assertEquals(15, timeRange[0].getHour()); // 下午3点
        assertEquals(0, timeRange[0].getMinute());
    }

    @Test
    void testDeleteIntent() {
        ParsedResult result = processor.parse("删除明天下午三点的会议");
        assertEquals(IntentType.DELETE, result.getIntent());
        assertNotNull(result.getEntities().get("timeRange"));
    }

    @Test
    void testQueryIntent() {
        ParsedResult result = processor.parse("查看今天有什么会议");
        assertEquals(IntentType.QUERY, result.getIntent());
        assertNotNull(result.getEntities().get("timeRange"));
    }

    @Test
    void testModifyIntent() {
        ParsedResult result = processor.parse("改到明天下午四点");
        assertEquals(IntentType.MODIFY, result.getIntent());
        assertTrue(result.isConfident());
    }

    @Test
    void testTitleExtraction() {
        ParsedResult result = processor.parse("添加明天下午三点和客户讨论需求");
        assertEquals(IntentType.ADD, result.getIntent());
        String title = (String) result.getEntities().get("title");
        assertNotNull(title);
        assertTrue(title.contains("客户"));
    }

    @Test
    void testUnknownIntent() {
        ParsedResult result = processor.parse("你好");
        assertEquals(IntentType.UNKNOWN, result.getIntent());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void testEmptyText() {
        ParsedResult result = processor.parse("");
        assertEquals(IntentType.UNKNOWN, result.getIntent());
    }

    @Test
    void testConfirmationFirst() {
        // 需要传入候选列表
        ParsedResult result = processor.parseConfirmation("第一个", java.util.List.of(new org.duiduidui.calendar.model.CalendarEvent("test", java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(1))));
        assertEquals(0.9, result.getConfidence());
    }

    @Test
    void testWeeklyTime() {
        // 每周三的解析（Natty 会返回下一个周三）
        ParsedResult result = processor.parse("添加每周三下午三点的例会");
        assertEquals(IntentType.ADD, result.getIntent());
        LocalDateTime[] timeRange = (LocalDateTime[]) result.getEntities().get("timeRange");
        assertNotNull(timeRange);
        assertEquals(15, timeRange[0].getHour());
        assertEquals(0, timeRange[0].getMinute());
        // 验证结果是周三
        assertEquals(3, timeRange[0].getDayOfWeek().getValue());
    }
}
