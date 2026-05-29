package org.duiduidui.calendar.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 应用配置管理。
 *
 * 配置加载优先级：
 *   1. 环境变量（最高优先）
 *   2. config.properties 文件（classpath 根目录）
 *   3. 硬编码默认值
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        // 加载配置文件
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("[Config] 加载 config.properties 失败: " + e.getMessage());
        }
    }

    // ===== 阿里云 ASR 配置 =====

    public String getAliyunAppKey() {
        return get("aliyun.asr.app.key");
    }

    public String getAliyunAccessKeyId() {
        return get("aliyun.asr.access.key.id");
    }

    public String getAliyunAccessKeySecret() {
        return get("aliyun.asr.access.key.secret");
    }

    public String getAliyunGateway() {
        return get("aliyun.asr.gateway", "nls-gateway.cn-shanghai.aliyuncs.com");
    }

    // ===== 存储配置 =====

    public String getDbPath() {
        return get("storage.db.path", "events.db");
    }

    // ===== 通用方法 =====

    private String get(String key) {
        String env = System.getenv(key.replace(".", "_").toUpperCase());
        if (env != null && !env.isEmpty()) return env;
        return props.getProperty(key);
    }

    private String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
