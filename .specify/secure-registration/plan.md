# 安全注册链接实施计划

## 设计决策

1. IAM 持久化注册链接状态和令牌哈希，避免进程重启后将有效链接变为不可恢复的状态。
2. MCP 通过受限的本机 REST 地址请求注册链接；浏览器密码提交只到 IAM。
3. 浏览器页面使用同一持有者令牌完成注册和查看结果；链接令牌绝不写入服务器日志。
4. 链接完成使用数据库行锁与状态更新，保证并发提交最多成功一次。

## 数据模型

`iam_registration_links`：ID、令牌哈希、状态、创建时间、过期时间、完成时间、已注册用户 ID。令牌哈希唯一；已注册用户仅在完成后存在。

## REST 契约

- `POST /api/v1/auth/registration-links`：创建链接，返回 ID、URL、过期时间。
- `GET /api/v1/auth/registration-links/{id}`：持有者令牌校验后的状态查询。
- `GET /api/v1/auth/registration-links/{id}/form`：返回本机注册 HTML 表单。
- `POST /api/v1/auth/registration-links/{id}/complete`：提交表单并完成注册链接。

所有持有者接口均使用查询参数或表单中的令牌；失败响应不区分不存在、过期、取消与令牌不匹配。

## 实施任务

1. 添加注册链接迁移、DAO 与状态模型。
2. 实现 IAM 链接创建、页面渲染、单次完成与状态查询服务及 REST 控制器。
3. 将 MCP 注册工具替换为“创建注册链接”和“查询注册链接状态”工具。
4. 增加 IAM、HTTP 客户端和 MCP 工具测试；执行聚合构建。
