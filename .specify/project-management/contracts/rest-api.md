# 项目管理与预配置 REST 接口契约

**统一前缀**：`/api/v1`  
**认证**：IAM 会话 Token 或 OAuth Bearer Token；两者均解析为 IAM 当前用户。  
**通用响应**：成功对象含 `id`、`version`、时间戳和非敏感状态；错误对象含 `code`、`message`、`traceId`，不回显秘密。

## 1. 项目

| 接口 | 调用方 / 授权 | 请求摘要 | 成功结果 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /tenants/{tenantId}/projects` | 租户管理员，`project.create` | `name`、可选 `description` | `201`，项目与创建人项目管理员绑定 | `PROJECT_NAME_INVALID`、`PROJECT_NAME_EXISTS`、`ACCESS_DENIED` |
| `GET /tenants/{tenantId}/projects` | 租户成员，`project.view` 过滤 | 游标、页大小 | 可见项目列表 | `AUTHENTICATION_REQUIRED` |
| `GET /projects/{projectId}` | 项目可见用户 | 无 | 项目当前版本摘要 | `PROJECT_NOT_FOUND` |
| `PATCH /projects/{projectId}` | 租户管理员或项目管理员，`project.update` | `name`、`description`、`expectedVersion` | 新的不可变版本摘要 | `PROJECT_VERSION_CONFLICT`、`PROJECT_NAME_EXISTS` |
| `DELETE /projects/{projectId}` | 租户管理员，`project.delete` | 无 | `204` | `PROJECT_DELETE_BLOCKED`、`PROJECT_NOT_FOUND` |

创建项目时服务端自动创建版本 1 并为创建人绑定 `PROJECT_ADMIN`。项目列表和单项读取不泄露跨租户资源。

## 2. 公开 GitHub 仓库

| 接口 | 授权 | 请求摘要 | 成功结果 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /projects/{projectId}/repositories` | `repository.modify` | `url`、可选 `defaultBranch` | `201`，规范化 URL、默认分支、健康状态 | `REPOSITORY_LIMIT_EXCEEDED`、`REPOSITORY_URL_INVALID`、`REPOSITORY_NOT_PUBLIC` |
| `GET /projects/{projectId}/repositories` | `repository.view` | 游标、页大小 | 仓库元数据列表 | `PROJECT_NOT_FOUND` |
| `PATCH /repositories/{repositoryId}` | `repository.modify` | `url`、`defaultBranch`、`expectedVersion` | 新版本摘要 | `REPOSITORY_VERSION_CONFLICT`、`REPOSITORY_NOT_PUBLIC` |
| `POST /repositories/{repositoryId}/validation` | `repository.modify` | 无 | 当前校验状态和默认分支 | `REPOSITORY_VALIDATION_FAILED` |
| `DELETE /repositories/{repositoryId}` | `repository.modify` | 无 | `204` | `REPOSITORY_NOT_FOUND` |

`url` 必须是匿名可读的 GitHub HTTPS URL。新增、更新与显式校验均只读访问远端；确认仓库不存在或不再公开时，服务端删除仓库配置并返回 `REPOSITORY_REMOVED` 业务结果。网络暂时故障仅返回校验失败。

## 3. 凭据与项目授权

| 接口 | 授权 | 请求摘要 | 成功结果 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /tenants/{tenantId}/credentials` | 租户管理员，`credential.manage` | `name`、`type`、类型专属 `secret` | `201`，仅返回 ID、名称、类型、版本和状态 | `CREDENTIAL_TYPE_INVALID`、`CREDENTIAL_PAYLOAD_INVALID` |
| `GET /tenants/{tenantId}/credentials` | 租户管理员，`credential.view` | 游标、页大小 | 不含秘密的租户凭据列表 | `ACCESS_DENIED` |
| `GET /projects/{projectId}/credential-references` | 可管理该项目环境的用户 | 游标、页大小 | 已授权给该项目的不含秘密凭据引用 | `PROJECT_NOT_FOUND` |
| `PATCH /credentials/{credentialId}` | 租户管理员 | `name`、`expectedVersion` | 凭据元数据更新 | `CREDENTIAL_VERSION_CONFLICT` |
| `POST /credentials/{credentialId}/rotations` | 租户管理员 | 类型专属 `secret`、`expectedVersion` | `201`，新凭据版本号 | `CREDENTIAL_DISABLED`、`CREDENTIAL_PAYLOAD_INVALID` |
| `POST /credentials/{credentialId}/disable` | 租户管理员 | `expectedVersion` | 停用后的元数据及受影响环境数量 | `CREDENTIAL_VERSION_CONFLICT` |
| `POST /credentials/{credentialId}/project-grants` | 租户管理员，`credential.grant` | `projectId` | `201`，非敏感授权摘要 | `PROJECT_NOT_FOUND`、`TENANT_MISMATCH` |
| `DELETE /credentials/{credentialId}/project-grants/{projectId}` | 租户管理员 | 无 | `204` | `CREDENTIAL_GRANT_NOT_FOUND` |

`secret` 只允许出现在写入请求。响应、日志、错误与列表中不得出现 `secret` 子字段、密码、私钥、Token、kubeconfig 或主密钥。

## 4. 项目环境

| 接口 | 授权 | 请求摘要 | 成功结果 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /projects/{projectId}/environments` | `environment.modify` | `name`、`targetType`、`environmentLevel`、`credentialId`、类型专属 `target` | `201`，环境及初次连接状态 | `ENVIRONMENT_NAME_EXISTS`、`CREDENTIAL_NOT_GRANTED`、`TARGET_CONFIGURATION_INVALID` |
| `GET /projects/{projectId}/environments` | `environment.view` | 可选等级、目标类型、游标 | 环境元数据列表 | `PROJECT_NOT_FOUND` |
| `GET /environments/{environmentId}` | `environment.view` | 无 | 当前版本和脱敏配置摘要 | `ENVIRONMENT_NOT_FOUND` |
| `PATCH /environments/{environmentId}` | `environment.modify` | 名称、等级、凭据、目标、`expectedVersion` | 新配置版本与连接状态 | `ENVIRONMENT_VERSION_CONFLICT`、`CREDENTIAL_NOT_GRANTED` |
| `POST /environments/{environmentId}/validation` | `environment.modify` | 无 | 更新后的连接状态 | `ENVIRONMENT_VALIDATION_FAILED` |
| `POST /environments/{environmentId}/enable` | `environment.modify` | `expectedVersion` | 启用后的摘要；不健康环境仍不可被后续运行使用 | `ENVIRONMENT_VERSION_CONFLICT` |
| `POST /environments/{environmentId}/disable` | `environment.modify` | `expectedVersion` | 停用后的摘要 | `ENVIRONMENT_NOT_FOUND` |
| `DELETE /environments/{environmentId}` | `environment.modify` | 无 | `204` | `ENVIRONMENT_NOT_FOUND` |

### 环境请求 `target` 形状

```json
{
  "KUBERNETES": {
    "apiServerUrl": "https://cluster.example.com:6443",
    "contextName": "production",
    "defaultNamespace": "app",
    "allowedNamespaces": ["app", "app-canary"]
  },
  "LINUX_HOST": {
    "host": "10.0.0.10",
    "port": 22,
    "hostKeyFingerprint": "SHA256:..."
  },
  "WINDOWS_HOST": {
    "endpointUrl": "https://10.0.0.20:5986/wsman",
    "certificateFingerprint": "SHA256:..."
  }
}
```

创建或更新环境会立即校验连接。校验失败仍返回已保存的环境，`connectionStatus=UNAVAILABLE`；只有启用且健康的环境可由未来运行模块考虑使用。环境响应不返回凭据内容。

## 5. 乐观锁、分页与错误可见性

- 所有更新、轮换、启停接口均使用 `expectedVersion`；版本不匹配返回 `409` 与稳定错误码。
- 列表使用游标分页，默认按创建时间倒序。
- 未认证返回 `401 AUTHENTICATION_REQUIRED`；不可见资源统一返回 `404 <RESOURCE>_NOT_FOUND`，不使用跨租户 `403` 暴露资源存在性。
- 业务校验错误返回 `400`；权限不足但资源可见的操作返回 `403 ACCESS_DENIED`；并发冲突返回 `409`。
