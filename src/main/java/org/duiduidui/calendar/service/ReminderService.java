package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.CalendarEvent;

public interface ReminderService {

    /** 启动后台扫描线程，每 30 秒执行一次。 */
    void start();

    /** 停止后台扫描。 */
    void stop();

    /** 注册提醒触发回调。 */
    void addReminderListener(ReminderListener listener);
}
