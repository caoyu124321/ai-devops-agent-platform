# Codex 本地登录与 MCP 接入规格

**修订版本**：0.1  
**状态**：需求已确认，待技术设计  
**关联模块**：IAM、未来流水线模块

## 目标

让用户在 Codex 中说出“登录 ai-devops”后，能够通过 Codex 的输入提示完成 AI DevOps 平台登录。成功后，Codex 可在后续 MCP 工具调用中以该用户身份访问平台后端。

## 范围

- 提供仅在本机运行的 MCP Server，通过标准输入输出（stdio）与 Codex 连接。
- MCP Server 调用本机平台 REST API，默认地址为 `http://127.0.0.1:8080`。
- 支持登录、登出、读取当前登录用户三个 MCP 工具。
- 使用 Codex 的 MCP 输入提示收集用户名（或邮箱）和密码。
- 仅在 Windows 凭据管理器中保存后端签发的不透明 Token；不保存密码。
- 后续工具由 MCP Server 在服务端自动附加 Bearer Token，不向 Codex、工具结果、日志或文件返回 Token 原文。

## 非范围

- 不实现浏览器登录、OAuth、刷新 Token、远程 MCP Server 或多平台凭据存储。
- 不将现有 Spring Boot REST 服务替换为 MCP 协议服务。
- 不实现流水线查询、运行、部署或其他业务工具；它们在对应模块完成后再接入。
- 不实现内嵌 Web 登录页或 Codex 插件。本期采用 Codex 的输入提示。

## 用户故事

### US-MCP-01 在 Codex 中登录平台

作为 Codex 用户，我希望请求登录 AI DevOps 平台后看到用户名和密码输入提示，以便让后续工具以我的平台身份执行操作。

**前置条件**：本机 AI DevOps 服务可访问，用户已在平台完成注册。

**主流程**：

1. 用户在 Codex 中请求登录 AI DevOps 平台。
2. Codex 调用无敏感参数的 `login_ai_devops` 工具。
3. MCP Server 通过 MCP 输入提示收集登录标识和密码。
4. MCP Server 调用 `POST /api/v1/auth/login`。
5. 登录成功后，MCP Server 将 Token 写入 Windows 凭据管理器，工具仅返回安全用户摘要。

**异常流程**：

- 登录失败、服务不可达或输入取消时，不写入或覆盖已有有效 Token。
- 登录响应中不应显示 Token、密码或密码哈希。

**验收标准**：

- AS-MCP-01.1：成功登录后，Windows 凭据管理器存在对应 Token，且密码不被保存。
- AS-MCP-01.2：工具结果和 MCP 日志不含 Token 或密码原文。
- AS-MCP-01.3：密码错误时，Codex 获得通用登录失败信息，不泄露账户是否存在。

### US-MCP-02 复用已登录身份

作为已登录用户，我希望 Codex 后续调用平台工具时自动携带有效身份，以便无需重复登录。

**主流程**：

1. MCP 工具从 Windows 凭据管理器读取 Token。
2. MCP Server 在调用平台受保护 API 时附加 `Authorization: Bearer <token>`。
3. 工具返回经后端授权后的业务结果。

**异常流程**：

- 未找到 Token 时，工具返回“需要登录”，不调用受保护 API。
- 后端返回 401 时，MCP Server 删除本地 Token，并返回“登录已失效，需要重新登录”。
- 后端返回 403 或不可见资源时，保留 Token，仅返回后端的安全拒绝结果。

**验收标准**：

- AS-MCP-02.1：成功登录后，读取当前用户工具可返回与 `GET /api/v1/me` 一致的安全用户摘要。
- AS-MCP-02.2：Token 失效后不会继续被用于新的工具调用。
- AS-MCP-02.3：Token 原文不会出现在任何工具输入、工具输出或日志中。

### US-MCP-03 登出平台

作为已登录用户，我希望在 Codex 中登出 AI DevOps 平台，以便撤销本机当前会话。

**主流程**：

1. Codex 调用 `logout_ai_devops`。
2. MCP Server 使用保存的 Token 调用 `POST /api/v1/auth/logout`。
3. MCP Server 无论后端会话是否已失效，均删除 Windows 凭据管理器中的本地 Token。
4. 工具返回已登出。

**验收标准**：

- AS-MCP-03.1：登出后，后续受保护工具必须要求重新登录。
- AS-MCP-03.2：重复登出按成功处理。

## 功能需求

- FR-MCP-001：MCP Server 必须为本地 stdio 服务，不监听公网端口。
- FR-MCP-002：`login_ai_devops` 不得将密码定义为模型主动填充的普通工具参数；凭据必须通过 Codex 的 MCP 输入提示收集。
- FR-MCP-003：Token 必须以当前 Windows 用户隔离的方式保存到 Windows 凭据管理器，并按平台地址与用户身份区分。
- FR-MCP-004：Token 只能由 MCP Server 使用，不能返回给 Codex 或写入工作区文件。
- FR-MCP-005：`get_current_ai_devops_user` 必须复用本地 Token 调用 `GET /api/v1/me`。
- FR-MCP-006：`logout_ai_devops` 必须尝试调用后端登出，并始终清理本地凭据。
- FR-MCP-007：后续平台工具必须在请求前检查登录状态，并在 401 后立即清理本地 Token。

## 工具契约

| 工具 | 输入 | 成功输出 | 失败输出 | 安全属性 |
| --- | --- | --- | --- | --- |
| `login_ai_devops` | 无；通过 MCP 输入提示收集登录标识和密码 | 用户 ID、用户名、邮箱、登录状态 | 登录失败、服务不可达、用户取消 | 创建后端会话，不返回 Token |
| `get_current_ai_devops_user` | 无 | 当前用户安全摘要 | 未登录、登录失效、服务不可达 | 只读 |
| `logout_ai_devops` | 无 | 已登出 | 不应因会话已失效而失败 | 撤销当前后端会话并删除本地凭据 |

## 成功标准

- 用户无需在聊天消息中粘贴密码，也无需手动复制 Token。
- 登录后，新增平台 MCP 工具可复用同一用户身份。
- 后端 IAM 继续是唯一认证和授权来源；MCP 不复制或绕过 IAM 权限判断。
