package devops.projectmanagement.domain;

/** 外部目标最近一次只读连通性结果；其含义不等同于资源是否已启用。 */
public enum ConnectionStatus {
    HEALTHY,
    UNAVAILABLE
}
