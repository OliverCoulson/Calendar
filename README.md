# 语音日历后端 — Voice Calendar Backend

基于 Spring Boot 3.3 的语音日历后端服务，提供 NLP 自然语言解析、用户认证、SQLite 持久化能力。增删逻辑预留 TODO，由他人实现。

---

## 技术栈

| 层 | 技术 |
|---|------|
| 框架 | Spring Boot 3.3 + Maven |
| 数据库 | SQLite (sqlite-jdbc 3.46) |
| LLM | Ollama + Qwen2.5 (function calling) |
| NLP 降级 | Natty 时间解析 + 中文规则引擎 |
| 鉴权 | SHA-256 密码哈希 + Token 会话 |
| Java | JDK 17+ |

---

## 项目结构

```
src/main/java/com/example/voiceinput/
├── VoiceInputApplication.java          # Spring Boot 启动类
├── config/
│   ├── WebConfig.java                  # CORS 跨域配置
│   ├── AuthInterceptor.java            # 鉴权拦截器（已创建，未启用）
│   └── DataInitializer.java           # 启动时创建测试账号
├── model/
│   ├── CalendarEvent.java              # 日历事件实体
│   ├── IntentType.java                 # ADD / DELETE / QUERY / MODIFY / UNKNOWN
│   ├── ParsedResult.java               # NLP 解析结果（意图 + 实体 + 置信度）
│   ├── User.java                       # 用户实体
│   └── VoiceRequest.java               # 语音请求 DTO
├── dao/
│   ├── EventDAO.java                   # 事件数据接口
│   ├── SqliteEventDAO.java             # SQLite 实现（按用户隔离）
│   ├── NlpConfigDAO.java               # NLP 配置词表管理（90+ 触发词/停用词/语气词）
│   └── UserDAO.java                    # 用户 CRUD
├── service/
│   ├── AIService.java                  # Ollama function calling（add/delete/query 三函数）
│   ├── CalendarService.java            # 日历业务接口
│   ├── NLPProcessor.java               # NLP 解析接口
│   └── impl/
│       ├── CalendarServiceImpl.java    # 日历 CRUD 实现
│       ├── LLMNLPProcessor.java        # LLM NLP（调用 AIService）
│       └── NLPProcessorImpl.java       # 规则 NLP（Natty + 中文关键词）
└── controller/
    ├── AuthController.java             # 注册 / 登录 / 绑定
    ├── NlpConfigController.java        # NLP 配置词 CRUD API
    └── CalendarController.java         # 语音解析 + 事件查询（增删 TODO）
```

---

## API 一览

### 认证 `POST /api/auth`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/register` | 注册 `{phone, password, nickname?, type}` | ❌ |
| POST | `/login` | 登录，返回 token | ❌ |
| GET | `/me` | 当前用户信息（Header: X-Token） | ✅ |
| POST | `/bind` | 发起监护绑定 `{targetPhone}` | ✅ |
| POST | `/bind/approve` | 同意绑定申请 | ✅ |

### 日历 `POST/GET /api/calendar`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/voice` | 语音文本 → NLP 解析 → 返回结构化实体 | ❌ |
| GET | `/events` | 查询当前用户事件 | 按需 |
| GET | `/events/range?start=&end=` | 按日期范围查询 | 按需 |

### NLP 配置 `GET/POST/DELETE /api/nlp`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/words` | 查看所有配置词 |
| POST | `/words` | 新增 `{word, category, intent}` |
| DELETE | `/words/{id}` | 删除 |

---

## NLP 双引擎架构

```
用户语音文本
    ↓
POST /api/calendar/voice
    ↓
┌─ AIService.functionCall() ─────────────┐
│  Ollama Qwen2.5 (3B/7B)                │
│  function calling:                      │
│    add_event(title,startTime,endTime,   │
│              location,description)      │
│    delete_event(keyword, date)          │
│    query_events(date, keyword)          │
│  失败/未启用 → 降级                     │
└────────────────────────────────────────┘
    ↓ fallback
┌─ NLPProcessorImpl (规则引擎) ──────────┐
│  SQLite nlp_config 表 → 加载词表        │
│  Natty 时间解析 + 中文星期/数字转换     │
│  意图分类 + 地点提取 + 标题清洗         │
└────────────────────────────────────────┘
    ↓
返回: { intent, title, startTime, endTime, location, period, confidence }
```

### LLM 启用方式

```bash
ollama pull qwen2.5:7b     # 或 qwen2.5:3b（更快）
ollama serve                # 启动 Ollama
```

`application.yml` 中 `nlp.llm.enabled: true`

---

## 数据库设计

### calendar_events（日历事件）

| 列 | 类型 | 说明 |
|----|------|------|
| id | TEXT PK | UUID |
| title | TEXT | 事件标题 |
| start_time | TEXT | ISO-8601 开始时间 |
| end_time | TEXT | 结束时间，可为 NULL |
| location | TEXT | 地点 |
| description | TEXT | 描述 |
| remind_time | TEXT | 提醒时间 |
| reminded | INTEGER | 0/1 是否已提醒 |
| **user_id** | **INTEGER FK** | **关联 users.id** |

### users（用户）

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 自增 |
| phone | TEXT UNIQUE | 手机号 |
| password | TEXT | SHA-256 哈希 |
| nickname | TEXT | 昵称 |
| avatar | TEXT | Emoji 👴/👤 |
| type | TEXT | elderly / guardian |
| token | TEXT | 登录会话 |
| bound_phone | TEXT | 绑定对象手机号 |
| bind_status | TEXT | none / pending / approved |

### nlp_config（NLP 配置词）

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 自增 |
| word | TEXT | 词 |
| category | TEXT | trigger_add / trigger_delete / trigger_query / stop_word / filler |
| intent | TEXT | 对应 IntentType |
| priority | INTEGER | 优先级 |

---

## 关键设计

### 用户隔离

`SqliteEventDAO.setCurrentUser(userId)` 设置当前会话用户，所有 SQL 自动带 `WHERE user_id = ?`。

```java
eventDAO.setCurrentUser(user.getId());       // 设置用户
List<CalendarEvent> events = eventDAO.findAll(); // 只查该用户的
```

未登录时 `userId=0`，查询返回空列表。

### 无具体时间的处理

只说"上午""下午"未说具体时间 → `startTime=00:00`，标题加时间段前缀：

```
"明天上午开会" → title="上午 开会", startTime="2026-06-01T00:00"
"明天下午三点开会" → title="开会", startTime="2026-06-01T15:00"
```

### 冲突检测

有具体时间的事件相互检测（±30min窗口），无具体时间的事件不参与冲突检测，同日期同标题自动去重。

### 时长识别

```
"三点到五点" → endTime 计算
"两小时"     → endTime = startTime + 2h
"半小时"     → endTime = startTime + 30min
未说明       → endTime = null
```

跨天支持："晚上十点到凌晨两点" → endTime 自动 +1 天。

---

## 启动

```bash
# 首次启动（删旧库）
rm -f calendar.db

# Maven
mvn spring-boot:run
# → http://localhost:3002

# IDEA: 直接运行 VoiceInputApplication.main()
```

### 测试账号（自动创建）

| 角色 | 手机号 | 密码 |
|------|--------|------|
| 老人 | 13800000001 | 1234 |
| 监护人 | 13800000002 | 1234 |

---

## 待实现（TODO）

`CalendarController` 中标记了 TODO 的增删功能：

```java
// TODO: POST /api/calendar/events/add
// 1. 从 X-Token 解析用户 → eventDAO.setCurrentUser(userId)
// 2. 从 body 取 /voice 返回的实体
// 3. 冲突检测 + 去重
// 4. calendarService.addEvent()

// TODO: DELETE /api/calendar/events/{id}
// 1. 鉴权
// 2. calendarService.deleteEvent(id)
```

参考旧版 `voice-input-backend` 中的 `CalendarController.executeAdd()` / `executeDelete()` 实现。
