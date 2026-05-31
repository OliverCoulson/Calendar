package com.example.voiceinput.dao;

import com.example.voiceinput.model.User;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.UUID;

@Repository
public class UserDAO {

    private final Connection connection;

    public UserDAO() {
        try {
            String dbPath = System.getProperty("user.dir") + "/calendar.db";
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        phone TEXT UNIQUE NOT NULL,
                        password TEXT NOT NULL,
                        nickname TEXT,
                        avatar TEXT DEFAULT '👤',
                        type TEXT DEFAULT 'elderly',
                        token TEXT,
                        bound_phone TEXT,
                        bind_status TEXT DEFAULT 'none'
                    )
                """);
            }
            System.out.println("[UserDAO] 用户表已就绪");
        } catch (SQLException e) {
            throw new RuntimeException("用户表初始化失败", e);
        }
    }

    public User findByPhone(String phone) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE phone = ?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户失败", e);
        }
    }

    public User findByToken(String token) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public User insert(User user) {
        String sql = "INSERT INTO users (phone, password, nickname, avatar, type, token) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getPhone());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getNickname() != null ? user.getNickname() : user.getPhone());
            ps.setString(4, user.getAvatar() != null ? user.getAvatar() : "👤");
            ps.setString(5, user.getType() != null ? user.getType() : "elderly");
            ps.setString(6, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) user.setId(rs.getLong(1));
            }
            return user;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) return null;
            throw new RuntimeException("注册失败", e);
        }
    }

    public void updateToken(long id, String token) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE users SET token = ? WHERE id = ?")) {
            ps.setString(1, token);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新token失败", e);
        }
    }

    public void updateBind(long id, String boundPhone, String status) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE users SET bound_phone = ?, bind_status = ? WHERE id = ?")) {
            ps.setString(1, boundPhone);
            ps.setString(2, status);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新绑定失败", e);
        }
    }

    public User findBoundUser(String phone) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE bound_phone = ? AND bind_status = 'approved'")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setPhone(rs.getString("phone"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setAvatar(rs.getString("avatar"));
        u.setType(rs.getString("type"));
        u.setToken(rs.getString("token"));
        u.setBoundPhone(rs.getString("bound_phone"));
        u.setBindStatus(rs.getString("bind_status"));
        return u;
    }
}
