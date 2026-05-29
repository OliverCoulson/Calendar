package org.duiduidui.calendar.dao;

import org.duiduidui.calendar.model.CalendarEvent;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 数据访问实现 —— 对标 Spring Boot 的 Mapper XML。
 * 使用 sqlite-jdbc 直连，日期以 ISO-8601 字符串存储。
 */
public class SqliteEventDAO implements EventDAO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String CREATE_TABLE = "" +
            "CREATE TABLE IF NOT EXISTS calendar_events (" +
            "  id TEXT PRIMARY KEY," +
            "  title TEXT NOT NULL," +
            "  start_time TEXT NOT NULL," +
            "  end_time TEXT NOT NULL," +
            "  location TEXT," +
            "  description TEXT," +
            "  remind_time TEXT," +
            "  reminded INTEGER DEFAULT 0" +
            ")";

    private final String jdbcUrl;
    private Connection connection;

    public SqliteEventDAO(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE);
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    @Override
    public void insert(CalendarEvent event) {
        String sql = "INSERT INTO calendar_events (id, title, start_time, end_time, location, description, remind_time, reminded) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getId());
            ps.setString(2, event.getTitle());
            ps.setString(3, event.getStartTime().format(FMT));
            ps.setString(4, event.getEndTime().format(FMT));
            ps.setString(5, event.getLocation());
            ps.setString(6, event.getDescription());
            ps.setString(7, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
            ps.setInt(8, event.isReminded() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入事件失败", e);
        }
    }

    @Override
    public void insertAll(List<CalendarEvent> events) {
        String sql = "INSERT INTO calendar_events (id, title, start_time, end_time, location, description, remind_time, reminded) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (CalendarEvent event : events) {
                ps.setString(1, event.getId());
                ps.setString(2, event.getTitle());
                ps.setString(3, event.getStartTime().format(FMT));
                ps.setString(4, event.getEndTime().format(FMT));
                ps.setString(5, event.getLocation());
                ps.setString(6, event.getDescription());
                ps.setString(7, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
                ps.setInt(8, event.isReminded() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("批量插入事件失败", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM calendar_events WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("删除事件失败", e);
        }
    }

    @Override
    public boolean update(CalendarEvent event) {
        String sql = "UPDATE calendar_events SET title=?, start_time=?, end_time=?, location=?, " +
                     "description=?, remind_time=?, reminded=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getTitle());
            ps.setString(2, event.getStartTime().format(FMT));
            ps.setString(3, event.getEndTime().format(FMT));
            ps.setString(4, event.getLocation());
            ps.setString(5, event.getDescription());
            ps.setString(6, event.getRemindTime() != null ? event.getRemindTime().format(FMT) : null);
            ps.setInt(7, event.isReminded() ? 1 : 0);
            ps.setString(8, event.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("更新事件失败", e);
        }
    }

    @Override
    public CalendarEvent findById(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM calendar_events WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询事件失败", e);
        }
    }

    @Override
    public List<CalendarEvent> findAll() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM calendar_events ORDER BY start_time")) {
            List<CalendarEvent> list = new ArrayList<>();
            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("查询全部事件失败", e);
        }
    }

    @Override
    public List<CalendarEvent> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT * FROM calendar_events WHERE start_time < ? AND end_time > ? ORDER BY start_time";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, end.format(FMT));
            ps.setString(2, start.format(FMT));
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("按时间范围查询失败", e);
        }
    }

    @Override
    public List<CalendarEvent> findByKeyword(String keyword) {
        String sql = "SELECT * FROM calendar_events WHERE title LIKE ? OR description LIKE ? ORDER BY start_time";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("按关键词查询失败", e);
        }
    }

    @Override
    public List<CalendarEvent> findByTimeAndTitle(LocalDateTime time, String titleKeyword) {
        String sql = "SELECT * FROM calendar_events WHERE start_time LIKE ? AND title LIKE ? ORDER BY start_time";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, time.format(DateTimeFormatter.ISO_LOCAL_DATE) + "%");
            ps.setString(2, "%" + titleKeyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("按时间和标题查询失败", e);
        }
    }

    @Override
    public List<CalendarEvent> findUpcomingReminders(LocalDateTime now, int minutesAhead) {
        LocalDateTime end = now.plusMinutes(minutesAhead);
        String sql = "SELECT * FROM calendar_events WHERE remind_time >= ? AND remind_time < ? AND reminded = 0 ORDER BY remind_time";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, now.format(FMT));
            ps.setString(2, end.format(FMT));
            try (ResultSet rs = ps.executeQuery()) {
                List<CalendarEvent> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询待提醒事件失败", e);
        }
    }

    @Override
    public void markReminded(String id) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE calendar_events SET reminded = 1 WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("标记已提醒失败", e);
        }
    }

    @Override
    public int count() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM calendar_events")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("计数失败", e);
        }
    }

    @Override
    public void shutdown() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    private CalendarEvent mapRow(ResultSet rs) throws SQLException {
        return new CalendarEvent(
                rs.getString("id"),
                rs.getString("title"),
                LocalDateTime.parse(rs.getString("start_time"), FMT),
                LocalDateTime.parse(rs.getString("end_time"), FMT),
                rs.getString("location"),
                rs.getString("description"),
                rs.getString("remind_time") != null ? LocalDateTime.parse(rs.getString("remind_time"), FMT) : null,
                rs.getInt("reminded") == 1
        );
    }
}
