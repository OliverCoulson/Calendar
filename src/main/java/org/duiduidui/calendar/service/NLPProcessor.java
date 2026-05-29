package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.model.ParsedResult;

import java.util.List;

public interface NLPProcessor {

    /** 解析自然语言文本，返回结构化结果。 */
    ParsedResult parse(String text);

    /** 解析二次确认回复（如"第一个"），用于多候选选择。 */
    ParsedResult parseConfirmation(String text, List<CalendarEvent> candidates);
}
