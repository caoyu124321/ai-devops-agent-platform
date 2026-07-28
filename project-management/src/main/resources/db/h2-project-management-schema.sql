CREATE TABLE IF NOT EXISTS pm_projects (
    id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL, name VARCHAR(128) NOT NULL, description VARCHAR(500),
    current_version_no INT NOT NULL, created_by CHAR(36) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (tenant_id, name), UNIQUE (id, tenant_id)
);
CREATE TABLE IF NOT EXISTS pm_project_versions (
    id CHAR(36) PRIMARY KEY, project_id CHAR(36) NOT NULL, version_no INT NOT NULL, name VARCHAR(128) NOT NULL,
    description VARCHAR(500), created_by CHAR(36) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (project_id, version_no)
);
CREATE TABLE IF NOT EXISTS pm_repositories (
    id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL, project_id CHAR(36) NOT NULL, canonical_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL, current_version_no INT NOT NULL, connection_status VARCHAR(16) NOT NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE, last_error_code VARCHAR(64), created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (project_id, canonical_url)
);
CREATE TABLE IF NOT EXISTS pm_repository_versions (
    id CHAR(36) PRIMARY KEY, repository_id CHAR(36) NOT NULL, version_no INT NOT NULL, canonical_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL, created_by CHAR(36) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (repository_id, version_no)
);
CREATE TABLE IF NOT EXISTS pm_credentials (
    id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL, name VARCHAR(128) NOT NULL, credential_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL, current_version_no INT NOT NULL, created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (tenant_id, name), UNIQUE (id, tenant_id)
);
CREATE TABLE IF NOT EXISTS pm_credential_versions (
    id CHAR(36) PRIMARY KEY, credential_id CHAR(36) NOT NULL, version_no INT NOT NULL, encrypted_payload BLOB NOT NULL,
    encryption_key_id VARCHAR(128) NOT NULL, encryption_algorithm VARCHAR(64) NOT NULL, created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (credential_id, version_no)
);
CREATE TABLE IF NOT EXISTS pm_credential_project_grants (
    id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL, credential_id CHAR(36) NOT NULL, project_id CHAR(36) NOT NULL,
    granted_by CHAR(36) NOT NULL, granted_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (credential_id, project_id)
);
CREATE TABLE IF NOT EXISTS pm_environments (
    id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL, project_id CHAR(36) NOT NULL, name VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL, environment_level VARCHAR(16) NOT NULL, enabled BOOLEAN NOT NULL,
    connection_status VARCHAR(16) NOT NULL, last_checked_at TIMESTAMP WITH TIME ZONE, last_error_code VARCHAR(64),
    current_version_no INT NOT NULL, created_by CHAR(36) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (project_id, name)
);
CREATE TABLE IF NOT EXISTS pm_environment_versions (
    id CHAR(36) PRIMARY KEY, environment_id CHAR(36) NOT NULL, version_no INT NOT NULL, target_type VARCHAR(32) NOT NULL,
    environment_level VARCHAR(16) NOT NULL, credential_id CHAR(36) NOT NULL, created_by CHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE (environment_id, version_no)
);
CREATE TABLE IF NOT EXISTS pm_kubernetes_environment_configs (
    environment_version_id CHAR(36) PRIMARY KEY, api_server_url VARCHAR(512) NOT NULL, context_name VARCHAR(255),
    default_namespace VARCHAR(253) NOT NULL
);
CREATE TABLE IF NOT EXISTS pm_kubernetes_allowed_namespaces (
    id CHAR(36) PRIMARY KEY, environment_version_id CHAR(36) NOT NULL, namespace VARCHAR(253) NOT NULL,
    UNIQUE (environment_version_id, namespace)
);
CREATE TABLE IF NOT EXISTS pm_linux_host_configs (
    environment_version_id CHAR(36) PRIMARY KEY, host VARCHAR(255) NOT NULL, port INT NOT NULL,
    host_key_fingerprint VARCHAR(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS pm_windows_host_configs (
    environment_version_id CHAR(36) PRIMARY KEY, endpoint_url VARCHAR(512) NOT NULL,
    certificate_fingerprint VARCHAR(255) NOT NULL
);
