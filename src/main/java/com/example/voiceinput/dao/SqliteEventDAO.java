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
        "  FOREIGN KEY (user_id) REFERENCES users(id)" +
        ")";

    private final Connection connection;
    private long currentUserId = 0;

    public SqliteEventDAO() {
        try {
            String dbPath = System.getProperty("user.dir") + "/calendar.db";
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE);
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(calendar_events)");
                StringBuilder cols = new StringBuilder("[EventDAO] 列: ");
                while (rs.next()) cols.append(rs.getString("name")).append(" ");
                System.out.println(cols.toString().trim());
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    public void setCurrentUser(long userId) { this.currentUserId = userId; }

    @Override public void initialize() {}

    @Override
    public void insert(CalendarEvent event) {
        String sql = "INSERT INTO calendar_events (id, title, start_time, end_time, location, description, remind_time, reminded, user_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getId());
            ps.setString(2, event.getTitle());
            ps.setString(3, event.getStartTime().format(FMT));
            ps.setString(4, event.getEndTime() != null ? event.getEndTime().format(FMT) : null);
            ps.setString(5, event.getLocation());
            ps.setString(6, event.getDescription());
            ps.setString(7, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
            ps.setInt(8, event.isReminded() ? 1 : 0);
            ps.setLong(9, currentUserId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("插入失败", e); }
    }

    @Override
    public void insertAll(List<CalendarEvent> events) {
        for (CalendarEvent e : events) insert(e);
    }

    @Override
    public boolean deleteById(String id) {
        String sql = currentUserId > 0
            ? "DELETE FROM calendar_events WHERE id = ? AND user_id = ?"
            : "DELETE FROM calendar_events WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            if (currentUserId > 0) ps.setLong(2, currentUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException("删除失败", e); }
    }

    @Override public boolean update(CalendarEvent event) { return false; }
    @Override public CalendarEvent findById(String id) { return null; }

    @Override
    public List<CalendarEvent> findAll() {
        if (currentUserId == 0) return List.of();
        return queryList("SELECT * FROM calendar_events WHERE user_id = ? ORDER BY start_time", currentUserId);
    }

    @Override
    public List<CalendarEvent> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        if (currentUserId == 0) return List.of();
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND start_time < ? AND COALESCE(end_time, start_time) > ? ORDER BY start_time",
            currentUserId, end.format(FMT), start.format(FMT));
    }

    @Override
    public List<CalendarEvent> findByKeyword(String keyword) {
        if (currentUserId == 0) return List.of();
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND (title LIKE ? OR description LIKE ?) ORDER BY start_time",
            currentUserId, "%" + keyword + "%", "%" + keyword + "%");
    }

    @Override
    public List<CalendarEvent> findByTimeAndTitle(LocalDateTime time, String titleKeyword) {
        if (currentUserId == 0) return List.of();
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND start_time LIKE ? AND title LIKE ? ORDER BY start_time",
            currentUserId, time.format(DateTimeFormatter.ISO_LOCAL_DATE) + "%", "%" + titleKeyword + "%");
    }

    @Override
    public List<CalendarEvent> findUpcomingReminders(LocalDateTime now, int minutesAhead) {
        if (currentUserId == 0) return List.of();
        LocalDateTime end = now.plusMinutes(minutesAhead);
        return queryList(
            "SELECT * FROM calendar_events WHERE user_id = ? AND remind_time >= ? AND remind_time < ? AND reminded = 0",
            currentUserId, now.format(FMT), end.format(FMT));
    }

    @Override
    public void markReminded(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE calendar_events SET reminded = 1 WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setLong(2, currentUserId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("标记失败", e); }
    }

    @Override public int count() {
        if (currentUserId == 0) return 0;
        return queryInt("SELECT COUNT(*) FROM calendar_events WHERE user_id = ?", currentUserId);
    }

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

    private int queryInt(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) { return 0; }
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        return new CalendarEvent(
            rs.getString("id"),
            rs.getString("title"),
            LocalDateTime.parse(rs.getString("start_time"), FMT),
            rs.getString("end_time") != null ? LocalDateTime.parse(rs.getString("end_time"), FMT) : null,
            rs.getString("location"),
            rs.getString("description"),
            rs.getString("remind_time") != null ? LocalDateTime.parse(rs.getString("remind_time"), FMT) : null,
            rs.getInt("reminded") == 1
        );
    }
}
