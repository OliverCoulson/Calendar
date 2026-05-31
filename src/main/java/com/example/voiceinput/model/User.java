package com.example.voiceinput.model;

public class User {
    private Long id;
    private String phone;
    private String password;     // BCrypt hash
    private String nickname;
    private String avatar;       // emoji or initial
    private String type;         // "elderly" | "guardian"
    private String token;        // session token
    private String boundPhone;   // 绑定的监护人/老人手机号
    private String bindStatus;   // pending/approved/rejected

    public User() {}

    public User(String phone, String password, String nickname, String type) {
        this.phone = phone;
        this.password = password;
        this.nickname = nickname;
        this.type = type;
        this.avatar = "elderly".equals(type) ? "👴" : "👤";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getBoundPhone() { return boundPhone; }
    public void setBoundPhone(String boundPhone) { this.boundPhone = boundPhone; }
    public String getBindStatus() { return bindStatus; }
    public void setBindStatus(String bindStatus) { this.bindStatus = bindStatus; }
}
