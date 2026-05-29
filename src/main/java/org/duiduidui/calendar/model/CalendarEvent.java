package org.duiduidui.calendar.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class CalendarEvent {

    private String id;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String description;
    private LocalDateTime remindTime;
    private boolean reminded;

    public CalendarEvent() {}

    public CalendarEvent(String title, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.remindTime = defaultRemindTime(startTime);
    }

    public CalendarEvent(String id, String title, LocalDateTime startTime, LocalDateTime endTime,
                         String location, String description, LocalDateTime remindTime, boolean reminded) {
        this.id = id;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.description = description;
        this.remindTime = remindTime;
        this.reminded = reminded;
    }

    public static LocalDateTime defaultRemindTime(LocalDateTime startTime) {
        return startTime != null ? startTime.minusMinutes(15) : null;
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getRemindTime() { return remindTime; }
    public void setRemindTime(LocalDateTime remindTime) { this.remindTime = remindTime; }

    public boolean isReminded() { return reminded; }
    public void setReminded(boolean reminded) { this.reminded = reminded; }
}
