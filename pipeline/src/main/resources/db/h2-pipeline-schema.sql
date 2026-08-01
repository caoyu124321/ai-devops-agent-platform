CREATE TABLE IF NOT EXISTS pl_pipelines (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, project_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL, description VARCHAR(500), enabled BOOLEAN NOT NULL, current_version_no INT NOT NULL,
    created_by VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_pl_pipelines_project_name UNIQUE(project_id, name)
);
CREATE TABLE IF NOT EXISTS pl_pipeline_versions (
    id VARCHAR(36) PRIMARY KEY, pipeline_id VARCHAR(36) NOT NULL, version_no INT NOT NULL, yaml_content CLOB NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL, repository_id VARCHAR(36) NOT NULL, repository_version_no INT NOT NULL,
    source_default_branch VARCHAR(255), created_by VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_pl_pipeline_versions_pipeline_version UNIQUE(pipeline_id, version_no)
);
CREATE TABLE IF NOT EXISTS pl_pipeline_steps (
    id VARCHAR(36) PRIMARY KEY, pipeline_version_id VARCHAR(36) NOT NULL, stage_name VARCHAR(128) NOT NULL,
    stage_sequence_no INT NOT NULL, step_id VARCHAR(128) NOT NULL, step_sequence_no INT NOT NULL,
    plugin_name VARCHAR(128) NOT NULL, plugin_version VARCHAR(64) NOT NULL, input_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_pl_pipeline_steps_version_step UNIQUE(pipeline_version_id, step_id),
    CONSTRAINT uk_pl_pipeline_steps_version_sequence UNIQUE(pipeline_version_id, step_sequence_no)
);
CREATE TABLE IF NOT EXISTS pl_runs (
    id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL, project_id VARCHAR(36) NOT NULL,
    pipeline_id VARCHAR(36) NOT NULL, pipeline_version_id VARCHAR(36) NOT NULL, repository_id VARCHAR(36) NOT NULL,
    repository_version_no INT NOT NULL, source_branch VARCHAR(255), source_commit VARCHAR(128), status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128), configuration_snapshot CLOB NOT NULL, failure_code VARCHAR(64), failure_message VARCHAR(500),
    requested_by VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL, started_at TIMESTAMP, finished_at TIMESTAMP,
    CONSTRAINT uk_pl_runs_project_idempotency UNIQUE(project_id, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pl_step_runs (
    id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, pipeline_step_id VARCHAR(36) NOT NULL, step_sequence_no INT NOT NULL,
    step_id VARCHAR(128) NOT NULL, plugin_name VARCHAR(128) NOT NULL, plugin_version VARCHAR(64) NOT NULL, input_json CLOB NOT NULL,
    status VARCHAR(16) NOT NULL, output_json CLOB, failure_code VARCHAR(64), failure_message VARCHAR(500), started_at TIMESTAMP, finished_at TIMESTAMP,
    CONSTRAINT uk_pl_step_runs_run_sequence UNIQUE(run_id, step_sequence_no)
);
CREATE TABLE IF NOT EXISTS pl_step_logs (
    id VARCHAR(36) PRIMARY KEY, step_run_id VARCHAR(36) NOT NULL, sequence_no INT NOT NULL, level VARCHAR(16) NOT NULL,
    message VARCHAR(4000) NOT NULL, created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_pl_step_logs_step_sequence UNIQUE(step_run_id, sequence_no)
);
