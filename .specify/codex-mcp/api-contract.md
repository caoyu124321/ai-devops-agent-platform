# MCP 工具契约

## 通用输出

成功输出：

```json
{
  "status": "LOGGED_IN",
  "user": {
    "id": "user-id",
    "username": "username",
    "email": "user@example.com"
  },
  "expiresAt": "2026-07-19T15:30:00Z"
}
```

失败输出：

```json
{
  "status": "ERROR",
  "code": "SESSION_EXPIRED",
  "message": "登录已失效，请重新登录。"
}
```

禁止字段：`token`、`password`、`passwordHash`、`authorization`、后端响应原文。

## `get_ai_devops_capabilities`

| 项目 | 约定 |
| --- | --- |
| 输入 Schema | 空对象 |
| 成功状态 | `CAPABILITIES_AVAILABLE` |
| 输出 | MCP 版本与能力列表；每项包含 `id`、`name`、`description`、`tool`、`loginRequired`、`examples` |
| 副作用 | 无；不访问后端、不读取或写入凭据 |
| 安全限制 | 不返回用户信息、密码、Token、注册链接或登录链接 |

## `register_ai_devops`

| 项目 | 约定 |
| --- | --- |
| 输入 Schema | 空对象；用户名、邮箱和密码均由 MCP 输入提示收集，不属于工具参数 |
| 输入提示 | `username`：用户名；`email`：邮箱；`password`：密码 |
| 成功状态 | `REGISTERED`，返回用户安全摘要 |
| 失败码 | `REGISTRATION_CANCELLED`、`REGISTRATION_FAILED`、`MCP_ELICITATION_UNAVAILABLE`、`BACKEND_UNAVAILABLE` |
| 副作用 | 只创建平台用户；不创建会话，不读写 Windows 凭据管理器 |

## `login_ai_devops`

| 项目 | 约定 |
| --- | --- |
| 输入 Schema | 空对象；凭据由 MCP 输入提示收集，不属于工具参数 |
| 输入提示 | `login`：用户名或邮箱；`password`：密码 |
| 成功状态 | `LOGGED_IN` |
| 失败码 | `LOGIN_CANCELLED`、`LOGIN_FAILED`、`MCP_ELICITATION_UNAVAILABLE`、`CREDENTIAL_STORE_FAILED`、`BACKEND_UNAVAILABLE` |
| 副作用 | 创建后端会话、替换本机活动 Token |

## `get_current_ai_devops_user`

| 项目 | 约定 |
| --- | --- |
| 输入 Schema | 空对象 |
| 成功状态 | `LOGGED_IN`，返回当前用户摘要 |
| 失败码 | `NOT_LOGGED_IN`、`SESSION_EXPIRED`、`BACKEND_UNAVAILABLE` |
| 副作用 | 仅在 401 时清理失效本地 Token |

## `logout_ai_devops`

| 项目 | 约定 |
| --- | --- |
| 输入 Schema | 空对象 |
| 成功状态 | `LOGGED_OUT` |
| 失败码 | 无；后端不可用或会话失效时仍清理本地凭据并返回成功 |
| 副作用 | 请求后端撤销会话、删除本地 Token |

## 错误映射

| 触发条件 | MCP 错误码 | 是否清理 Token |
| --- | --- | --- |
| 无活动凭据 | `NOT_LOGGED_IN` | 否 |
| 后端 401 | `SESSION_EXPIRED` | 是 |
| 后端 403/资源不可见 | `BACKEND_ACCESS_DENIED` | 否 |
| 登录请求被拒绝 | `LOGIN_FAILED` | 否 |
| 网络错误、超时、5xx | `BACKEND_UNAVAILABLE` | 否 |
| 凭据管理器异常 | `CREDENTIAL_STORE_FAILED` | 否 |
