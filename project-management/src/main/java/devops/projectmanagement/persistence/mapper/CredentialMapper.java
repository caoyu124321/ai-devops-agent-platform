package devops.projectmanagement.persistence.mapper;

import devops.projectmanagement.dao.CredentialDao.CredentialRow;
import devops.projectmanagement.dao.CredentialDao.CredentialVersionRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 凭据映射只返回元数据和密文，任何查询都不解密或投影秘密字段。 */
@Mapper
public interface CredentialMapper {
    @Insert("insert into pm_credentials(id,tenant_id,name,credential_type,status,current_version_no,created_by,created_at,updated_at) "
            + "values(#{id},#{tenantId},#{name},#{type},'ACTIVE',1,#{createdBy},#{now},#{now})")
    int create(@Param("id") String id, @Param("tenantId") String tenantId, @Param("name") String name,
               @Param("type") String type, @Param("createdBy") String createdBy, @Param("now") Instant now);

    @Insert("insert into pm_credential_versions(id,credential_id,version_no,encrypted_payload,encryption_key_id,encryption_algorithm,created_by,created_at) "
            + "values(#{id},#{credentialId},#{versionNo},#{payload},#{keyId},#{algorithm},#{createdBy},#{now})")
    int createVersion(@Param("id") String id, @Param("credentialId") String credentialId, @Param("versionNo") int versionNo,
                      @Param("payload") byte[] payload, @Param("keyId") String keyId, @Param("algorithm") String algorithm,
                      @Param("createdBy") String createdBy, @Param("now") Instant now);

    @Select("select id,tenant_id as tenantId,name,credential_type as credentialType,status,current_version_no as currentVersionNo,"
            + "created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_credentials where id=#{credentialId}")
    CredentialRow findById(@Param("credentialId") String credentialId);

    @Select("select id,credential_id as credentialId,version_no as versionNo,encrypted_payload as encryptedPayload,"
            + "encryption_key_id as encryptionKeyId,encryption_algorithm as encryptionAlgorithm,created_by as createdBy,created_at as createdAt "
            + "from pm_credential_versions where credential_id=#{credentialId} and version_no=#{versionNo}")
    CredentialVersionRow findVersion(@Param("credentialId") String credentialId, @Param("versionNo") int versionNo);

    @Select("select id,tenant_id as tenantId,name,credential_type as credentialType,status,current_version_no as currentVersionNo,"
            + "created_by as createdBy,created_at as createdAt,updated_at as updatedAt from pm_credentials where tenant_id=#{tenantId} order by created_at desc")
    List<CredentialRow> listByTenant(@Param("tenantId") String tenantId);

    @Select("select c.id,c.tenant_id as tenantId,c.name,c.credential_type as credentialType,c.status,c.current_version_no as currentVersionNo,"
            + "c.created_by as createdBy,c.created_at as createdAt,c.updated_at as updatedAt from pm_credentials c "
            + "join pm_credential_project_grants g on g.credential_id=c.id where g.project_id=#{projectId} and c.status='ACTIVE' order by c.name")
    List<CredentialRow> listGrantedByProject(@Param("projectId") String projectId);

    @Update("update pm_credentials set name=#{name},current_version_no=current_version_no+1,updated_at=#{now} "
            + "where id=#{credentialId} and current_version_no=#{expectedVersion}")
    int rename(@Param("credentialId") String credentialId, @Param("expectedVersion") int expectedVersion,
               @Param("name") String name, @Param("now") Instant now);

    @Update("update pm_credentials set current_version_no=current_version_no+1,updated_at=#{now} "
            + "where id=#{credentialId} and current_version_no=#{expectedVersion} and status='ACTIVE'")
    int rotate(@Param("credentialId") String credentialId, @Param("expectedVersion") int expectedVersion, @Param("now") Instant now);

    @Update("update pm_credentials set status='DISABLED',current_version_no=current_version_no+1,updated_at=#{now} "
            + "where id=#{credentialId} and current_version_no=#{expectedVersion} and status='ACTIVE'")
    int disable(@Param("credentialId") String credentialId, @Param("expectedVersion") int expectedVersion, @Param("now") Instant now);

    @Insert("insert into pm_credential_project_grants(id,tenant_id,credential_id,project_id,granted_by,granted_at) "
            + "values(#{id},#{tenantId},#{credentialId},#{projectId},#{grantedBy},#{now})")
    int grant(@Param("id") String id, @Param("tenantId") String tenantId, @Param("credentialId") String credentialId,
              @Param("projectId") String projectId, @Param("grantedBy") String grantedBy, @Param("now") Instant now);

    @Delete("delete from pm_credential_project_grants where credential_id=#{credentialId} and project_id=#{projectId}")
    int revokeGrant(@Param("credentialId") String credentialId, @Param("projectId") String projectId);

    @Select("select count(*) from pm_credential_project_grants where credential_id=#{credentialId} and project_id=#{projectId}")
    int countGrant(@Param("credentialId") String credentialId, @Param("projectId") String projectId);
}
