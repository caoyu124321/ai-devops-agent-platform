CREATE TABLE iam_login_links (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录链接 ID（UUID）',
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录链接持有者令牌的不可逆哈希',
    session_token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MCP 预生成平台会话令牌的不可逆哈希',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING、COMPLETED、EXPIRED',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    expires_at DATETIME(3) NOT NULL COMMENT '链接过期时间（UTC）',
    completed_at DATETIME(3) NULL COMMENT '完成时间（UTC）',
    session_expires_at DATETIME(3) NULL COMMENT '平台会话过期时间（UTC）',
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '完成登录的用户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_login_links_token_hash (token_hash),
    KEY idx_iam_login_links_status_expires (status, expires_at),
    CONSTRAINT fk_iam_login_links_user
        FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_login_links_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT chk_iam_login_links_expiry
        CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='本机安全登录链接；不保存密码或平台会话令牌原文';
