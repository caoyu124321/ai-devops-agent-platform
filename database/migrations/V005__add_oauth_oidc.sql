-- OAuth/OIDC 与远程 MCP 的持久化结构。
-- 所有凭据字段仅保存不可逆哈希，应用统一以 UTC 写入时间。

CREATE TABLE iam_oauth_clients (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公共客户端 ID',
    client_name VARCHAR(128) NOT NULL COMMENT '客户端展示名称',
    redirect_uris TEXT NOT NULL COMMENT '逐行存储的精确回调地址',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING、ACTIVE、SUSPENDED',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_iam_oauth_clients_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth 公共客户端；不保存 client_secret';

CREATE TABLE iam_oauth_grants (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    audience VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    absolute_expires_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revocation_reason VARCHAR(32) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_iam_oauth_grants_user (user_id, revoked_at),
    KEY idx_iam_oauth_grants_client (client_id, revoked_at),
    CONSTRAINT fk_iam_oauth_grants_user FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT fk_iam_oauth_grants_client FOREIGN KEY (client_id) REFERENCES iam_oauth_clients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与公共客户端的 OAuth 授权链';

CREATE TABLE iam_oauth_authorization_codes (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    grant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    code_challenge VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_oauth_authorization_codes_hash (code_hash),
    KEY idx_iam_oauth_codes_expiry (expires_at, consumed_at),
    CONSTRAINT fk_iam_oauth_codes_grant FOREIGN KEY (grant_id) REFERENCES iam_oauth_grants (id),
    CONSTRAINT fk_iam_oauth_codes_client FOREIGN KEY (client_id) REFERENCES iam_oauth_clients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次 OAuth 授权码；不保存原文';

CREATE TABLE iam_oauth_access_tokens (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    grant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    audience VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revocation_reason VARCHAR(32) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_oauth_access_tokens_hash (token_hash),
    KEY idx_iam_oauth_access_grant (grant_id, revoked_at, expires_at),
    CONSTRAINT fk_iam_oauth_access_grant FOREIGN KEY (grant_id) REFERENCES iam_oauth_grants (id),
    CONSTRAINT fk_iam_oauth_access_user FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT fk_iam_oauth_access_client FOREIGN KEY (client_id) REFERENCES iam_oauth_clients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth Access Token；不保存原文';

CREATE TABLE iam_oauth_refresh_tokens (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    grant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_token_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(16) NOT NULL COMMENT 'ACTIVE、ROTATED、REVOKED',
    issued_at DATETIME(3) NOT NULL,
    rotated_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_oauth_refresh_tokens_hash (token_hash),
    KEY idx_iam_oauth_refresh_grant (grant_id, status),
    CONSTRAINT fk_iam_oauth_refresh_grant FOREIGN KEY (grant_id) REFERENCES iam_oauth_grants (id),
    CONSTRAINT fk_iam_oauth_refresh_parent FOREIGN KEY (parent_token_id) REFERENCES iam_oauth_refresh_tokens (id),
    CONSTRAINT chk_iam_oauth_refresh_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth Refresh Token 轮换链；不保存原文';

CREATE TABLE iam_oauth_browser_sessions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_oauth_browser_sessions_hash (token_hash),
    KEY idx_iam_oauth_browser_user (user_id, revoked_at),
    CONSTRAINT fk_iam_oauth_browser_user FOREIGN KEY (user_id) REFERENCES iam_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth 授权页面浏览器会话；不保存 Cookie 原文';
