package com.example.voiceinput.dao;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class NlpConfigDAO {

    private final Connection connection;

    public NlpConfigDAO() {
        try {
            String dbPath = System.getProperty("user.dir") + "/calendar.db";
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initialize();
            System.out.println("[NLP-DB] 配置词表已就绪");
        } catch (SQLException e) {
            throw new RuntimeException("NLP 配置数据库初始化失败", e);
        }
    }

    private void initialize() {
        String sql = """
            CREATE TABLE IF NOT EXISTS nlp_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                category TEXT NOT NULL,
                intent TEXT,
                priority INTEGER DEFAULT 0
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            // 初始化默认数据（仅首次）
            int count = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nlp_config")) {
                if (rs.next()) count = rs.getInt(1);
            }
            if (count == 0) {
                insertDefaults(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException("建表失败", e);
        }
    }

    private void insertDefaults(Statement stmt) throws SQLException {
        // trigger_add
        String[][] triggerAdd = {
            {"添加","trigger_add","ADD"},{"新增","trigger_add","ADD"},{"记下","trigger_add","ADD"},
            {"安排","trigger_add","ADD"},{"提醒我","trigger_add","ADD"},{"创建","trigger_add","ADD"},
            {"加入","trigger_add","ADD"},{"预定","trigger_add","ADD"},{"预订","trigger_add","ADD"},
            {"定一个","trigger_add","ADD"},{"加一个","trigger_add","ADD"},{"设一个","trigger_add","ADD"},
            {"帮我记","trigger_add","ADD"},{"帮我加","trigger_add","ADD"},{"帮我定","trigger_add","ADD"}
        };
        // trigger_delete
        String[][] triggerDelete = {
            {"删除","trigger_delete","DELETE"},{"取消","trigger_delete","DELETE"},
            {"移除","trigger_delete","DELETE"},{"去掉","trigger_delete","DELETE"},
            {"清除","trigger_delete","DELETE"},{"删掉","trigger_delete","DELETE"},
            {"帮我删","trigger_delete","DELETE"},{"帮我取消","trigger_delete","DELETE"}
        };
        // trigger_query
        String[][] triggerQuery = {
            {"查看","trigger_query","QUERY"},{"查询","trigger_query","QUERY"},
            {"列出","trigger_query","QUERY"},{"有什么","trigger_query","QUERY"},
            {"找一下","trigger_query","QUERY"},{"搜索","trigger_query","QUERY"},
            {"显示","trigger_query","QUERY"},{"查一下","trigger_query","QUERY"},
            {"帮我查","trigger_query","QUERY"},{"帮我看看","trigger_query","QUERY"}
        };
        // stop_words
        String[][] stops = {
            {"帮我","stop_word",""},{"今天","stop_word",""},{"明天","stop_word",""},
            {"后天","stop_word",""},{"昨天","stop_word",""},{"大后天","stop_word",""},
            {"早上","stop_word",""},{"上午","stop_word",""},{"中午","stop_word",""},
            {"下午","stop_word",""},{"晚上","stop_word",""},{"凌晨","stop_word",""},
            {"周一","stop_word",""},{"周二","stop_word",""},{"周三","stop_word",""},
            {"周四","stop_word",""},{"周五","stop_word",""},{"周六","stop_word",""},
            {"周日","stop_word",""},{"星期一","stop_word",""},{"星期二","stop_word",""},
            {"星期三","stop_word",""},{"星期四","stop_word",""},{"星期五","stop_word",""},
            {"星期六","stop_word",""},{"星期日","stop_word",""},{"星期天","stop_word",""},
            {"点","stop_word",""},{"分","stop_word",""},{"半","stop_word",""},
            {"的","stop_word",""},{"个","stop_word",""},{"一下","stop_word",""},
            {"一个","stop_word",""},{"到","stop_word",""},{"至","stop_word",""},
            {"从","stop_word",""},{"开始","stop_word",""},{"起","stop_word",""},
            {"小时","stop_word",""},{"分钟","stop_word",""},{"秒钟","stop_word",""},
            {"钟头","stop_word",""},{"在","stop_word",""},{"于","stop_word",""}
        };
        // filler words
        String[][] fillers = {
            {"嗯","filler",""},{"啊","filler",""},{"哦","filler",""},
            {"呢","filler",""},{"吧","filler",""},{"嘛","filler",""},
            {"呀","filler",""},{"嘿","filler",""},{"哈","filler",""},
            {"呵","filler",""},{"了","filler",""},{"着","filler",""},
            {"过","filler",""},{"的","filler",""},{"吧","filler",""},
            {"吗","filler",""},{"哟","filler",""},{"呗","filler",""},
            {"啦","filler",""},{"哇","filler",""},{"哎","filler",""},
            {"唉","filler",""},{"呃","filler",""},{"喔","filler",""},
            {"呐","filler",""},{"咚","filler",""},{"滴","filler",""},
            {"那个","filler",""},{"这个","filler",""},{"然后","filler",""},
            {"就是","filler",""},{"就是说","filler",""},{"的话","filler",""},
            {"帮我","filler",""},{"请","filler",""},{"麻烦","filler",""},
            {"预计","filler",""},{"可能","filler",""},{"应该","filler",""},
            {"大概","filler",""},{"估计","filler",""},{"准备","filler",""},
            {"打算","filler",""},{"想要","filler",""},{"需要","filler",""}
        };

        String insertSql = "INSERT INTO nlp_config (word, category, intent) VALUES ";
        StringBuilder sb = new StringBuilder(insertSql);
        boolean first = true;
        for (String[][] group : new String[][][]{triggerAdd, triggerDelete, triggerQuery, stops, fillers}) {
            for (String[] row : group) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("('").append(row[0].replace("'", "''")).append("','")
                  .append(row[1]).append("','").append(row[2]).append("')");
            }
        }
        stmt.execute(sb.toString());
        System.out.println("[NLP-DB] 已插入默认配置词");
    }

    /** 查询所有配置词 */
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM nlp_config ORDER BY category, priority DESC")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("word", rs.getString("word"));
                row.put("category", rs.getString("category"));
                row.put("intent", rs.getString("intent"));
                row.put("priority", rs.getInt("priority"));
                list.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询配置词失败", e);
        }
        return list;
    }

    /** 按 category 分组加载配置词 */
    public Map<String, List<String>> loadByCategory() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM nlp_config ORDER BY category, priority DESC")) {
            while (rs.next()) {
                String category = rs.getString("category");
                String word = rs.getString("word");
                map.computeIfAbsent(category, k -> new ArrayList<>()).add(word);
            }
        } catch (SQLException e) {
            throw new RuntimeException("加载配置词失败", e);
        }
        return map;
    }

    /** 新增配置词 */
    public void insert(String word, String category, String intent) {
        String sql = "INSERT INTO nlp_config (word, category, intent) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, word);
            ps.setString(2, category);
            ps.setString(3, intent);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入配置词失败", e);
        }
    }

    /** 删除配置词 */
    public boolean delete(int id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM nlp_config WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("删除配置词失败", e);
        }
    }
}
