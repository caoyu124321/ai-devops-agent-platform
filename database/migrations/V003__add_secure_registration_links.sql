CREATE TABLE iam_registration_links (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '注册链接 ID（UUID）',
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '注册链接持有者令牌的不可逆哈希',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING、COMPLETED、EXPIRED、CANCELLED',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间（UTC）',
    completed_at DATETIME(3) NULL COMMENT '完成时间（UTC）',
    registered_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '通过此链接创建的用户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_registration_links_token_hash (token_hash),
    KEY idx_iam_registration_links_status_expires (status, expires_at),
    CONSTRAINT fk_iam_registration_links_user
        FOREIGN KEY (registered_user_id) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_registration_links_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_iam_registration_links_expiry
        CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='本机安全注册链接；不保存密码或令牌原文';
