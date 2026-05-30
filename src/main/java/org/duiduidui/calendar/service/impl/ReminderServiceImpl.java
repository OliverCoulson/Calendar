package org.duiduidui.calendar.service.impl;

import org.duiduidui.calendar.model.CalendarEvent;
import org.duiduidui.calendar.service.CalendarService;
import org.duiduidui.calendar.service.ReminderListener;
import org.duiduidui.calendar.service.ReminderService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 提醒服务 — 后台每 30 秒扫描一次，触发到期提醒。
 */
public class ReminderServiceImpl implements ReminderService {

    private final CalendarService calendarService;
    private final List<ReminderListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    public ReminderServiceImpl(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Override
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reminder-scanner");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::scan, 0, 30, TimeUnit.SECONDS);
        System.out.println("[Reminder] 扫描线程已启动（每 30 秒）");
    }

    @Override
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("[Reminder] 扫描线程已停止");
        }
    }

    @Override
    public void addReminderListener(ReminderListener listener) {
        listeners.add(listener);
    }

    private void scan() {
        try {
            List<CalendarEvent> dueEvents = calendarService.getUpcomingReminders(LocalDateTime.now(), 1);
            for (CalendarEvent event : dueEvents) {
                // 触发回调（UI 弹窗 + TTS）
                for (ReminderListener listener : listeners) {
                    listener.onReminderTriggered(event);
                }
                // 标记已提醒
                calendarService.acknowledgeReminder(event.getId());
                System.out.println("[Reminder] 已触发: " + event.getTitle());
            }
        } catch (Exception e) {
            System.err.println("[Reminder] 扫描异常: " + e.getMessage());
        }
    }
}
