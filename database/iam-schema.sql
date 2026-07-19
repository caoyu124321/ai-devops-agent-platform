-- IAM 初始数据库结构
-- 适用范围：MySQL 8.0.16+，在目标数据库已创建并被选中后执行。
-- 说明：所有时间均由应用以 UTC 写入；UUID 由应用生成；本脚本不创建任何业务模块表。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

CREATE TABLE iam_users (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID（UUID）',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    email VARCHAR(254) NOT NULL COMMENT '登录邮箱',
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可逆密码哈希',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_users_username (username),
    UNIQUE KEY uk_iam_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='平台用户身份';

CREATE TABLE iam_sessions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话 ID（UUID）',
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属用户 ID',
    token_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '随机不透明 Token 的不可逆哈希',
    issued_at DATETIME(3) NOT NULL COMMENT '签发时间（UTC）',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间（UTC）',
    revoked_at DATETIME(3) NULL COMMENT '撤销时间（UTC）',
    revocation_reason VARCHAR(32) NULL COMMENT '撤销原因，如 LOGOUT、PASSWORD_CHANGED',
    client_summary VARCHAR(255) NULL COMMENT '脱敏后的客户端摘要',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_sessions_token_hash (token_hash),
    KEY idx_iam_sessions_user_revoked_expires (user_id, revoked_at, expires_at),
    CONSTRAINT fk_iam_sessions_user
        FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_sessions_expiry
        CHECK (expires_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多设备登录会话；不保存 Token 原文';

CREATE TABLE iam_login_locks (
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    failed_count INT NOT NULL COMMENT '连续登录失败次数',
    last_failed_at DATETIME(3) NULL COMMENT '最近失败时间（UTC）',
    locked_until DATETIME(3) NULL COMMENT '锁定截止时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_iam_login_locks_user
        FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_login_locks_failed_count
        CHECK (failed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='已识别用户的连续登录失败与锁定状态';

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

CREATE TABLE iam_tenants (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户 ID（UUID）',
    name VARCHAR(128) NOT NULL COMMENT '租户名称，允许重复',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建用户 ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_iam_tenants_created_by (created_by),
    CONSTRAINT fk_iam_tenants_created_by
        FOREIGN KEY (created_by) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_tenants_name_length
        CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='租户；名称不要求唯一';

CREATE TABLE iam_tenant_members (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成员关系 ID（UUID）',
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属租户 ID',
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成员用户 ID',
    role_code VARCHAR(32) NOT NULL COMMENT '内置角色编码',
    joined_at DATETIME(3) NOT NULL COMMENT '加入时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_tenant_members_tenant_user (tenant_id, user_id),
    UNIQUE KEY uk_iam_tenant_members_id_tenant (id, tenant_id),
    KEY idx_iam_tenant_members_tenant_role (tenant_id, role_code),
    KEY idx_iam_tenant_members_user (user_id),
    CONSTRAINT fk_iam_tenant_members_tenant
        FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id),
    CONSTRAINT fk_iam_tenant_members_user
        FOREIGN KEY (user_id) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_tenant_members_role
        CHECK (role_code IN ('TENANT_ADMIN', 'MEMBER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='租户成员关系与内置角色';

CREATE TABLE iam_project_role_bindings (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '项目角色绑定 ID',
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户 ID',
    member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户成员关系 ID',
    project_id VARCHAR(64) NOT NULL COMMENT '外部项目标识，IAM 不建立项目外键',
    role_code VARCHAR(32) NOT NULL COMMENT '固定项目角色',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分配人用户 ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_project_role_bindings_member_project (member_id, project_id),
    KEY idx_iam_project_role_bindings_tenant_project (tenant_id, project_id),
    CONSTRAINT fk_iam_project_role_bindings_member_tenant
        FOREIGN KEY (member_id, tenant_id) REFERENCES iam_tenant_members (id, tenant_id),
    CONSTRAINT fk_iam_project_role_bindings_created_by
        FOREIGN KEY (created_by) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_project_role_bindings_role
        CHECK (role_code IN ('PROJECT_ADMIN', 'DEVELOPER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目范围内的固定角色绑定；不依赖项目业务表';

CREATE TABLE iam_invitations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请 ID（UUID）',
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标租户 ID',
    invited_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受邀的已注册用户 ID',
    role_code VARCHAR(32) NOT NULL COMMENT '接受邀请后的初始内置角色',
    invited_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请发起人 ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间（UTC）',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING、ACCEPTED、REJECTED、REVOKED',
    resolved_at DATETIME(3) NULL COMMENT '处理时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_iam_invitations_tenant_user_status_expiry
        (tenant_id, invited_user_id, status, expires_at),
    KEY idx_iam_invitations_invited_user_status (invited_user_id, status),
    KEY idx_iam_invitations_invited_by (invited_by),
    CONSTRAINT fk_iam_invitations_tenant
        FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id),
    CONSTRAINT fk_iam_invitations_invited_user
        FOREIGN KEY (invited_user_id) REFERENCES iam_users (id),
    CONSTRAINT fk_iam_invitations_invited_by
        FOREIGN KEY (invited_by) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_invitations_role
        CHECK (role_code IN ('TENANT_ADMIN', 'MEMBER')),
    CONSTRAINT chk_iam_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED')),
    CONSTRAINT chk_iam_invitations_expiry
        CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='仅面向已注册用户的租户邀请';

CREATE TABLE iam_authorization_grants (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权项 ID（UUID）',
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户边界',
    member_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被授权成员关系 ID',
    resource_type VARCHAR(32) NOT NULL COMMENT '抽象资源类型，如 PROJECT、ENVIRONMENT',
    resource_id VARCHAR(64) NOT NULL COMMENT '业务资源标识',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码，如 environment.deploy',
    environment_level VARCHAR(16) NULL COMMENT '环境等级：TEST、STAGING、PROD；不适用时为空',
    environment_level_key VARCHAR(16)
        GENERATED ALWAYS AS (COALESCE(environment_level, '')) STORED
        COMMENT '环境等级唯一性比较键；将 NULL 规范化为空字符串',
    effect VARCHAR(8) NOT NULL COMMENT 'MVP 仅允许 ALLOW',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权人用户 ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_authorization_grants_rule
        (member_id, resource_type, resource_id, action_code, environment_level_key, effect),
    KEY idx_iam_authorization_grants_decision
        (tenant_id, member_id, resource_type, resource_id, action_code),
    KEY idx_iam_authorization_grants_created_by (created_by),
    CONSTRAINT fk_iam_authorization_grants_tenant
        FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id),
    CONSTRAINT fk_iam_authorization_grants_member_tenant
        FOREIGN KEY (member_id, tenant_id) REFERENCES iam_tenant_members (id, tenant_id),
    CONSTRAINT fk_iam_authorization_grants_created_by
        FOREIGN KEY (created_by) REFERENCES iam_users (id),
    CONSTRAINT chk_iam_authorization_grants_environment_level
        CHECK (environment_level IS NULL OR environment_level IN ('TEST', 'STAGING', 'PROD')),
    CONSTRAINT chk_iam_authorization_grants_effect
        CHECK (effect = 'ALLOW')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目、环境等抽象范围的附加授权项';

-- 业务事务约束（最后管理员保护、仅一条未过期待处理邀请等）依赖当前状态与时间，
-- 必须由应用服务在同一事务内使用行锁校验，不能仅依靠静态 CHECK 约束表达。
