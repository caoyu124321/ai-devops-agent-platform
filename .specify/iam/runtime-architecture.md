# 运行时启动边界

平台只保留一个 Spring Boot 启动入口：`devops.AiDevopsAgentPlatformApplication`。

- `application` 是可执行 Spring Boot 应用，并依赖 `iam` 模块；根工程仅负责 Maven 聚合。
- `iam` 是普通类库模块，只提供组件、REST 接口、MyBatis 映射和公开契约，不包含 `main` 方法、`@SpringBootApplication` 或 Spring Boot 打包插件。
- 主应用位于 `devops` 包，组件扫描天然覆盖 `devops.iam`，因此 IAM 能力随主应用一起装配。
- 数据源与 MyBatis 配置归入根工程的 `application.yaml`，避免多个模块存在同名启动配置造成覆盖顺序不确定。
