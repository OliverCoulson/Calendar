package com.example.voiceinput.dao;

import com.example.voiceinput.model.User;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class UserDAO {

    private final Connection connection;

    public UserDAO() {
        try {
            String dbPath = System.getProperty("user.dir") + "/calendar.db";
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        phone TEXT UNIQUE NOT NULL,
                        password TEXT NOT NULL,
                        nickname TEXT,
                        avatar TEXT DEFAULT '👤',
                        type TEXT DEFAULT 'elderly',
                        token TEXT
                    )
                """);
                // 消息表：绑定请求通知
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bind_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        from_user_id INTEGER NOT NULL,
                        to_user_id INTEGER NOT NULL,
                        alias TEXT,
                        status INTEGER DEFAULT 0,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (from_user_id) REFERENCES users(id),
                        FOREIGN KEY (to_user_id) REFERENCES users(id)
                    )
                """);
                // 关系表：已确认的绑定
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_relationships (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guardian_id INTEGER NOT NULL,
                        elderly_id INTEGER NOT NULL,
                        guardian_alias TEXT,
                        elderly_alias TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (guardian_id) REFERENCES users(id),
                        FOREIGN KEY (elderly_id) REFERENCES users(id)
                    )
                """);
            }
            System.out.println("[UserDAO] 用户表+消息表+关系表已就绪");
        } catch (SQLException e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    // ==================== 用户 CRUD ====================

    public User findByPhone(String phone) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE phone = ?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
        } catch (SQLException e) { throw new RuntimeException("查询失败", e); }
    }

    public User findById(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
        } catch (SQLException e) { return null; }
    }

    public User findByToken(String token) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapRow(rs) : null; }
        } catch (SQLException e) { return null; }
    }

    public User insert(User user) {
        String sql = "INSERT INTO users (phone, password, nickname, avatar, type, token) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getPhone()); ps.setString(2, user.getPassword());
            ps.setString(3, user.getNickname() != null ? user.getNickname() : user.getPhone());
            ps.setString(4, user.getAvatar() != null ? user.getAvatar() : "👤");
            ps.setString(5, user.getType() != null ? user.getType() : "elderly");
            ps.setString(6, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) user.setId(rs.getLong(1)); }
            return user;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) return null;
            throw new RuntimeException("注册失败", e);
        }
    }

    public void updateToken(long id, String token) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE users SET token = ? WHERE id = ?")) {
            ps.setString(1, token); ps.setLong(2, id); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("更新token失败", e); }
    }

    // ==================== 消息表：绑定请求 ====================
    // status: 0=未处理, 1=已接受, 2=已拒绝

    /** 发送绑定请求 → 写入消息表 */
    public Map<String, Object> sendBindRequest(long fromUserId, String targetPhone, String alias) {
        System.out.println("[Msg] 发送: from=" + fromUserId + " toPhone=" + targetPhone + " alias=" + alias);
        User from = findById(fromUserId);
        User to = findByPhone(targetPhone);
        if (to == null) return Map.of("success", false, "message", "该手机号未注册");
        if (from.getType().equals(to.getType()))
            return Map.of("success", false, "message", "只能绑定不同类型的用户");

        // 检查是否已绑定
        if (isAlreadyBound(from, to))
            return Map.of("success", false, "message", "已存在绑定关系");

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bind_messages (from_user_id, to_user_id, alias, status, created_at) VALUES (?,?,?,0,?)")) {
            ps.setLong(1, fromUserId); ps.setLong(2, to.getId());
            ps.setString(3, alias); ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
            System.out.println("[Msg] 写入成功: from=" + fromUserId + " to=" + to.getId());
            return Map.of("success", true, "message", "已发送绑定申请");
        } catch (SQLException e) { throw new RuntimeException("发送失败", e); }
    }

    /** 查询发给我的未处理消息 */
    public List<Map<String, Object>> pendingMessages(long userId) {
        System.out.println("[Msg] 查询未处理: userId=" + userId);

        String sql = """
            SELECT m.id, m.from_user_id, m.alias, m.status, m.created_at,
                   u.nickname, u.phone, u.avatar, u.type
            FROM bind_messages m JOIN users u ON m.from_user_id = u.id
            WHERE m.to_user_id = ? AND m.status = 0 ORDER BY m.created_at DESC
            """;
        List<Map<String, Object>> result = queryList(sql, userId);
        return result;
    }

    /** 未处理消息数 */
    public int pendingMessageCount(long userId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM bind_messages WHERE to_user_id = ? AND status = 0")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { return 0; }
    }

    /** 处理消息：1=接受, 2=拒绝 */
    public Map<String, Object> handleMessage(long msgId, int action, long userId) {
        System.out.println("[Msg] 处理: msgId=" + msgId + " action=" + action + " userId=" + userId);
        try {
            // 查询消息
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM bind_messages WHERE id = ? AND to_user_id = ? AND status = 0");
            ps.setLong(1, msgId); ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Map.of("success", false, "message", "消息不存在或已处理");

            long fromId = rs.getLong("from_user_id");
            long toId = rs.getLong("to_user_id");
            String alias = rs.getString("alias");

            if (action == 2) {
                // 拒绝 → 标记
                try (PreparedStatement up = connection.prepareStatement(
                        "UPDATE bind_messages SET status = 2 WHERE id = ?")) {
                    up.setLong(1, msgId); up.executeUpdate();
                }
                System.out.println("[Msg] 已拒绝");
                return Map.of("success", true, "message", "已拒绝");
            }

            // 接受 → 写入关系表
            User from = findById(fromId);
            User to = findById(toId);
            long guardianId = "guardian".equals(from.getType()) ? fromId : toId;
            long elderlyId = "guardian".equals(from.getType()) ? toId : fromId;
            String gAlias = "guardian".equals(from.getType()) && alias != null ? alias : null;
            String eAlias = "elderly".equals(from.getType()) && alias != null ? alias : null;

            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT INTO user_relationships (guardian_id, elderly_id, guardian_alias, elderly_alias, created_at) VALUES (?,?,?,?,?)")) {
                ins.setLong(1, guardianId); ins.setLong(2, elderlyId);
                ins.setString(3, gAlias); ins.setString(4, eAlias);
                ins.setString(5, Instant.now().toString());
                ins.executeUpdate();
            }

            // 标记消息为已处理
            try (PreparedStatement up = connection.prepareStatement(
                    "UPDATE bind_messages SET status = 1 WHERE id = ?")) {
                up.setLong(1, msgId); up.executeUpdate();
            }
            System.out.println("[Msg] 已接受，关系已创建");
            return Map.of("success", true, "message", "已接受绑定");
        } catch (SQLException e) { throw new RuntimeException("处理失败", e); }
    }

    // ==================== 关系表 ====================

    public List<Map<String, Object>> approvedRelations(long userId) {
        User me = findById(userId);
        if (me == null) return List.of();
        String sql = me.getType().equals("guardian")
            ? "SELECT r.*, u.id AS userId, u.nickname, u.phone, u.avatar, u.type FROM user_relationships r JOIN users u ON r.elderly_id = u.id WHERE r.guardian_id = ?"
            : "SELECT r.*, u.id AS userId, u.nickname, u.phone, u.avatar, u.type FROM user_relationships r JOIN users u ON r.guardian_id = u.id WHERE r.elderly_id = ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("userId", rs.getLong("userId"));
                    m.put("nickname", rs.getString("nickname"));
                    m.put("phone", rs.getString("phone"));
                    m.put("avatar", rs.getString("avatar"));
                    m.put("type", rs.getString("type"));
                    String alias = "guardian".equals(me.getType())
                        ? rs.getString("elderly_alias") : rs.getString("guardian_alias");
                    m.put("alias", alias);
                    list.add(m);
                }
            }
        } catch (SQLException e) { /* ignore */ }
        return list;
    }

    public void deleteRelation(long relationId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM user_relationships WHERE id = ?")) {
            ps.setLong(1, relationId); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("删除失败", e); }
    }

    // ==================== 辅助 ====================

    private boolean isAlreadyBound(User a, User b) {
        long gId = "guardian".equals(a.getType()) ? a.getId() : b.getId();
        long eId = "guardian".equals(a.getType()) ? b.getId() : a.getId();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM user_relationships WHERE guardian_id = ? AND elderly_id = ?")) {
            ps.setLong(1, gId); ps.setLong(2, eId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private List<Map<String, Object>> queryList(String sql, Object... params) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("nickname", rs.getString("nickname"));
                    m.put("phone", rs.getString("phone"));
                    m.put("avatar", rs.getString("avatar"));
                    m.put("type", rs.getString("type"));
                    m.put("alias", rs.getString("alias"));
                    m.put("status", rs.getInt("status"));
                    m.put("created_at", rs.getString("created_at"));
                    list.add(m);
                }
            }
        } catch (SQLException e) { /* ignore */ }
        return list;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id")); u.setPhone(rs.getString("phone"));
        u.setPassword(rs.getString("password")); u.setNickname(rs.getString("nickname"));
        u.setAvatar(rs.getString("avatar")); u.setType(rs.getString("type"));
        u.setToken(rs.getString("token"));
        return u;
    }
}
