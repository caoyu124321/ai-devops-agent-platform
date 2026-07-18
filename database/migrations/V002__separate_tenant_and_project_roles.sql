-- 将租户成员身份与项目角色分离。执行前应确认不存在旧的 PROJECT_ADMIN、DEVELOPER、OBSERVER 成员记录。
ALTER TABLE iam_tenant_members DROP CHECK chk_iam_tenant_members_role;
ALTER TABLE iam_tenant_members
    ADD CONSTRAINT chk_iam_tenant_members_role CHECK (role_code IN ('TENANT_ADMIN', 'MEMBER'));

ALTER TABLE iam_invitations DROP CHECK chk_iam_invitations_role;
ALTER TABLE iam_invitations
    ADD CONSTRAINT chk_iam_invitations_role CHECK (role_code IN ('TENANT_ADMIN', 'MEMBER'));
