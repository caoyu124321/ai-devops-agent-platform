# Codex URL 型 OAuth 本机登录计划

## 设计

项目级 Codex 配置将 `ai_devops` 指向本机 HTTP MCP URL。Codex 通过 OAuth 认证挑战打开浏览器注册、登录和同意页面；服务端和项目文件均不保存 Codex 的 OAuth Token。

应用在默认 H2 数据源时加载 H2 专用 IAM/OAuth 表结构；外部 MySQL 数据库不运行启动建表逻辑，继续执行版本化迁移。

## 决策记录

- 只使用 URL 型 Streamable HTTP + OAuth。原因是 OAuth Token 必须由 Codex 客户端的安全存储管理，服务端不能安全地写入 Windows 凭据管理器。
- 默认 H2 使用独立、幂等的开发表结构；生产 MySQL 继续使用数据库迁移，避免运行时修改外部数据库。

## 验证

1. 检查项目配置只声明 URL 型 MCP 与 OAuth。
2. 检查默认 H2 启动后可创建 OAuth 客户端、注册链接和浏览器授权记录。
3. 执行 OAuth/MCP 单元与集成测试；在重启 Codex 后，以 `register_ai_devops`、`login_ai_devops` 和 `get_ai_devops_login_status` 完成手工验收。
