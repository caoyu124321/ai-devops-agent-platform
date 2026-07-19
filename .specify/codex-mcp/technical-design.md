# Codex MCP 技术设计

## 设计结论

新增 Maven 子模块 `mcp`，产出本地可执行的 `ai-devops-mcp` JAR。该模块是独立的 stdio 适配进程，不是 Spring Boot 应用：

- 不包含 `@SpringBootApplication`；
- 不启动 Web 容器、不连接业务数据库、不依赖 MyBatis；
- 只通过 HTTP 调用已经启动的 `application` 服务；
- `devops.AiDevopsAgentPlatformApplication` 仍是唯一 Spring Boot 启动类。

由于 stdio MCP 必须由 Codex 启动一个独立进程，`mcp` 模块需要一个普通 Java 入口类。这是对“其他模块不设置 Spring Boot 启动类”约束的必要细化，而非新增 Spring Boot 服务。

## 架构

```text
Codex
  │ stdio / MCP
  ▼
ai-devops-mcp（本地 Java 进程）
  ├─ MCP 工具与输入提示
  ├─ 平台 REST 客户端
  └─ Windows 凭据仓库
             │ HTTPS/HTTP + Bearer Token
             ▼
application（127.0.0.1:8080）
  └─ IAM REST API / IAM 授权
```

## 模块与包边界

| 包 | 职责 | 禁止事项 |
| --- | --- | --- |
| `devops.mcp.server` | 初始化 MCP Server、注册工具、维护 stdio 生命周期 | 不含业务认证逻辑 |
| `devops.mcp.tool` | 将 MCP 工具请求转换为用例调用，构造安全输出 | 不直接调用 Windows API 或 HTTP |
| `devops.mcp.identity` | 登录、登出、当前用户、会话失效处理 | 不保存密码 |
| `devops.mcp.platform` | 调用平台 REST API、映射安全错误 | 不解释 IAM 角色或权限 |
| `devops.mcp.credential` | Token 的 Windows 凭据管理器读写删 | 不写工作区文件、不使用命令行参数传递 Token |

## REST 调用边界

| MCP 用例 | REST 调用 | 认证头 | 处理 |
| --- | --- | --- | --- |
| 登录 | `POST /api/v1/auth/login` | 无 | 仅从响应内存取得 Token，存入凭据管理器 |
| 当前用户 | `GET /api/v1/me` | Bearer Token | 401 时删除本地 Token |
| 登出 | `POST /api/v1/auth/logout` | Bearer Token | 无论结果均删除本地 Token |

`POST /auth/login` 的响应包含 `token`、`expiresInSeconds` 和 `user`。Token 只允许停留在 MCP 进程内存与 Windows 凭据管理器，不得进入 MCP 工具输出。

## 凭据模型

### 存储规则

- 凭据类型：Windows Generic Credential。
- Token 目标名：`ai-devops-mcp/token/<baseUrlSha256>/<userId>`。
- 当前身份指针：`ai-devops-mcp/active/<baseUrlSha256>`，内容仅为当前用户 ID。
- 登录不同账户时，先删除同一平台的旧活动 Token 与活动指针，再写入新 Token 和新指针。
- 登出、401、凭据读取损坏时，删除活动指针及其关联 Token。

Windows 凭据管理器按当前 Windows 用户隔离。MCP 不保存平台密码，也不在文件、环境变量、Maven 配置或日志中写入 Token。

### 接口

```text
CredentialStore
  readActive(baseUrl) -> Optional<StoredSession>
  replaceActive(baseUrl, userId, token, expiresAt)
  clearActive(baseUrl)
```

生产实现通过 JNA 调用 Windows API；测试使用内存实现。JNA 的原生调用失败必须转换为 `CREDENTIAL_STORE_FAILED`，不得暴露 Token 内容。

## 输入提示与会话生命周期

1. `login_ai_devops` 不接收普通用户名或密码参数。
2. 工具处理器请求 Codex 的 MCP 输入提示，字段为 `login` 与 `password`。
3. 用户取消输入：返回 `LOGIN_CANCELLED`，且不触碰已有会话。
4. 后端登录失败：返回 `LOGIN_FAILED`，不暴露用户是否存在、密码是否错误或账户是否锁定。
5. 后端 401：清理凭据并返回 `SESSION_EXPIRED`。
6. 后端 403：保留凭据，返回 `BACKEND_ACCESS_DENIED`。
7. 网络超时、连接拒绝、非预期 5xx：返回 `BACKEND_UNAVAILABLE`，保留现有凭据。

若 Codex 未启用 MCP 输入提示能力，`login_ai_devops` 返回 `MCP_ELICITATION_UNAVAILABLE`，提示启用该能力；不得降级为让模型把密码作为工具参数或聊天文本传递。

## Codex 配置

建议在受信任仓库的 `.codex/config.toml` 配置：

```toml
[mcp_servers.ai_devops]
command = "C:\\Users\\Administrator\\.jdks\\openjdk-26.0.1\\bin\\java.exe"
args = ["--enable-native-access=ALL-UNNAMED", "-jar", "D:\\workspace\\ai-devops-agent-platform\\mcp\\target\\mcp-0.0.1-SNAPSHOT.jar"]
env = { AI_DEVOPS_BASE_URL = "http://127.0.0.1:8080" }
startup_timeout_sec = 15
tool_timeout_sec = 30
enabled_tools = ["get_ai_devops_capabilities", "register_ai_devops", "get_ai_devops_registration_status", "login_ai_devops", "get_ai_devops_login_status", "get_current_ai_devops_user", "logout_ai_devops"]
```

启动脚本负责调用 JAR，并设置 `--enable-native-access=ALL-UNNAMED`。脚本和配置中不得出现平台账号、密码或 Token。

## 能力发现与 Skill 设计

- `McpToolHandlers` 维护唯一的能力目录，并同时用于注册 `get_ai_devops_capabilities` 工具和生成其返回值，避免文档与实际注册工具漂移。
- 能力发现工具仅返回静态元数据，不依赖 `McpIdentityService`、平台 REST API 或 Windows 凭据管理器。
- MCP 初始化响应通过 `instructions` 声明：`ai-devops`、`ai devops`、`ai-d` 均是本服务别名；不明确需求或能力查询时优先调用能力发现工具。
- 仓库级 Skill 存放在 `.agents/skills/ai-devops-assistant`，随仓库分发；Skill 负责将自然语言路由到 MCP 工具，不重复维护动态能力列表，也不处理任何密码或 Token。

## 安全设计

- stdio 协议的标准输出只能输出 MCP JSON-RPC；诊断日志写标准错误，且执行统一脱敏。
- HTTP 客户端只允许访问已配置的本地基础地址；拒绝重定向和任意用户输入的 URL。
- 工具输出只包含用户安全摘要、状态、稳定错误码与可操作提示。
- `logout_ai_devops` 标记为有副作用工具；`get_current_ai_devops_user` 标记为只读工具。
- IAM 继续进行 Token 认证和资源授权；MCP 仅转发身份，不能缓存或自行判断角色权限。
