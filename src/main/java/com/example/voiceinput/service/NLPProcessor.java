package com.example.voiceinput.service;

import com.example.voiceinput.model.CalendarEvent;
import com.example.voiceinput.model.ParsedResult;
import java.util.List;

public interface NLPProcessor {
    ParsedResult parse(String text);
    ParsedResult parseConfirmation(String text, List<CalendarEvent> candidates);
}
