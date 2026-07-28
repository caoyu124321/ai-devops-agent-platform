# 项目管理与预配置模块数据模型

**数据库**：MySQL 8+  
**迁移规划**：`V007__create_project_management.sql`（尚未创建、尚未执行）  
**约定**：UUID 使用 `char(36)`；时间使用 UTC `datetime(3)`；所有秘密只保存密文，绝不保存或返回明文。

## 1. 表清单

| 表 | 目的 |
| --- | --- |
| `pm_projects` | 当前项目与租户归属 |
| `pm_project_versions` | 项目不可变配置版本 |
| `pm_repositories` | 项目当前仓库配置与校验状态 |
| `pm_repository_versions` | 仓库不可变配置版本 |
| `pm_credentials` | 租户凭据元数据、启停状态与当前版本 |
| `pm_credential_versions` | 加密后的凭据版本 |
| `pm_credential_project_grants` | 凭据可被哪些项目引用 |
| `pm_environments` | 当前环境状态与连接健康状态 |
| `pm_environment_versions` | 环境通用不可变版本 |
| `pm_kubernetes_environment_configs` | Kubernetes 目标配置 |
| `pm_kubernetes_allowed_namespaces` | Kubernetes 环境允许命名空间 |
| `pm_linux_host_configs` | Linux SSH 目标配置 |
| `pm_windows_host_configs` | Windows HTTPS WinRM 目标配置 |

不创建项目成员表：项目角色继续由 IAM 的 `iam_project_role_bindings` 管理。不会创建环境“插件支持状态”表；插件兼容性属于后续插件模块。

## 2. 项目与仓库

### `pm_projects`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 项目 ID |
| `tenant_id` | `char(36)` 非空，FK → `iam_tenants.id` | 租户边界 |
| `name` | `varchar(128)` 非空 | 同一租户内唯一 |
| `description` | `varchar(500)` 可空 | 非敏感项目说明 |
| `current_version_no` | `int` 非空 | 当前项目版本，创建时为 1 |
| `created_by` | `char(36)` 非空，FK → `iam_users.id` | 创建用户 |
| `created_at` / `updated_at` | `datetime(3)` 非空 | 时间戳 |

约束与索引：`unique(tenant_id, name)`；`unique(id, tenant_id)` 供跨表租户完整性约束；`index(tenant_id, created_at)` 用于列表。

### `pm_project_versions`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 项目版本 ID |
| `project_id` | `char(36)` 非空，FK → `pm_projects.id` | 所属项目 |
| `version_no` | `int` 非空 | 从 1 连续递增 |
| `name` / `description` | 与项目当前字段一致 | 当时配置快照 |
| `created_by` / `created_at` | 非空 | 版本创建信息 |

约束：`unique(project_id, version_no)`；版本记录只插入，不更新和删除（项目硬删除时随项目级联删除）。

### `pm_repositories`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 仓库 ID |
| `tenant_id` / `project_id` | 非空 | 归属边界；`(project_id, tenant_id)` 必须引用项目 |
| `canonical_url` | `varchar(512)` 非空 | 规范化后的公开 GitHub HTTPS 地址 |
| `default_branch` | `varchar(255)` 非空 | GitHub 读取或管理员覆盖的默认分支 |
| `current_version_no` | `int` 非空 | 当前版本 |
| `connection_status` | `varchar(16)` 非空 | `HEALTHY`、`UNAVAILABLE` |
| `last_checked_at` / `last_error_code` | 可空 | 最近校验时间与安全错误码 |
| `created_by` / `created_at` / `updated_at` | 非空 | 时间戳与创建人 |

约束与索引：`unique(project_id, canonical_url)`；`index(project_id, connection_status)`。每项目最多 20 条由同一事务内的服务层计数并加项目行锁保证。

### `pm_repository_versions`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 仓库版本 ID |
| `repository_id` | 非空 FK → `pm_repositories.id` | 所属仓库 |
| `version_no` | `int` 非空 | 版本号 |
| `canonical_url` / `default_branch` | 非空 | 版本快照 |
| `created_by` / `created_at` | 非空 | 版本创建信息 |

约束：`unique(repository_id, version_no)`；仅追加。

## 3. 凭据与项目授权

### `pm_credentials`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 凭据 ID |
| `tenant_id` | `char(36)` 非空，FK → `iam_tenants.id` | 所属租户 |
| `name` | `varchar(128)` 非空 | 租户内展示名称，不含秘密 |
| `credential_type` | `varchar(32)` 非空 | `KUBECONFIG`、`SSH_PASSWORD`、`SSH_PRIVATE_KEY`、`WINRM_PASSWORD`、`GITHUB_TOKEN` |
| `status` | `varchar(16)` 非空 | `ACTIVE`、`DISABLED` |
| `current_version_no` | `int` 非空 | 当前密文版本 |
| `created_by` / `created_at` / `updated_at` | 非空 | 时间戳与创建人 |

约束：`unique(tenant_id, name)`；`unique(id, tenant_id)`；`index(tenant_id, status)`。

### `pm_credential_versions`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 凭据版本 ID |
| `credential_id` | `char(36)` 非空 FK → `pm_credentials.id` | 所属凭据 |
| `version_no` | `int` 非空 | 从 1 递增 |
| `encrypted_payload` | `mediumblob` 非空 | 加密后的类型专属秘密载荷 |
| `encryption_key_id` | `varchar(128)` 非空 | 应用配置中主密钥的标识，不保存主密钥 |
| `encryption_algorithm` | `varchar(64)` 非空 | 加密算法标识 |
| `created_by` / `created_at` | 非空 | 轮换或创建信息 |

约束：`unique(credential_id, version_no)`。秘密字段集合为：`KUBECONFIG={kubeconfig}`；`SSH_PASSWORD={username,password}`；`SSH_PRIVATE_KEY={username,privateKey,passphrase?}`；`WINRM_PASSWORD={username,password}`；`GITHUB_TOKEN={token}`。这些字段解密后仅在受控校验或未来执行时短暂使用。

### `pm_credential_project_grants`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 授权 ID |
| `tenant_id` / `credential_id` / `project_id` | 非空 | 三者必须属于同一租户 |
| `granted_by` / `granted_at` | 非空 | 租户管理员授权信息 |

约束与索引：`unique(credential_id, project_id)`；`index(project_id, credential_id)`。服务层在同一事务中验证凭据和项目的租户一致性。

## 4. 环境与目标配置

### `pm_environments`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 环境 ID |
| `tenant_id` / `project_id` | 非空 | 环境归属项目及租户边界 |
| `name` | `varchar(128)` 非空 | 项目内唯一名称 |
| `target_type` | `varchar(32)` 非空 | `KUBERNETES`、`LINUX_HOST`、`WINDOWS_HOST` |
| `environment_level` | `varchar(16)` 非空 | `DEV`、`TEST`、`STAGING`、`PROD` |
| `enabled` | `tinyint(1)` 非空 | 管理员显式启停 |
| `connection_status` | `varchar(16)` 非空 | `UNKNOWN`、`HEALTHY`、`UNAVAILABLE` |
| `last_checked_at` / `last_error_code` | 可空 | 最近校验结果；不得保存秘密或原始响应 |
| `current_version_no` | `int` 非空 | 当前环境配置版本 |
| `created_by` / `created_at` / `updated_at` | 非空 | 时间戳与创建人 |

约束与索引：`unique(project_id, name)`；`index(project_id, enabled, connection_status)`；`index(tenant_id, environment_level)`。环境可被后续使用的必要条件为 `enabled=1 AND connection_status=HEALTHY`，还需 IAM 授权和插件兼容性。

### `pm_environment_versions`

| 字段 | 类型 / 约束 | 说明 |
| --- | --- | --- |
| `id` | `char(36)` PK | 环境版本 ID |
| `environment_id` | `char(36)` 非空 FK → `pm_environments.id` | 所属环境 |
| `version_no` | `int` 非空 | 版本号 |
| `target_type` / `environment_level` | 非空 | 配置快照 |
| `credential_id` | `char(36)` 非空 FK → `pm_credentials.id` | 引用当前凭据，不保存其秘密 |
| `created_by` / `created_at` | 非空 | 版本创建信息 |

约束：`unique(environment_id, version_no)`。服务层校验凭据处于 `ACTIVE` 且已授权给该项目。

### 类型专属配置表

| 表 | 主键及外键 | 字段 | 关键约束 |
| --- | --- | --- | --- |
| `pm_kubernetes_environment_configs` | `environment_version_id` PK / FK → `pm_environment_versions.id` | `api_server_url varchar(512)`、`context_name varchar(255)`、`default_namespace varchar(253)` | 仅关联 `KUBERNETES` 版本；默认命名空间存在于允许列表 |
| `pm_kubernetes_allowed_namespaces` | `id` PK，`environment_version_id` FK | `namespace varchar(253)` | `unique(environment_version_id, namespace)` |
| `pm_linux_host_configs` | `environment_version_id` PK / FK | `host varchar(255)`、`port int`、`host_key_fingerprint varchar(255)` | 仅关联 `LINUX_HOST`；端口 1–65535；指纹非空 |
| `pm_windows_host_configs` | `environment_version_id` PK / FK | `endpoint_url varchar(512)`、`certificate_fingerprint varchar(255)` | 仅关联 `WINDOWS_HOST`；URL 必须 HTTPS；证书指纹非空 |

目标类型与专属表一致性由服务层和迁移中的检查约束共同保证；MySQL 不支持跨表条件外键时，服务层是最终防线。

## 5. 删除、版本和保留规则

- 项目硬删除级联删除项目、项目/仓库/环境版本及项目环境配置；删除前服务层撤销 IAM 项目角色和验证未来运行取消条件。
- 凭据不硬删除。停用后不允许新环境或新运行使用，已创建运行的版本引用按后续运行保留策略处理。
- 仓库在永久不可访问时硬删除其当前与版本记录；网络暂时故障仅更新安全错误码。
- 本模块不保存连接校验明细或审计日志，只保存当前健康状态、最后时间和安全错误码。
