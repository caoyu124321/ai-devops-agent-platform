# 远程 AI DevOps MCP 接入规格

**修订版本**：1.1
**状态**：需求与技术设计已确认，待实施
**关联模块**：IAM OAuth/OIDC 扩展、MCP 接入层、未来流水线模块

## 目标

将 AI DevOps MCP Server 以公网可部署的 Streamable HTTP 服务提供给 Codex 及其他兼容 Agent。用户通过 IAM 提供的 OAuth/OIDC 登录页完成认证；MCP Server 仅接收短期访问令牌，并由 IAM 继续进行资源授权。

## 已确认的产品决策

| 决策 | 结论 |
| --- | --- |
| 身份提供方 | 在现有 IAM 上扩展 OAuth 2.0 与 OpenID Connect 提供方能力，不引入外部 IdP。 |
| 服务对象 | 面向外部租户开放；允许匿名注册平台账号并由用户自行创建租户。 |
| Agent 兼容性 | 面向支持 MCP Streamable HTTP、OAuth 授权码流程与 PKCE 的 Agent；不限定为 Codex。 |
| 客户端接入 | 使用动态客户端注册，实施限流与启用状态控制；不向公共客户端分发共享 `client_secret`。 |
| 邀请 | 保留既有租户邀请、接受、拒绝、撤销及七天到期规则。 |
| Refresh Token | 最长 30 天绝对有效；连续 7 天未使用失效；每次使用后轮换；检测到已轮换 Token 被重用时撤销整条授权链。 |
| 本地验证 | 使用与生产一致的 Streamable HTTP 与 OAuth/OIDC 协议；仅开发环境允许绑定 `127.0.0.1` 并使用 HTTP。 |
| Codex OAuth 不可用时的本机回退 | 仅开发环境允许启用旧版 stdio 一次性浏览器登录适配器；它使用当前 Windows 用户凭据管理器保存会话，且与远程 OAuth 连接二选一。 |

## 范围

- IAM 作为 OAuth 2.0 Authorization Server 与 OpenID Provider，提供发现、授权、令牌、撤销、令牌自省与用户信息能力。
- 提供符合 MCP Streamable HTTP 的远程 MCP 端点。
- 支持授权码流程与 PKCE（仅 `S256`）。
- 支持匿名能力发现与安全注册链接；认证后的 MCP 工具使用 OAuth Access Token。
- 支持动态客户端注册、客户端启用状态、浏览器授权与用户拒绝授权。
- 将现有本地 stdio MCP 与 Windows 凭据管理器实现迁移出正式运行路径。
- 当 Codex 未提供远程 MCP OAuth 授权入口时，提供仅绑定本机的 stdio 一次性浏览器登录回退适配器。

## 非范围

- 不支持隐式流程、资源所有者密码流程或在聊天/工具参数中收集密码。
- MVP 不支持客户端凭据流程、服务账号、设备授权流程、令牌交换、DPoP 或 mTLS 发送方约束。
- 不设计流水线、部署、项目、产物等业务工具的具体接口；后续只复用本认证接入与 IAM 授权入口。
- 不要求 Agent 保存平台密码；OAuth 客户端如何保护自身 Refresh Token 由其安全凭据机制负责。
- 当前不新增审计模块或审计持久化要求。
- 不允许在同一 Codex 配置中同时启用远程 OAuth MCP 与本机 stdio 回退连接。

## 用户故事

### US-MCP-01 使用任意兼容 Agent 登录

作为外部租户用户，我希望在支持该协议的 Agent 中连接 AI DevOps MCP 并在浏览器完成登录，以便无需向 Agent 或 MCP 工具提供密码。

**前置条件**：Agent 已配置 MCP 端点，且支持 OAuth 授权码流程与 PKCE；其客户端已启用。

**主流程**：

1. Agent 发现 MCP 与 OAuth 元数据，并生成 PKCE 校验参数。
2. Agent 打开 IAM 授权页；用户注册（如尚无账号）、登录并同意本次授权。
3. IAM 向预先登记且严格匹配的回调地址返回授权码。
4. Agent 使用授权码与 PKCE 校验值换取短期 Access Token；需要长期登录时同时取得 Refresh Token。
5. Agent 携带 Access Token 调用 MCP；MCP 将认证主体交给 IAM 统一授权。

**异常流程**：用户拒绝授权、客户端未启用、回调地址不匹配、PKCE 校验失败、授权码失效或重复使用时，均不得签发 Token，也不得泄露用户密码或令牌。

**验收标准**：

- AS-MCP-01.1：MCP 工具 Schema 与聊天消息均不包含用户名、密码或 Token 字段。
- AS-MCP-01.2：公共客户端不需要且不能使用共享 `client_secret`。
- AS-MCP-01.3：未通过 OAuth 认证的调用不能访问需要登录的 MCP 工具。

### US-MCP-02 安全地保持 Agent 登录

作为已授权用户，我希望 Agent 在短期访问令牌到期后安全续期，以便在合理期限内无需反复登录。

**主流程**：

1. Access Token 在 15 分钟内用于访问目标 MCP 资源。
2. Access Token 到期后，Agent 使用当前 Refresh Token 请求续期。
3. IAM 签发新的 Access Token 与新的 Refresh Token，并立即使旧 Refresh Token 失效。
4. 授权链达到 30 天绝对有效期或 Refresh Token 连续 7 天未使用后，用户需重新浏览器登录。

**异常流程**：已失效或已使用的 Refresh Token 被再次提交时，IAM 撤销该授权链的全部 Refresh Token；登出、修改密码时撤销相关授权链。

**验收标准**：

- AS-MCP-02.1：同一 Refresh Token 不能成功兑换两次。
- AS-MCP-02.2：检测到 Refresh Token 重用后，该授权链中的最新 Refresh Token 也不能再续期。
- AS-MCP-02.3：Token 原文不进入服务端数据库、应用日志、MCP 工具输出或工作区文件。

### US-MCP-03 查询公开能力与注册

作为尚未登录的平台访客，我希望通过 Agent 查询平台能力并取得注册链接，以便完成注册后登录。

**主流程**：

1. Agent 调用无需认证的 `get_ai_devops_capabilities`。
2. MCP 返回不含用户数据和凭据的能力目录。
3. 用户选择注册时，Agent 调用 `register_ai_devops` 并打开平台返回的安全注册链接。
4. 注册完成后，用户通过标准 OAuth 浏览器流程登录。

**验收标准**：

- AS-MCP-03.1：未认证状态只能访问明确标识为公开的能力与注册入口。
- AS-MCP-03.2：注册链接、登录页和授权页不得把密码回传给 MCP 工具。

### US-MCP-04 Codex OAuth 不可用时完成本机登录

作为本机开发用户，当 Codex 未显示远程 MCP 的 OAuth 授权入口时，我希望通过一次性浏览器链接登录，以便继续安全使用本机 AI DevOps 工具。

**验收标准**：

- AS-MCP-04.1：回退登录工具不接收用户名、邮箱、密码或 Token 参数，只返回本机一次性链接和过期时间。
- AS-MCP-04.2：浏览器登录完成后，会话仅保存到当前 Windows 用户的凭据管理器；密码和 Token 不进入聊天、MCP 输出或日志。
- AS-MCP-04.3：远程 OAuth 与本机回退配置必须互斥，防止同一工具名注册两次或混用会话。

## 功能需求

- FR-MCP-001：正式 MCP Server 必须使用 Streamable HTTP；不得以 stdio JAR 作为正式部署或客户端凭据代理。
- FR-MCP-002：需要登录的 MCP 工具必须验证 Bearer Access Token，并以 IAM 返回的认证主体进行授权。
- FR-MCP-003：未认证仅可访问静态能力发现和注册引导等明确公开的工具。
- FR-MCP-004：MCP 必须向兼容客户端公布 OAuth 保护资源元数据与授权服务器发现地址。
- FR-MCP-005：本地验证必须使用最终 HTTP/OAuth 协议；生产环境必须 HTTPS，开发环境仅可将 HTTP 绑定至 `127.0.0.1`。
- FR-MCP-006：MCP 不得保存用户 Access Token、Refresh Token、密码或 Windows 凭据；客户端负责保存自己取得的 Token。
- FR-MCP-007：能力目录必须由实际注册的 MCP 工具生成，且公开目录不读取用户身份或 Token。
- FR-MCP-008：本机回退仅允许连接 `127.0.0.1` 平台地址，使用 stdio MCP 与一次性浏览器登录链接；不得作为公网部署或多用户凭据代理。
- FR-MCP-009：启动配置必须在远程 OAuth 与本机回退之间明确二选一；切换模式不迁移或复用另一模式的凭据。

## 成功标准

- Codex 和其他兼容 Agent 能通过同一协议完成浏览器登录并调用受保护 MCP 工具。
- 泄露单个短期 Access Token 的影响被限制在其有效期内；Refresh Token 重用可被检测并切断授权链。
- IAM 始终是唯一认证与授权来源；MCP 不复制角色、权限项或租户授权规则。

## 迁移说明

远程 OAuth 是正式公网模式。本机 stdio + Windows 凭据管理器仅在 Codex 未提供 OAuth 授权入口时作为开发回退；两种连接不能在同一 Codex 配置中并行启用，切换时必须显式替换连接配置。
