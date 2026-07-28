package devops.projectmanagement.persistence.mapper;

import devops.projectmanagement.dao.EnvironmentDao.EnvironmentRow;
import devops.projectmanagement.dao.EnvironmentDao.KubernetesRow;
import devops.projectmanagement.dao.EnvironmentDao.LinuxRow;
import devops.projectmanagement.dao.EnvironmentDao.WindowsRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 环境 Mapper 只保存目标非敏感配置和凭据引用；凭据秘密始终留在凭据版本表。 */
@Mapper
public interface EnvironmentMapper {
    @Insert("insert into pm_environments(id,tenant_id,project_id,name,target_type,environment_level,enabled,connection_status,last_checked_at,last_error_code,current_version_no,created_by,created_at,updated_at) values(#{id},#{tenantId},#{projectId},#{name},#{targetType},#{level},#{enabled},#{status},#{checkedAt},#{errorCode},1,#{createdBy},#{now},#{now})")
    int create(@Param("id") String id, @Param("tenantId") String tenantId, @Param("projectId") String projectId, @Param("name") String name, @Param("targetType") String targetType, @Param("level") String level, @Param("enabled") boolean enabled, @Param("status") String status, @Param("checkedAt") Instant checkedAt, @Param("errorCode") String errorCode, @Param("createdBy") String createdBy, @Param("now") Instant now);
    @Insert("insert into pm_environment_versions(id,environment_id,version_no,target_type,environment_level,credential_id,created_by,created_at) values(#{id},#{environmentId},#{versionNo},#{targetType},#{level},#{credentialId},#{createdBy},#{now})")
    int version(@Param("id") String id,@Param("environmentId") String environmentId,@Param("versionNo") int versionNo,@Param("targetType") String type,@Param("level") String level,@Param("credentialId") String credentialId,@Param("createdBy") String createdBy,@Param("now") Instant now);
    @Insert("insert into pm_kubernetes_environment_configs(environment_version_id,api_server_url,context_name,default_namespace) values(#{versionId},#{url},#{context},#{namespace})") int createKubernetesConfig(@Param("versionId") String versionId,@Param("url") String url,@Param("context") String context,@Param("namespace") String namespace);
    @Insert("insert into pm_kubernetes_allowed_namespaces(id,environment_version_id,namespace) values(#{id},#{versionId},#{namespace})") int namespace(@Param("id") String id,@Param("versionId") String versionId,@Param("namespace") String namespace);
    @Insert("insert into pm_linux_host_configs(environment_version_id,host,port,host_key_fingerprint) values(#{versionId},#{host},#{port},#{fingerprint})") int linux(@Param("versionId") String versionId,@Param("host") String host,@Param("port") int port,@Param("fingerprint") String fingerprint);
    @Insert("insert into pm_windows_host_configs(environment_version_id,endpoint_url,certificate_fingerprint) values(#{versionId},#{url},#{fingerprint})") int windows(@Param("versionId") String versionId,@Param("url") String url,@Param("fingerprint") String fingerprint);
    @Select("select id,tenant_id as tenantId,project_id as projectId,name,target_type as targetType,environment_level as environmentLevel,enabled,connection_status as connectionStatus,last_checked_at as lastCheckedAt,last_error_code as lastErrorCode,current_version_no as currentVersionNo,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_environments where id=#{id}") EnvironmentRow find(@Param("id") String id);
    @Select("select id,tenant_id as tenantId,project_id as projectId,name,target_type as targetType,environment_level as environmentLevel,enabled,connection_status as connectionStatus,last_checked_at as lastCheckedAt,last_error_code as lastErrorCode,current_version_no as currentVersionNo,created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_environments where project_id=#{projectId} order by created_at desc") List<EnvironmentRow> list(@Param("projectId") String projectId);
    @Select("select credential_id from pm_environment_versions where environment_id=#{environmentId} and version_no=#{versionNo}") String credential(@Param("environmentId") String environmentId,@Param("versionNo") int versionNo);
    @Select("select c.api_server_url as apiServerUrl,c.context_name as contextName,c.default_namespace as defaultNamespace from pm_kubernetes_environment_configs c join pm_environment_versions v on v.id=c.environment_version_id where v.environment_id=#{environmentId} and v.version_no=#{versionNo}") KubernetesRow kubernetes(@Param("environmentId") String environmentId,@Param("versionNo") int versionNo);
    @Select("select n.namespace from pm_kubernetes_allowed_namespaces n join pm_environment_versions v on v.id=n.environment_version_id where v.environment_id=#{environmentId} and v.version_no=#{versionNo} order by n.namespace") List<String> namespaces(@Param("environmentId") String environmentId,@Param("versionNo") int versionNo);
    @Select("select c.host,c.port,c.host_key_fingerprint as fingerprint from pm_linux_host_configs c join pm_environment_versions v on v.id=c.environment_version_id where v.environment_id=#{environmentId} and v.version_no=#{versionNo}") LinuxRow linuxRead(@Param("environmentId") String environmentId,@Param("versionNo") int versionNo);
    @Select("select c.endpoint_url as endpointUrl,c.certificate_fingerprint as fingerprint from pm_windows_host_configs c join pm_environment_versions v on v.id=c.environment_version_id where v.environment_id=#{environmentId} and v.version_no=#{versionNo}") WindowsRow windowsRead(@Param("environmentId") String environmentId,@Param("versionNo") int versionNo);
    @Update("update pm_environments set name=#{name},target_type=#{type},environment_level=#{level},enabled=#{enabled},connection_status=#{status},last_checked_at=#{checkedAt},last_error_code=#{errorCode},current_version_no=current_version_no+1,updated_at=#{now} where id=#{id} and current_version_no=#{version}") int update(@Param("id") String id,@Param("version") int version,@Param("name") String name,@Param("type") String type,@Param("level") String level,@Param("enabled") boolean enabled,@Param("status") String status,@Param("checkedAt") Instant checkedAt,@Param("errorCode") String errorCode,@Param("now") Instant now);
    @Update("update pm_environments set enabled=#{enabled},current_version_no=current_version_no+1,updated_at=#{now} where id=#{id} and current_version_no=#{version}") int enabled(@Param("id") String id,@Param("version") int version,@Param("enabled") boolean enabled,@Param("now") Instant now);
    @Update("update pm_environments set connection_status=#{status},last_checked_at=#{checkedAt},last_error_code=#{errorCode},updated_at=#{now} where id=#{id}") int health(@Param("id") String id,@Param("status") String status,@Param("checkedAt") Instant checkedAt,@Param("errorCode") String errorCode,@Param("now") Instant now);
    @Delete("delete from pm_environments where id=#{id}") int delete(@Param("id") String id);
}
