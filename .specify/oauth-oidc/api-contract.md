# OAuth/OIDC 对外契约规划

本文件描述协议边界，不生成 Controller、DTO 或实现代码。所有错误响应不得包含密码、Token、授权码、PKCE 校验值或用户是否存在等信息。

## 1. 公开发现端点

| 端点 | 调用方 | 输出 | 约束 |
| --- | --- | --- | --- |
| `GET /.well-known/openid-configuration` | OIDC 客户端 | issuer、授权/令牌/用户信息/JWK 端点、支持的 Scope 与签名算法 | issuer 必须为配置的外部 HTTPS 地址（本地为 127.0.0.1）。 |
| `GET /.well-known/oauth-authorization-server` | OAuth 客户端 | OAuth 端点、PKCE 方法、动态注册端点、支持的 grant | 仅声明授权码和刷新 Token。 |
| `GET /.well-known/oauth-protected-resource/mcp` | MCP 客户端 | MCP 资源标识、授权服务器地址、支持 Scope | 不包含用户或客户端信息。 |
| `GET /oauth/jwks` | OIDC 客户端 | 当前及过渡期公钥 | 仅公钥；按 `kid` 可缓存。 |

## 2. 动态客户端注册

### `POST /oauth/register`

**调用方**：支持 OAuth 的外部 Agent。  
**输入**：客户端名称、回调地址列表、请求的 Token 端点认证方式（仅 `none`）、支持 grant（仅 `authorization_code`、可选 `refresh_token`）。  
**成功输出**：`client_id`、客户端状态（默认 `ACTIVE`）、允许的 grant、回调地址与下一步说明。  
**主要拒绝**：字段不合法、回调地址不安全、不支持的认证方式/流程、注册限流、来源拒绝。  
**安全边界**：不返回 `client_secret`、注册管理 Token 或任何用户凭据。

## 3. 浏览器授权

### `GET /oauth/authorize`

**调用方**：用户浏览器。  
**输入参数**：`response_type=code`、`client_id`、`redirect_uri`、`scope`、`state`、`code_challenge`、`code_challenge_method=S256`、`resource=${issuer}/mcp`。
**成功结果**：经用户登录与同意后，302 到严格匹配的 `redirect_uri?code=<一次性授权码>&state=<原值>`。  
**用户拒绝**：302 到严格匹配的回调地址，携带标准拒绝错误和原 `state`，不含用户信息。  
**主要拒绝**：客户端未启用、回调地址不匹配、缺失/不支持 PKCE、无效 Scope 或受众、浏览器会话失效。

## 4. Token 端点

### `POST /oauth/token`

**调用方**：已登记公共客户端。  
**授权码输入**：`grant_type=authorization_code`、`code`、`client_id`、`redirect_uri`、`code_verifier`。  
**刷新输入**：`grant_type=refresh_token`、`refresh_token`、`client_id`、可选受众（不得扩大）。  
**成功输出**：

```json
{
  "access_token": "仅在此响应中出现的随机值",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "openid profile mcp.tools offline_access",
  "refresh_token": "仅在获准 offline_access 时出现的随机值",
  "id_token": "OIDC 签名结果"
}
```

**主要拒绝**：授权码失效、重复使用、PKCE 不匹配、刷新 Token 失效/闲置/绝对到期/重用、客户端不匹配、客户端已停用。  
**安全边界**：响应禁止缓存；Token 原文不写日志或数据库；刷新 Token 重用时使用统一安全错误，不告知具体链状态。

## 5. 撤销、自省与用户信息

| 端点 | 调用方 | 目的 | 主要约束 |
| --- | --- | --- | --- |
| `POST /oauth/revoke` | 已登记客户端 | 撤销当前 Access/Refresh Token；Refresh Token 撤销整条授权链 | 幂等成功，不泄露 Token 是否存在。 |
| `POST /oauth/introspect` | 仅内部受信资源服务器 | 校验不透明 Access Token 并获取用户、客户端、受众、Scope、到期信息 | 不对公网匿名开放；不返回 Token 原文。 |
| `GET /oauth/userinfo` | 已持有 `openid` Access Token 的客户端 | 返回 `sub`，并按 Scope 返回 username/email 等安全声明 | 受众、Scope 和 Token 状态均须有效。 |

## 6. MCP 认证契约

`POST /mcp` 采用 Streamable HTTP。需要登录的工具调用必须含：

```http
Authorization: Bearer <access_token>
```

缺少、无效、过期或受众不匹配时返回 HTTP `401`，并携带：

```http
WWW-Authenticate: Bearer resource_metadata="${issuer}/.well-known/oauth-protected-resource/mcp"
```

MCP 认证适配器向工具层提供以下内部只读契约：

```text
AuthenticatedSubject
  userId
  oauthGrantId
  clientId
  audience = ${issuer}/mcp
  scopes
  expiresAt
```

该契约不包含角色、授权项、密码、Access Token 或 Refresh Token。业务工具继续显式请求 IAM 的“资源 + 动作 + 范围”授权决策。
