# 语音日历后端 — Voice Calendar Backend

基于 Spring Boot 3.3 的语音日历后端服务，提供 NLP 自然语言解析、用户认证、SQLite 持久化能力。支持通过大模型（Ollama）或规则引擎将自然语音指令直接转为日历事件操作（添加、查询、删除、修改）。

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
│   ├── AIService.java                  # Ollama function calling（add/delete/query/modify 四函数）
│   ├── CalendarService.java            # 日历业务接口
│   ├── NLPProcessor.java               # NLP 解析接口
│   └── impl/
│       ├── CalendarServiceImpl.java    # 日历 CRUD 实现
│       ├── LLMNLPProcessor.java        # LLM NLP（调用 AIService）
│       └── NLPProcessorImpl.java       # 规则 NLP（Natty + 中文关键词）
└── controller/
    ├── AuthController.java             # 注册 / 登录 / 绑定
    ├── NlpConfigController.java        # NLP 配置词 CRUD API
    └── CalendarController.java         # 语音解析 / 语音执行 / 事件 CRUD
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

### 日历 `POST/GET/DELETE /api/calendar`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/voice` | 语音文本 → NLP 解析 → 返回结构化实体 | ❌ |
| POST | `/voice/execute` | ⭐ 语音文本 → 解析 → 自动增删查改 | ✅ |
| GET | `/events` | 查询当前用户事件 | ✅ |
| GET | `/events/range?start=&end=` | 按日期范围查询 | ✅ |
| POST | `/events/add` | 手动添加事件 | ✅ |
| DELETE | `/events/{id}` | 按 ID 删除事件 | ✅ |
| POST | `/events/query` | 按关键词/日期查询事件 | ✅ |
| POST | `/events/delete-by-voice` | 语音模糊搜索删除 | ✅ |

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
│    modify_event(keyword, date, newTitle,│
│                 newStartTime, newEndTime,│
│                 newLocation)             │
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

### 语音执行流程

```
POST /api/calendar/voice/execute  { text: "明天下午三点开会" }
    ↓
用户鉴权（X-Token）
    ↓
NLP 解析（LLM → rule 回退）
    ↓
┌─ ADD    → 去重检测 → 冲突检测 → 写入数据库
├─ DELETE → 搜索候选 → 单删 / 多候选返回
├─ QUERY  → 按时间/关键词查询 → 返回事件列表
└─ MODIFY → 搜索候选 → 单改 / 多候选返回
    ↓
返回: { success, intent, message, event/events/candidates }
```
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
| **created_by** | **INTEGER** | **操作者用户 ID，删除时校验** |

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
| category | TEXT | trigger_add / trigger_delete / trigger_query / trigger_modify / stop_word / filler |
| intent | TEXT | 对应 IntentType |
| priority | INTEGER | 优先级 |

---

## 关键设计

### 用户隔离

`SqliteEventDAO.setCurrentUser(userId)` 设置当前会话用户，所有 SQL 自动带 `WHERE user_id = ?`。
监护人可通过 `setViewingUser(uid)` 查看/操作被监护人日历，需校验监护关系。

### 操作权限

- 查询：可查自己创建的或自己名下的所有事件
- 添加：`user_id = targetUserId()`（归属），`created_by = currentUserId`（操作者）
- 删除/修改：仅操作者（`created_by = currentUserId`）可删改自己创建的事件

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

添加事件时自动检测同一时间段已有事件，返回冲突列表。同日期同标题自动去重不重复添加。

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

### 前置：启动 Ollama（LLM 模式）

```bash
ollama pull qwen2.5:3b      # 或 qwen2.5:7b
ollama serve
```

环境配置（`application.properties`）：

```properties
nlp.llm.enabled=true
nlp.llm.url=http://localhost:11423/api/chat
nlp.llm.model=qwen2.5:3b
```

Ollama 不可用时自动降级为规则引擎。

### 运行服务

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

### 前置：启动 Ollama（LLM 模式）

```bash
ollama pull qwen2.5:3b      # 或 qwen2.5:7b
ollama serve
```

环境配置（`application.properties`）：

```properties
nlp.llm.enabled=true
nlp.llm.url=http://localhost:11434/api/chat
nlp.llm.model=qwen2.5:3b
```

Ollama 不可用时自动降级为规则引擎。



## 演示视频
【日历-演示视频】 https://www.bilibili.com/video/BV1uhVU6WESV/?share_source=copy_web&vd_source=941f69ebe6affead31df6b96cbe68632
