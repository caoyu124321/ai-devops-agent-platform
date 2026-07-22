-- 对外开放策略生效后，历史动态注册客户端不再因旧默认值 PENDING 被阻断。
-- 运行时的 client_id 显式覆盖仍可将指定客户端设为 PENDING 或 SUSPENDED。
UPDATE iam_oauth_clients
SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING';
