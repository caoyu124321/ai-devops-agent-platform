# IAM OAuth/OIDC 与远程 MCP 技术设计

## 1. 设计结论

现有 `iam` 模块增加 `oauth` 子域，作为 OAuth 2.0 Authorization Server 与 OpenID Provider；`mcp` 模块从独立 stdio 进程调整为不含启动类的 HTTP 适配模块，由唯一的 `devops.AiDevopsAgentPlatformApplication` 装配并提供 `/mcp` 端点。

```text
兼容 Agent（Codex 等）
  │ 发现元数据、授权码 + PKCE、Bearer Access Token
  ▼
application（唯一 Spring Boot 进程）
  ├─ /mcp                 Streamable HTTP MCP 资源服务器
  ├─ /oauth/*             IAM OAuth/OIDC 授权服务器
  ├─ /.well-known/*       公开元数据
  └─ /api/v1/*            既有 REST API
        │
        ├─ IAM 身份、会话、OAuth 授权链
        └─ IAM 授权门面（资源 + 动作 + 范围）
```

`mcp` 只依赖 IAM 的公开认证主体和授权决策契约；它不读取 IAM 持久化模型，也不解释角色、租户、项目或环境。

## 2. 端点与协议边界

| 路径 | 用途 | 认证 |
| --- | --- | --- |
| `GET /.well-known/openid-configuration` | OIDC Discovery | 无 |
| `GET /.well-known/oauth-authorization-server` | OAuth 授权服务器元数据 | 无 |
| `GET /.well-known/oauth-protected-resource/mcp` | MCP 资源服务器元数据 | 无 |
| `GET /oauth/authorize` | 浏览器授权入口 | 浏览器登录会话或登录页 |
| `POST /oauth/token` | 授权码兑换与刷新 | 客户端标识；公共客户端使用 PKCE |
| `POST /oauth/revoke` | 撤销指定 Token 或当前授权链 | 已认证客户端请求 |
| `POST /oauth/introspect` | 受控资源服务器查询不透明 Token | 仅内部受信资源服务器 |
| `GET /oauth/userinfo` | OIDC 用户安全摘要 | Bearer Access Token + `openid` Scope |
| `GET /oauth/jwks` | ID Token 验签公钥集合 | 无 |
| `POST /oauth/register` | 动态公共客户端注册 | 无，限流 |
| `POST /mcp` | Streamable HTTP MCP | 公开工具或 Bearer Access Token |

Access Token 的 `audience` 固定为配置的 `${issuer}/mcp`，并与 MCP 受保护资源元数据中的 `resource` 一致。后续 REST 资源如需复用 OAuth，必须登记独立受众，不能接受面向 MCP 的 Token。

## 3. 授权码与浏览器会话

1. Agent 从 Discovery 读取端点，生成随机 `state`、`code_verifier` 与 `S256(code_verifier)`。
2. `GET /oauth/authorize` 校验 `client_id`、客户端状态、`redirect_uri`、响应类型、Scope、受众与 PKCE 参数。
3. 用户在 IAM 浏览器页面登录；浏览器会话采用独立随机不透明 Cookie，设置 `HttpOnly`、`Secure`（开发 `127.0.0.1` 例外）、`SameSite=Lax`，服务端仅保存哈希。
4. 用户查看客户端名称、请求 Scope 和 MCP 受众并同意或拒绝。授权同意与业务授权分离：它只允许该客户端使用协议 Scope，不授予任何租户业务权限。
5. 同意后，创建 5 分钟且一次性的授权码记录，重定向到精确匹配的回调地址，附带 `code` 与原 `state`。
6. `POST /oauth/token` 使用授权码、`client_id`、`redirect_uri` 和 `code_verifier` 原子消费授权码；校验通过才签发 Token。

禁止密码流程、隐式流程、把用户密码发送给 MCP 或由 MCP 模拟用户登录。

## 4. Token 与授权链设计

### 4.1 Token 形式

- Access Token、Refresh Token、授权码、浏览器 Cookie 都由密码学安全随机源生成。
- 持久化层只保存带版本的不可逆哈希、所属记录和生命周期状态；原文仅在签发响应中短暂存在。
- Access Token：15 分钟固定有效，不延长。
- ID Token：仅用于 OIDC 客户端确认登录结果，以平台私钥签名；不作为 MCP API 凭据。

### 4.2 Refresh Token 轮换

```text
授权链 G（绝对截止：首次同意后 30 天）
  R1 ──成功刷新──> R1=已轮换，签发 A2 + R2
  R1 ──再次提交──> 判定重用，撤销 G 下 A*、R*，要求重新登录
```

刷新请求必须在单个事务中锁定当前授权链与 Refresh Token：先检查授权链未撤销、绝对截止时间和最近成功使用时间，再使旧 Token 进入 `ROTATED`，签发新 Token，并更新最后使用时间。重用检测与撤销必须在同一事务可见，避免并发刷新签发两条有效分支。

`offline_access` 是取得 Refresh Token 的必要 Scope；未请求或未同意该 Scope 时只签发 Access Token。

### 4.3 撤销规则

| 事件 | 结果 |
| --- | --- |
| OAuth 客户端登出或 `/oauth/revoke` | 撤销指定 Token；撤销 Refresh Token 时同时撤销其授权链。 |
| 修改平台密码 | 撤销该用户全部 OAuth 授权链、Access Token 与浏览器授权会话。 |
| 客户端停用 | 撤销该客户端全部有效授权链与 Access Token。 |
| 成员被移除/角色变化 | 不撤销身份 Token；MCP 下一次请求重新通过 IAM 授权门面判断资源访问。 |
| Refresh Token 重用 | 撤销该授权链全部 Token。 |

## 5. 客户端注册与回调地址

动态注册只创建 `token_endpoint_auth_method=none` 的公共客户端，返回 `client_id`，不创建或返回 `client_secret`。

客户端状态：

| 状态 | 行为 |
| --- | --- |
| `PENDING` | 已登记，不能开始授权。 |
| `ACTIVE` | 可使用已登记回调地址开始授权。 |
| `SUSPENDED` | 立即拒绝新授权并撤销有效 OAuth 授权链。 |

对外开放模式下，未知公共客户端默认创建为 `ACTIVE`。该默认值不代表客户端品牌可信：客户端仍必须完成严格回调地址校验、PKCE S256、用户登录与同意，并受动态注册限流约束；平台可按精确 `client_id` 将任何客户端覆盖为 `PENDING` 或 `SUSPENDED`。

生产回调地址只接受 HTTPS，或 RFC 原生应用规则允许的 `localhost` 地址；比较时严格匹配完整登记地址。开发 Profile 仅额外允许 `http://127.0.0.1:<port>`。动态注册接口需要按来源与客户端名称限流，并对异常注册拒绝服务；不记录密码、Token 或请求主体中的敏感内容。

### 5.1 品牌软件声明与配置覆盖

`software_statement` 是可选的签名 JWT。可信品牌注册表仅由后端配置维护，包含品牌编码、签发方、JWK Set 地址、软件标识和启用状态。注册时必须验证 JWT 的签名、`iss`、`aud`、`exp`、`software_id` 及声明内回调地址；经验证的声明元数据优先于普通请求字段。

品牌与实例状态的优先级为：显式 `SUSPENDED` 覆盖 > 禁用品牌 > 配置中精确 `client_id` 覆盖 > 已验证且启用的品牌 > 本地回环开发自动启用 > 未知客户端默认状态。客户端名称绝不能参与该判定。

未知客户端的默认状态、实例 `client_id` 覆盖和品牌注册表均由后端配置加载，首期在重启服务后生效。签发方 JWK 地址只能来自预配置品牌，不得由客户端注册请求指定，以避免 SSRF。

## 6. OIDC 签名密钥

- 使用非对称签名密钥签发 ID Token，优先 `EdDSA`；`/oauth/jwks` 只公开公钥与 `kid`。
- 私钥不得进入数据库、配置仓库或日志；从生产 Secret/KMS 注入，开发环境使用隔离的测试密钥。
- 新旧公钥并存至少覆盖最长 ID Token 生命周期后再移除旧 `kid`。

## 7. MCP 认证与授权

1. MCP 传输层读取 `Authorization: Bearer <access-token>`，不把 Token 暴露给工具处理器。
2. 认证组件通过 IAM 内部 Token 校验门面验证 Token 状态、受众、过期时间与 `mcp.tools` Scope，得到 `AuthenticatedSubject`。
3. 工具处理器仅将主体、抽象资源标识、动作、范围和上下文提交给 IAM 授权门面。
4. IAM 返回允许或安全拒绝。MCP 不缓存角色和授权结果；成员移除与角色变化下次调用立即生效。

公开工具白名单只包含能力发现和注册引导。除该白名单外，缺失、错误、过期或受众不匹配的 Token 都返回同一未认证结果。

## 8. 逻辑持久化模型

本节为实现边界，不定义 SQL 或 ORM 实体。

| 逻辑记录 | 核心字段 | 用途 |
| --- | --- | --- |
| OAuth 客户端 | 客户端 ID、名称、回调地址集合、状态、创建/更新时间 | 公共客户端识别与启用控制。 |
| 用户授权链 | 授权链 ID、用户 ID、客户端 ID、受众、Scope、绝对截止、最后使用、撤销状态 | Refresh Token 轮换和整体撤销边界。 |
| 授权码 | 哈希、授权链上下文、PKCE Challenge、回调地址、到期、消费时间 | 单次授权码兑换。 |
| OAuth Access Token | 哈希、用户/客户端/授权链、受众、Scope、到期、撤销时间 | MCP Access Token 校验与即时撤销。 |
| OAuth Refresh Token | 哈希、授权链、父 Token、状态、轮换时间、到期、最后使用 | 轮换、重用检测和撤销。 |
| 浏览器授权会话 | 哈希、用户 ID、到期、撤销时间 | 授权页登录态，不替代 API Token。 |
| 用户客户端同意 | 用户、客户端、受众、Scope、同意/撤销时间 | 显示授权页和管理已同意授权。 |

## 9. 部署与本地终版验证

生产：反向代理终止 TLS，分别暴露 `https://auth.<domain>` 与 `https://mcp.<domain>/mcp`，或在同一受控域名下按路径分流。服务端必须配置可信外部 URL，禁止由可伪造请求头推导回调或 Issuer。

本地：同一个 Spring Boot 进程监听 `127.0.0.1:8080`，使用 `http://127.0.0.1:8080/mcp` 和本地 OAuth Discovery。Codex 等客户端配置 URL 型 MCP Server 并使用 OAuth 登录；不执行 JAR、stdio 管道或 Windows Credential Manager。`0.0.0.0` 仅可作为受明确控制的开发调试选项，不能是默认值。
