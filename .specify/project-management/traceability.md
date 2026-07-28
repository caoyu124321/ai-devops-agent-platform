# 项目管理模块验收追溯

## 自动化验证

| 验收范围 | 证据 |
| --- | --- |
| AS-01 项目创建、版本冲突、可见性过滤、创建者项目管理员绑定 | `ProjectServiceTest`；`AiDevopsAgentPlatformApplicationTests.localH2SupportsAuthenticatedProjectCreation` |
| AS-02 GitHub HTTPS 规范化、最大 20 个、永久失效删除、临时故障保留 | `RepositoryServiceTest` |
| AS-03 凭据类型、AES-GCM 加密、轮换、停用、项目授权、跨租户拒绝与不回显秘密 | `CredentialCryptoServiceTest`、`CredentialServiceTest`；H2 集成测试 |
| AS-04 Kubernetes 命名空间限制、凭据授权和失败时不可用 | `EnvironmentServiceTest`；`ReadOnlyEnvironmentConnectionValidatorTest`；H2 集成测试 |
| AS-05 SSH 主机密钥指纹、WinRM TLS 证书指纹和失败时不可用 | `ReadOnlyEnvironmentConnectionValidator`；`ReadOnlyEnvironmentConnectionValidatorTest` |
| AS-06 统一 IAM 授权、OAuth/会话主体解析、环境启停与连接状态分离 | `AuthFilterTest`、服务层测试、应用集成测试 |

## 当前验证命令

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\openjdk-26.0.1'
& 'C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' -pl application -am test
```

## 部署前检查

`database/migrations/V007__create_project_management.sql` 已于本机 `app_db` 成功执行，并通过只读 `information_schema` 查询确认创建全部 13 张 `pm_*` 表。自动化集成测试仍使用本地 H2 等价 Schema。
