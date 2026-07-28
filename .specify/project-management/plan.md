# 项目管理与预配置模块技术设计

**状态**：已获准进行技术设计，尚未开始实现  
**依据**：[产品规格](spec.md)、[需求检查清单](requirements-checklist.md)、[IAM 授权模型](../iam/authorization-model.md)

## 1. 实现边界

新增 Maven 模块 `project-management`，包根为 `devops.projectmanagement`。`application` 依赖该模块并作为唯一 Spring Boot 启动模块；`project-management` 可依赖 `iam` 暴露的授权和项目角色绑定契约，`iam` 不得反向依赖项目模块。

```text
application
 ├─ iam                 身份、租户、统一授权、项目角色绑定
 └─ project-management  项目、仓库、凭据、环境与连通性校验
      ├─ api            REST Controller，仅做参数转换
      ├─ service        业务规则、事务、IAM 授权调用
      ├─ dao            MyBatis Mapper 与持久化对象
      └─ integration    GitHub / Kubernetes / SSH / WinRM 校验适配器
```

本模块只配置与校验资源：不执行 Kubernetes、SSH 或 WinRM 部署；不提供流水线、插件、产物、MCP 业务工具或二次确认实现。

## 2. 授权设计

所有入口在服务层通过 IAM 的 `AuthorizationRequest` 校验。资源归属、环境健康和启用状态由项目模块先确认，再将真实的租户、项目、环境范围提交给 IAM。

| 场景 | 资源 / 动作 | 范围 | 默认允许者 |
| --- | --- | --- | --- |
| 创建项目 | `project.create` | 租户 | 租户管理员 |
| 查看、修改、删除项目 | `project.view` / `project.update` / `project.delete` | 项目 | 租户管理员、项目管理员 |
| 管理仓库 | `repository.view` / `repository.modify` | 项目 | 租户管理员、项目管理员 |
| 管理凭据与项目授权 | `credential.manage` / `credential.grant` | 租户 | 租户管理员 |
| 查看凭据元数据 | `credential.view` | 租户 | 租户管理员；项目管理员仅可通过其具体项目查询已授权的非敏感引用 |
| 管理环境 | `environment.view` / `environment.modify` | 项目 / 环境 | 租户管理员、项目管理员 |
| 后续使用仓库/环境 | `repository.use` / `environment.deploy` | 项目 / 环境 | 由 IAM 内置角色和后续授权决定 |

创建项目成功后，在同一业务事务的提交后调用 IAM 建立创建人的 `PROJECT_ADMIN` 绑定；项目硬删除前撤销对应项目范围角色。IAM 只接收抽象 `projectId`，不读取项目表。

## 3. 外部校验适配器

| 适配器 | 触发时机 | 允许行为 | 结果处理 |
| --- | --- | --- | --- |
| GitHubRepositoryChecker | 新增、更新、显式重新校验仓库 | 只读访问公开 GitHub HTTPS 仓库，读取存在性与默认分支 | 不存在或不再公开：删除仓库配置；网络/服务暂时错误：保留配置并返回校验失败 |
| KubernetesEnvironmentChecker | 新增、更新、重试 Kubernetes 环境 | API Server 认证、读取允许命名空间 | 成功更新为健康；失败保存/更新为 `UNAVAILABLE`，不得写入集群 |
| SshEnvironmentChecker | 新增、更新、重试 Linux 主机 | SSH 认证并严格校验已配置主机指纹 | 成功更新为健康；失败为 `UNAVAILABLE` |
| WinRmEnvironmentChecker | 新增、更新、重试 Windows 主机 | HTTPS WinRM 认证并严格校验证书指纹 | 成功更新为健康；失败为 `UNAVAILABLE` |

适配器日志只记录资源 ID、目标类型、结果码和脱敏原因；不得输出 URL 中的敏感参数、凭据、Token、私钥、密码、kubeconfig 或完整远程响应。

## 4. 配置、版本与删除策略

- 项目、仓库、环境配置使用“当前记录 + 不可变版本记录”模型；更新生成新版本，不覆盖旧版本。
- 凭据当前记录仅保存名称、类型、启停状态和当前版本号；密文只保存在凭据版本记录中。
- 新环境配置引用凭据 ID；未来运行创建时解析该凭据的当前版本，并将项目、仓库、环境、凭据版本 ID 固定到运行快照。
- 环境“启用状态”和“连接健康状态”属于环境当前状态；是否有兼容部署插件由未来插件模块判断，不写入环境表。
- 项目硬删除在当前阶段不需要调用运行模块（尚无运行数据）。运行模块上线后，必须在启用项目删除入口前实现“取消未终态运行；取消失败则删除失败”的协作契约。

## 5. 数据库与迁移

- MySQL 8+ 为正式数据库，H2 提供测试等价 Schema。
- 计划新增 `database/migrations/V007__create_project_management.sql` 与对应 H2 Schema；本计划不创建或执行该迁移。
- 表、外键、唯一索引与敏感字段边界详见 [data-model.md](data-model.md)。
- 时间使用 `datetime(3)` UTC；主键使用 `char(36)` UUID；所有查询必须以 `tenant_id` 或由项目反查出的租户边界约束。

## 6. 接口与错误策略

REST 契约见 [contracts/rest-api.md](contracts/rest-api.md)。认证适配器同时接受 IAM 会话 Token 与 OAuth Bearer Token，并解析为同一 IAM 当前用户。

- 未认证：统一认证错误。
- 跨租户、无权限或不存在的资源：返回不可见语义，不暴露资源归属。
- 输入不合法、凭据类型不匹配、环境配置不完整、仓库不公开、资源上限：返回稳定业务错误码。
- 所有凭据写入接口必须拒绝在响应体回显敏感字段。

## 7. 验证策略

- 服务层单元测试覆盖规格中全部 `AS-*` 场景和 IAM 拒绝路径。
- MyBatis / MySQL 集成测试覆盖外键、唯一索引、租户隔离、版本号和项目最多 20 仓库约束。
- GitHub、Kubernetes、SSH、WinRM 使用适配器契约测试；真实网络测试必须可选择性运行，不依赖公共网络稳定性。
- Web 层测试覆盖双 Token 认证、错误脱敏和不会回显秘密。

## 8. 风险与后续工作

- 主密钥仅通过受保护应用配置注入，不写入数据库；密钥轮换方案在实现阶段记录为独立技术决策。
- 公开 GitHub 检查可能受外部限流影响；适配器需区分永久不可访问和临时失败。
- 主机指纹/证书由管理员在环境配置时提供；首次自动信任会削弱安全性，因此不支持。
- `GITHUB_TOKEN` 虽可安全保存，但第一版不参与仓库访问；私有仓库需新规格后才能启用。
