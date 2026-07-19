# Codex MCP 实施任务

**状态**：已批准，可实施  
**对应规格**：`spec.md`、`technical-design.md`、`api-contract.md`

| 编号 | 任务 | 对应需求 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| T-MCP-01 | 创建 `mcp` Maven 子模块与可执行打包配置 | FR-MCP-001 | 根工程构建通过，JAR 可启动 | 已完成 |
| T-MCP-02 | 实现平台 REST 客户端、响应模型和安全错误映射 | FR-MCP-004、005、006、007 | Mock HTTP 契约测试 | 已完成 |
| T-MCP-03 | 实现凭据仓库接口、内存实现和 Windows 凭据管理器实现 | FR-MCP-003、004 | 单元测试与 Windows 手工验证 | 已完成 |
| T-MCP-04 | 实现登录、当前用户、登出身份用例 | US-MCP-01、02、03 | 单元测试 | 已完成 |
| T-MCP-05 | 实现 MCP stdio 服务、输入提示和三个工具 | FR-MCP-001、002 | MCP 握手与工具契约测试 | 已完成（JAR 已验证可启动，待 Codex 实机握手） |
| T-MCP-06 | 增加安全启动脚本、Codex 配置和接入说明 | FR-MCP-001、003 | Codex 可发现工具 | 已完成（配置已生成，待重启 Codex 加载） |
| T-MCP-07 | 完成自动化测试、构建和本机联调 | 全部验收场景 | Maven 测试与人工验收 | 进行中（待 Codex 实机登录验收） |

| T-MCP-08 | 增加一次性安全注册链接 MCP 工具、REST 适配与测试 | US-MCP-01A、FR-MCP-008 | 单元测试、HTTP 契约测试、Codex 人工验收 | 已完成（待 Codex 实机验收） |
| T-MCP-09 | 将登录改为一次性本机浏览器链接并安全交接会话令牌 | US-MCP-01、FR-MCP-002 | 单元测试、HTTP 契约测试、Codex 人工验收 | 已完成（待 Codex 实机验收） |

| T-MCP-09 | 增加能力发现工具、Server instructions 与仓库级 Skill | US-MCP-04、FR-MCP-009、FR-MCP-010 | 单元测试、Skill 校验、Codex 人工验收 | 已完成（待 Codex 实机验收） |

## 验收顺序

1. 模块构建和 MCP 协议握手。
2. 登录成功且 Token 不进入日志或工具输出。
3. 当前用户工具复用凭据。
4. 401 自动清理凭据，403 不清理凭据。
5. 登出与重复登出。
6. Codex 本地 MCP 配置加载。
