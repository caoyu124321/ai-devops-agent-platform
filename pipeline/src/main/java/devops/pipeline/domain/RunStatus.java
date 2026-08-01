package devops.pipeline.domain;

/** 运行终态不可逆，调度器只允许从 QUEUED 或 RUNNING 推进状态。 */
public enum RunStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELED, TIMED_OUT }
