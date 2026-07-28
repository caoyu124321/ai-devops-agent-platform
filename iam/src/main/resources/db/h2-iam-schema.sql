CREATE TABLE IF NOT EXISTS iam_users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_sessions (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(32),
    client_summary VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS iam_login_locks (
    user_id CHAR(36) PRIMARY KEY,
    failed_count INT NOT NULL,
    last_failed_at TIMESTAMP WITH TIME ZONE,
    locked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_registration_links (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    registered_user_id CHAR(36)
);

CREATE TABLE IF NOT EXISTS iam_login_links (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    session_token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    session_expires_at TIMESTAMP WITH TIME ZONE,
    user_id CHAR(36)
);

CREATE TABLE IF NOT EXISTS iam_tenants (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_tenant_members (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS iam_invitations (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    invited_user_id CHAR(36) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    invited_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS iam_authorization_grants (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    member_id CHAR(36) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    environment_level VARCHAR(16),
    effect VARCHAR(8) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_project_role_bindings (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    member_id CHAR(36) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (member_id, project_id)
);

CREATE TABLE IF NOT EXISTS iam_oauth_clients (
    id CHAR(36) PRIMARY KEY,
    client_name VARCHAR(128) NOT NULL,
    redirect_uris CLOB NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_oauth_grants (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    client_id CHAR(36) NOT NULL,
    audience VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_oauth_authorization_codes (
    id CHAR(36) PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL UNIQUE,
    grant_id CHAR(36) NOT NULL,
    client_id CHAR(36) NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    code_challenge VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS iam_oauth_access_tokens (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    grant_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    client_id CHAR(36) NOT NULL,
    audience VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS iam_oauth_refresh_tokens (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    grant_id CHAR(36) NOT NULL,
    parent_token_id CHAR(36),
    status VARCHAR(16) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS iam_oauth_browser_sessions (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id CHAR(36) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
