package com.example.voiceinput.config;

import com.example.voiceinput.dao.UserDAO;
import com.example.voiceinput.model.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserDAO userDAO;

    public DataInitializer(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public void run(String... args) {
        // 创建测试老人账号
        createIfNotExists("13800000001", "1234", "张爷爷", "elderly", "👴");
        // 创建测试监护人账号
        createIfNotExists("13800000002", "1234", "小明", "guardian", "👤");

        System.out.println("[Init] 测试账号已就绪: 13800000001 / 1234 (老人), 13800000002 / 1234 (监护人)");
    }

    private void createIfNotExists(String phone, String password, String nickname, String type, String avatar) {
        if (userDAO.findByPhone(phone) != null) return;
        User u = new User(phone, hash(password), nickname, type);
        u.setAvatar(avatar);
        userDAO.insert(u);
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return s; }
    }
}
