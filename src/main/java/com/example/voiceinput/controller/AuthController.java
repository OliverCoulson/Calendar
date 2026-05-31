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

    public AuthController(UserDAO userDAO) { this.userDAO = userDAO; }

    // ==================== 注册 / 登录 ====================

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone"); String password = body.get("password");
        String nickname = body.getOrDefault("nickname", "");
        String type = body.getOrDefault("type", "elderly");
        if (phone == null || !phone.matches("\\d{11}"))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号格式错误"));
        if (password == null || password.length() < 4)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "密码至少4位"));
        User user = new User(phone, hash(password), nickname.isEmpty() ? phone : nickname, type);
        User saved = userDAO.insert(user);
        if (saved == null) return ResponseEntity.ok(Map.of("success", false, "message", "该手机号已注册"));
        return ResponseEntity.ok(userToMap(saved, "注册成功"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone"); String password = body.get("password");
        if (phone == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号和密码必填"));
        User user = userDAO.findByPhone(phone);
        if (user == null || !user.getPassword().equals(hash(password)))
            return ResponseEntity.ok(Map.of("success", false, "message", "手机号或密码错误"));
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
        Map<String, Object> m = userToMap(user, "ok");
        m.put("pendingCount", userDAO.pendingMessageCount(user.getId()));
        return ResponseEntity.ok(m);
    }

    // ==================== 绑定消息 ====================

    /** 发送绑定请求 → bind_messages */
    @PostMapping("/bind/request")
    public ResponseEntity<Map<String, Object>> requestBind(@RequestHeader("X-Token") String token,
                                                            @RequestBody Map<String, String> body) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        String targetPhone = body.get("targetPhone");
        String alias = body.getOrDefault("alias", "");
        if (targetPhone == null || !targetPhone.matches("\\d{11}"))
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "目标手机号格式错误"));
        return ResponseEntity.ok(userDAO.sendBindRequest(me.getId(), targetPhone, alias.isEmpty() ? null : alias));
    }

    /** 待处理消息 */
    @GetMapping("/bind/pending")
    public ResponseEntity<List<Map<String, Object>>> pending(@RequestHeader("X-Token") String token) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(userDAO.pendingMessages(me.getId()));
    }

    /** 接受(1)或拒绝(2) */
    @PostMapping("/bind/{id}/handle")
    public ResponseEntity<Map<String, Object>> handle(@RequestHeader("X-Token") String token,
                                                       @PathVariable long id,
                                                       @RequestBody Map<String, Integer> body) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        int action = body.getOrDefault("action", 2); // 1=接受, 2=拒绝
        return ResponseEntity.ok(userDAO.handleMessage(id, action, me.getId()));
    }

    /** 未读消息数 */
    @GetMapping("/bind/count")
    public ResponseEntity<Map<String, Object>> pendingCount(@RequestHeader("X-Token") String token) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("count", 0));
        return ResponseEntity.ok(Map.of("count", userDAO.pendingMessageCount(me.getId())));
    }

    /** 已绑定列表 */
    @GetMapping("/bind/list")
    public ResponseEntity<List<Map<String, Object>>> bindList(@RequestHeader("X-Token") String token) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(userDAO.approvedRelations(me.getId()));
    }

    /** 删除关系 */
    @DeleteMapping("/bind/{id}")
    public ResponseEntity<Map<String, Object>> unbind(@RequestHeader("X-Token") String token, @PathVariable long id) {
        User me = userDAO.findByToken(token);
        if (me == null) return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        userDAO.deleteRelation(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "已解除绑定"));
    }

    // ==================== 辅助 ====================

    private Map<String, Object> userToMap(User u, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true); m.put("message", message);
        m.put("id", u.getId()); m.put("phone", u.getPhone());
        m.put("nickname", u.getNickname()); m.put("avatar", u.getAvatar());
        m.put("type", u.getType()); m.put("token", u.getToken());
        m.put("pendingCount", userDAO.pendingMessageCount(u.getId()));
        m.put("relations", userDAO.approvedRelations(u.getId()));
        return m;
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
