CREATE TABLE pm_projects (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    current_version_no INT NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_projects_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uq_pm_projects_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_pm_projects_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants(id),
    CONSTRAINT fk_pm_projects_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_projects_version CHECK (current_version_no >= 1)
);

CREATE TABLE pm_project_versions (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_project_versions UNIQUE (project_id, version_no),
    CONSTRAINT fk_pm_project_versions_project FOREIGN KEY (project_id) REFERENCES pm_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_project_versions_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_project_versions_number CHECK (version_no >= 1)
);

CREATE TABLE pm_repositories (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_id CHAR(36) NOT NULL,
    canonical_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL,
    current_version_no INT NOT NULL,
    connection_status VARCHAR(16) NOT NULL,
    last_checked_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_repositories_project_url UNIQUE (project_id, canonical_url),
    CONSTRAINT fk_pm_repositories_project_tenant FOREIGN KEY (project_id, tenant_id) REFERENCES pm_projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_repositories_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_repositories_version CHECK (current_version_no >= 1),
    CONSTRAINT chk_pm_repositories_status CHECK (connection_status IN ('HEALTHY', 'UNAVAILABLE'))
);

CREATE TABLE pm_repository_versions (
    id CHAR(36) PRIMARY KEY,
    repository_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    canonical_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_repository_versions UNIQUE (repository_id, version_no),
    CONSTRAINT fk_pm_repository_versions_repository FOREIGN KEY (repository_id) REFERENCES pm_repositories(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_repository_versions_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_repository_versions_number CHECK (version_no >= 1)
);

CREATE TABLE pm_credentials (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_version_no INT NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_credentials_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uq_pm_credentials_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_pm_credentials_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants(id),
    CONSTRAINT fk_pm_credentials_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_credentials_type CHECK (credential_type IN ('KUBECONFIG', 'SSH_PASSWORD', 'SSH_PRIVATE_KEY', 'WINRM_PASSWORD', 'GITHUB_TOKEN')),
    CONSTRAINT chk_pm_credentials_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_pm_credentials_version CHECK (current_version_no >= 1)
);

CREATE TABLE pm_credential_versions (
    id CHAR(36) PRIMARY KEY,
    credential_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    encrypted_payload MEDIUMBLOB NOT NULL,
    encryption_key_id VARCHAR(128) NOT NULL,
    encryption_algorithm VARCHAR(64) NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_credential_versions UNIQUE (credential_id, version_no),
    CONSTRAINT fk_pm_credential_versions_credential FOREIGN KEY (credential_id) REFERENCES pm_credentials(id),
    CONSTRAINT fk_pm_credential_versions_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_credential_versions_number CHECK (version_no >= 1)
);

CREATE TABLE pm_credential_project_grants (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    granted_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    granted_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_credential_project_grants UNIQUE (credential_id, project_id),
    CONSTRAINT fk_pm_credential_grants_credential_tenant FOREIGN KEY (credential_id, tenant_id) REFERENCES pm_credentials(id, tenant_id),
    CONSTRAINT fk_pm_credential_grants_project_tenant FOREIGN KEY (project_id, tenant_id) REFERENCES pm_projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_credential_grants_granter FOREIGN KEY (granted_by) REFERENCES iam_users(id)
);

CREATE TABLE pm_environments (
    id CHAR(36) PRIMARY KEY,
    tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    project_id CHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    environment_level VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    connection_status VARCHAR(16) NOT NULL,
    last_checked_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    current_version_no INT NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_environments_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_pm_environments_project_tenant FOREIGN KEY (project_id, tenant_id) REFERENCES pm_projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_environments_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_environments_target CHECK (target_type IN ('KUBERNETES', 'LINUX_HOST', 'WINDOWS_HOST')),
    CONSTRAINT chk_pm_environments_level CHECK (environment_level IN ('DEV', 'TEST', 'STAGING', 'PROD')),
    CONSTRAINT chk_pm_environments_status CHECK (connection_status IN ('UNKNOWN', 'HEALTHY', 'UNAVAILABLE')),
    CONSTRAINT chk_pm_environments_version CHECK (current_version_no >= 1)
);

CREATE TABLE pm_environment_versions (
    id CHAR(36) PRIMARY KEY,
    environment_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    environment_level VARCHAR(16) NOT NULL,
    credential_id CHAR(36) NOT NULL,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_pm_environment_versions UNIQUE (environment_id, version_no),
    CONSTRAINT fk_pm_environment_versions_environment FOREIGN KEY (environment_id) REFERENCES pm_environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_environment_versions_credential FOREIGN KEY (credential_id) REFERENCES pm_credentials(id),
    CONSTRAINT fk_pm_environment_versions_creator FOREIGN KEY (created_by) REFERENCES iam_users(id),
    CONSTRAINT chk_pm_environment_versions_target CHECK (target_type IN ('KUBERNETES', 'LINUX_HOST', 'WINDOWS_HOST')),
    CONSTRAINT chk_pm_environment_versions_level CHECK (environment_level IN ('DEV', 'TEST', 'STAGING', 'PROD')),
    CONSTRAINT chk_pm_environment_versions_number CHECK (version_no >= 1)
);

CREATE TABLE pm_kubernetes_environment_configs (
    environment_version_id CHAR(36) PRIMARY KEY,
    api_server_url VARCHAR(512) NOT NULL,
    context_name VARCHAR(255) NULL,
    default_namespace VARCHAR(253) NOT NULL,
    CONSTRAINT fk_pm_kubernetes_configs_version FOREIGN KEY (environment_version_id) REFERENCES pm_environment_versions(id) ON DELETE CASCADE
);

CREATE TABLE pm_kubernetes_allowed_namespaces (
    id CHAR(36) PRIMARY KEY,
    environment_version_id CHAR(36) NOT NULL,
    namespace VARCHAR(253) NOT NULL,
    CONSTRAINT uq_pm_kubernetes_namespaces UNIQUE (environment_version_id, namespace),
    CONSTRAINT fk_pm_kubernetes_namespaces_version FOREIGN KEY (environment_version_id) REFERENCES pm_environment_versions(id) ON DELETE CASCADE
);

CREATE TABLE pm_linux_host_configs (
    environment_version_id CHAR(36) PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    host_key_fingerprint VARCHAR(255) NOT NULL,
    CONSTRAINT fk_pm_linux_configs_version FOREIGN KEY (environment_version_id) REFERENCES pm_environment_versions(id) ON DELETE CASCADE,
    CONSTRAINT chk_pm_linux_configs_port CHECK (port BETWEEN 1 AND 65535)
);

CREATE TABLE pm_windows_host_configs (
    environment_version_id CHAR(36) PRIMARY KEY,
    endpoint_url VARCHAR(512) NOT NULL,
    certificate_fingerprint VARCHAR(255) NOT NULL,
    CONSTRAINT fk_pm_windows_configs_version FOREIGN KEY (environment_version_id) REFERENCES pm_environment_versions(id) ON DELETE CASCADE
);

CREATE INDEX idx_pm_projects_tenant_created ON pm_projects(tenant_id, created_at);
CREATE INDEX idx_pm_repositories_project_status ON pm_repositories(project_id, connection_status);
CREATE INDEX idx_pm_credentials_tenant_status ON pm_credentials(tenant_id, status);
CREATE INDEX idx_pm_credential_grants_project ON pm_credential_project_grants(project_id, credential_id);
CREATE INDEX idx_pm_environments_project_status ON pm_environments(project_id, enabled, connection_status);
CREATE INDEX idx_pm_environments_tenant_level ON pm_environments(tenant_id, environment_level);
