package com.example.voiceinput.dao;

import com.example.voiceinput.model.CalendarEvent;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SqliteEventDAO implements EventDAO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS calendar_events (" +
        "  id TEXT PRIMARY KEY," +
        "  title TEXT NOT NULL," +
        "  start_time TEXT NOT NULL," +
        "  end_time TEXT," +
        "  location TEXT," +
        "  description TEXT," +
        "  remind_time TEXT," +
        "  reminded INTEGER DEFAULT 0," +
        "  user_id INTEGER NOT NULL," +
        "  created_by INTEGER NOT NULL DEFAULT 0," +
        "  FOREIGN KEY (user_id) REFERENCES users(id)" +
        ")";

    private final Connection connection;
    private final ThreadLocal<Long> currentUserId = ThreadLocal.withInitial(() -> 0L);
    private final ThreadLocal<Long> viewingUserId = ThreadLocal.withInitial(() -> 0L); // 监护人正在查看的老年人 ID

    public SqliteEventDAO() {
        try {
            String dbPath = System.getProperty("user.dir") + "/calendar.db";
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute(CREATE_TABLE);
                try { stmt.execute("ALTER TABLE calendar_events ADD COLUMN created_by INTEGER NOT NULL DEFAULT 0"); }
                catch (SQLException ignored) {}
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(calendar_events)");
                StringBuilder cols = new StringBuilder("[EventDAO] 列: ");
                while (rs.next()) cols.append(rs.getString("name")).append(" ");
                System.out.println(cols.toString().trim());
            }
        } catch (SQLException e) { throw new RuntimeException("数据库初始化失败", e); }
    }

    /** 设置当前操作者 */
    public void setCurrentUser(long userId) { this.currentUserId.set(userId); }
    /** 设置正在查看的目标用户（监护人查看老人日历时用） */
    public void setViewingUser(long userId) { this.viewingUserId.set(userId); }

    /** 实际写入事件的目标用户 ID */
    private long targetUserId() { long v = viewingUserId.get(); return v > 0 ? v : currentUserId.get(); }

    @Override public void initialize() {}

    // ==================== TODO: 插入事件 ====================
    /**
     * TODO: INSERT INTO calendar_events
     * 表结构: id, title, start_time, end_time, location, description, remind_time, reminded, user_id, created_by
     * user_id = targetUserId() (事件归属，监护人替老人添加时 = 老人ID)
     * created_by = currentUserId (操作者)
     */
    @Override
    public void insert(CalendarEvent event) {
        long userId = targetUserId();
        String sql = "INSERT INTO calendar_events (id, title, start_time, end_time, location, description, remind_time, reminded, user_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getId());
            ps.setString(2, event.getTitle());
            ps.setString(3, event.getStartTime() != null ? event.getStartTime().format(FMT) : null);
            ps.setString(4, event.getEndTime() != null ? event.getEndTime().format(FMT) : null);
            ps.setString(5, event.getLocation());
            ps.setString(6, event.getDescription());
            ps.setString(7, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
            ps.setInt(8, event.isReminded() ? 1 : 0);
            ps.setLong(9, userId);
            ps.setLong(10, currentUserId.get());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入事件失败", e);
        }
    }

    @Override public void insertAll(List<CalendarEvent> events) { /* TODO */ }

    // ==================== TODO: 删除事件 ====================
    /**
     * TODO: DELETE FROM calendar_events WHERE id = ? AND created_by = ?
     * 只能删除自己创建的事件
     */
    @Override
    public boolean deleteById(String id) {
        if (currentUserId.get() == 0) return false;
        String sql = "DELETE FROM calendar_events WHERE id = ? AND created_by = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setLong(2, currentUserId.get());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("删除事件失败", e);
        }
    }

    @Override
    public boolean update(CalendarEvent event) {
        if (currentUserId.get() == 0) return false;
        String sql = "UPDATE calendar_events SET title=?, start_time=?, end_time=?, location=?, description=?, remind_time=?, reminded=? WHERE id=? AND created_by=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getTitle());
            ps.setString(2, event.getStartTime() != null ? event.getStartTime().format(FMT) : null);
            ps.setString(3, event.getEndTime() != null ? event.getEndTime().format(FMT) : null);
            ps.setString(4, event.getLocation());
            ps.setString(5, event.getDescription());
            ps.setString(6, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
            ps.setInt(7, event.isReminded() ? 1 : 0);
            ps.setString(8, event.getId());
            ps.setLong(9, currentUserId.get());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("更新事件失败", e);
        }
    }
    @Override public CalendarEvent findById(String id) { return null; }

    @Override
    public List<CalendarEvent> findAll() {
        if (currentUserId.get() == 0) return List.of();
        // 查看自己的 + 监护人查看被监护人
        if (viewingUserId.get() > 0)
            return queryList("SELECT * FROM calendar_events WHERE user_id = ? ORDER BY start_time", viewingUserId.get());
        return queryList("SELECT * FROM calendar_events WHERE user_id = ? ORDER BY start_time", currentUserId.get());
    }

    @Override
    public List<CalendarEvent> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        long uid = viewingUserId.get() > 0 ? viewingUserId.get() : currentUserId.get();
        if (uid == 0) return List.of();
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND start_time < ? AND COALESCE(end_time, start_time) > ? ORDER BY start_time",
            uid, end.format(FMT), start.format(FMT));
    }

    @Override
    public List<CalendarEvent> findByKeyword(String keyword) {
        if (currentUserId.get() == 0) return List.of();
        long uid1 = currentUserId.get();
        long uid2 = viewingUserId.get() > 0 ? viewingUserId.get() : currentUserId.get();
        return queryList(
            "SELECT * FROM calendar_events WHERE (user_id = ? OR user_id = ?) AND (title LIKE ? OR description LIKE ?) ORDER BY start_time",
            uid1, uid2, "%" + keyword + "%", "%" + keyword + "%");
    }

    @Override
    public List<CalendarEvent> findByTimeAndTitle(LocalDateTime time, String titleKeyword) {
        long uid = viewingUserId.get() > 0 ? viewingUserId.get() : currentUserId.get();
        if (uid == 0) return List.of();
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND start_time LIKE ? AND title LIKE ? ORDER BY start_time",
            uid, time.format(DateTimeFormatter.ISO_LOCAL_DATE) + "%", "%" + titleKeyword + "%");
    }

    @Override
    public List<CalendarEvent> findUpcomingReminders(LocalDateTime now, int minutesAhead) {
        if (currentUserId.get() == 0) return List.of();
        LocalDateTime end = now.plusMinutes(minutesAhead);
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND remind_time >= ? AND remind_time < ? AND reminded = 0",
            currentUserId.get(), now.format(FMT), end.format(FMT));
    }

    @Override
    public void markReminded(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE calendar_events SET reminded = 1 WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id); ps.setLong(2, currentUserId.get()); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("标记失败", e); }
    }

    @Override public int count() { return 0; }
    @Override public void shutdown() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
    }

    // ---- helpers ----
    private List<CalendarEvent> queryList(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) { return List.of(); }
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        CalendarEvent evt = new CalendarEvent(
            rs.getString("id"), rs.getString("title"),
            LocalDateTime.parse(rs.getString("start_time"), FMT),
            rs.getString("end_time") != null ? LocalDateTime.parse(rs.getString("end_time"), FMT) : null,
            rs.getString("location"), rs.getString("description"),
            rs.getString("remind_time") != null ? LocalDateTime.parse(rs.getString("remind_time"), FMT) : null,
            rs.getInt("reminded") == 1
        );
        return evt;
    }
}
