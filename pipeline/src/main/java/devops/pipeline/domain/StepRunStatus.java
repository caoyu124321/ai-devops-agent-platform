package devops.pipeline.domain;

/** 通用步骤状态与插件内部状态分离，避免插件类型泄漏到编排器。 */
public enum StepRunStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELED, TIMED_OUT, SKIPPED }
