package com.example.voiceinput.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class AIService {

    private final boolean enabled;
    private final String ollamaUrl;
    private final String model;
    private final int timeoutMs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    /** 三函数 Schema — Ollama function calling */
    private static final String TOOLS_JSON = """
        [
          {
            "type": "function",
            "function": {
              "name": "add_event",
              "description": "添加日历事件",
              "parameters": {
                "type": "object",
                "properties": {
                  "title": {"type": "string", "description": "事件标题，去除时间地点语气词后的核心内容"},
                  "startTime": {"type": "string", "description": "开始时间 yyyy-MM-dd HH:mm，只说时间段未说具体时间填 yyyy-MM-dd 00:00"},
                  "endTime": {"type": "string", "description": "结束时间，未指定则为null"},
                  "location": {"type": "string", "description": "地点，无则为null"},
                  "description": {"type": "string", "description": "补充描述，无则为null"}
                },
                "required": ["title","startTime"]
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "delete_event",
              "description": "删除日历事件",
              "parameters": {
                "type": "object",
                "properties": {
                  "keyword": {"type": "string", "description": "要删除的事件关键词或标题"},
                  "date": {"type": "string", "description": "事件日期 yyyy-MM-dd，无则为null"}
                },
                "required": ["keyword"]
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "query_events",
              "description": "查询日历事件",
              "parameters": {
                "type": "object",
                "properties": {
                  "date": {"type": "string", "description": "查询日期 yyyy-MM-dd，无则为null查全部"},
                  "keyword": {"type": "string", "description": "搜索关键词，无则为null"}
                },
                "required": []
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "modify_event",
              "description": "修改日历事件，例如改时间、改标题、改地点",
              "parameters": {
                "type": "object",
                "properties": {
                  "keyword": {"type": "string", "description": "要修改的原事件关键词或标题"},
                  "date": {"type": "string", "description": "原事件日期 yyyy-MM-dd，无则为null"},
                  "newTitle": {"type": "string", "description": "新标题，不改则为null"},
                  "newStartTime": {"type": "string", "description": "新开始时间 yyyy-MM-dd HH:mm，不改则为null"},
                  "newEndTime": {"type": "string", "description": "新结束时间，不改则为null"},
                  "newLocation": {"type": "string", "description": "新地点，不改则为null"}
                },
                "required": ["keyword"]
              }
            }
          }
        ]
        """;

    public AIService(
            @Value("${nlp.llm.enabled:true}") boolean enabled,
            @Value("${nlp.llm.url:http://localhost:11434/api/chat}") String ollamaUrl,
            @Value("${nlp.llm.model:qwen2.5:7b}") String model,
            @Value("${nlp.llm.timeout:60000}") int timeoutMs) {
        this.enabled = enabled;
        this.ollamaUrl = ollamaUrl;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isEnabled() { return enabled; }

    /**
     * 调用 Ollama function calling，返回 { functionName, parameters }
     */
    public Map<String, Object> functionCall(String userText) {
        if (!enabled) {
            System.out.println("[AI] 未启用");
            return null;
        }
        System.out.println("[AI] function calling → " + model);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                Map.of("role", "system", "content", buildSystemPrompt()),
                Map.of("role", "user", "content", userText)
            ));
            body.put("tools", mapper.readTree(TOOLS_JSON));
            body.put("stream", false);
            body.put("options", Map.of("temperature", 0.1, "num_predict", 300));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode msg = root.path("message");
            JsonNode toolCalls = msg.path("tool_calls");

            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                JsonNode call = toolCalls.get(0);
                String fnName = call.path("function").path("name").asText("unknown");
                JsonNode args = call.path("function").path("arguments");
                System.out.println("[AI] → " + fnName + "(" + args + ")");

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("function", fnName);
                result.put("parameters", mapper.convertValue(args, Map.class));
                return result;
            }

            System.out.println("[AI] 无 tool_call: " + msg.path("content").asText(""));
            return null;
        } catch (java.net.ConnectException e) {
            System.out.println("[AI] Ollama 未启动");
            return null;
        } catch (Exception e) {
            System.out.println("[AI] 失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt() {
        return """
            你是日历助手。根据用户输入选择对应的函数调用。
            规则：
            - "明天""后天""周X"等转为具体日期（当前日期: """ + java.time.LocalDate.now() + """
            - 具体时间（如"三点""3点半"）→ 填入startTime
            - 只说时间段未说具体时间（"上午""下午"等）→ startTime填00:00，title加时间段前缀如"上午 会议"
            - "下午/晚上X点" → 24小时制(+12)
            - 未指定时长 → endTime保持null
            - 地点提取后放入location字段
            """;
    }
}
