package com.example.voiceinput.controller;

import com.example.voiceinput.dao.UserDAO;
import com.example.voiceinput.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserDAO userDAO;

    public AuthController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        String nickname = body.getOrDefault("nickname", "");
        String type = body.getOrDefault("type", "elderly");

        if (phone == null || !phone.matches("\\d{11}"))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号格式错误"));
        if (password == null || password.length() < 4)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "密码至少4位"));

        User user = new User(phone, hash(password), nickname.isEmpty() ? phone : nickname, type);
        User saved = userDAO.insert(user);
        if (saved == null)
            return ResponseEntity.ok(Map.of("success", false, "message", "该手机号已注册"));

        return ResponseEntity.ok(userToMap(saved, "注册成功"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        if (phone == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号和密码必填"));

        User user = userDAO.findByPhone(phone);
        if (user == null || !user.getPassword().equals(hash(password)))
            return ResponseEntity.ok(Map.of("success", false, "message", "手机号或密码错误"));

        // 每次登录刷新 token
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        userDAO.updateToken(user.getId(), token);
        user.setToken(token);

        return ResponseEntity.ok(userToMap(user, "登录成功"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(value = "X-Token", defaultValue = "") String token) {
        if (token.isEmpty()) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        User user = userDAO.findByToken(token);
        if (user == null) return ResponseEntity.ok(Map.of("success", false, "message", "登录已过期"));
        return ResponseEntity.ok(userToMap(user, "ok"));
    }

    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bind(@RequestHeader("X-Token") String token,
                                                     @RequestBody Map<String, String> body) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));

        String targetPhone = body.get("targetPhone");
        if (targetPhone == null || !targetPhone.matches("\\d{11}"))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "目标手机号格式错误"));

        User target = userDAO.findByPhone(targetPhone);
        if (target == null)
            return ResponseEntity.ok(Map.of("success", false, "message", "该手机号未注册"));

        // 监护人 → 老人：发起绑定申请
        if ("guardian".equals(me.getType()) && "elderly".equals(target.getType())) {
            userDAO.updateBind(me.getId(), targetPhone, "pending");
            return ResponseEntity.ok(Map.of("success", true, "message", "已发送绑定申请，等待老人确认"));
        }
        // 老人 → 监护人：同理
        if ("elderly".equals(me.getType()) && "guardian".equals(target.getType())) {
            userDAO.updateBind(me.getId(), targetPhone, "pending");
            return ResponseEntity.ok(Map.of("success", true, "message", "已发送绑定申请，等待对方确认"));
        }

        return ResponseEntity.ok(Map.of("success", false, "message", "只能绑定不同类型的用户（老人↔监护人）"));
    }

    @PostMapping("/bind/approve")
    public ResponseEntity<Map<String, Object>> approveBind(@RequestHeader("X-Token") String token) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));

        // 查找谁向我发起了绑定申请
        User requester = userDAO.findBoundUser(me.getPhone());
        if (requester == null || !"pending".equals(requester.getBindStatus()))
            return ResponseEntity.ok(Map.of("success", false, "message", "没有待处理的绑定申请"));

        // 双向确认
        userDAO.updateBind(requester.getId(), requester.getBoundPhone(), "approved");
        userDAO.updateBind(me.getId(), requester.getPhone(), "approved");

        return ResponseEntity.ok(Map.of("success", true, "message", "绑定成功"));
    }

    private Map<String, Object> userToMap(User u, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("message", message);
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("type", u.getType());
        m.put("token", u.getToken());
        m.put("boundPhone", u.getBoundPhone());
        m.put("bindStatus", u.getBindStatus());
        return m;
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
