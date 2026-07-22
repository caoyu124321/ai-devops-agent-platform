# OAuth/OIDC 技术调研与决策依据

## 已采纳决策

| 主题 | 结论 | 原因 |
| --- | --- | --- |
| Grant 类型 | 授权码 + PKCE S256 | 公共 Agent 不能安全保存共享密钥，PKCE 降低授权码截获风险。 |
| Access Token | 随机不透明，15 分钟 | 与现有 IAM 的哈希存储和即时撤销模式一致；MCP 不需要自行解析身份。 |
| Refresh Token | 30 天绝对有效、7 天闲置、轮换与重用撤销 | 在用户体验和公网公共客户端风险之间取得 MVP 平衡。 |
| 客户端接入 | 动态注册 + PENDING/ACTIVE/SUSPENDED，未知客户端默认 ACTIVE | 支持多 Agent 自助接入；以精确回调校验、PKCE、登录同意、限流和显式停用控制风险。 |
| 本地验证 | URL 型 HTTP MCP + OAuth | 以相同协议替代 stdio 与 Windows 本地凭据依赖。 |

## 标准约束

- OAuth 安全最佳实践要求公共客户端的 Refresh Token 使用发送方约束或 Refresh Token 轮换；本期选择轮换与重用检测。
- PKCE 用于防止公共客户端授权码被截获后兑换。
- 原生应用回调地址需要严格校验；本地开发只放宽到受控的回环地址。

来源：[RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)、[RFC 7636](https://www.rfc-editor.org/info/rfc7636/)、[RFC 8252](https://www.rfc-editor.org/info/rfc8252/)。

## 已知后续风险

- 不使用 DPoP/mTLS 时，Refresh Token 轮换只能在重用发生后发现泄露，不能阻止首次滥用；MVP 通过短 Access Token、7 天闲置和整链撤销降低影响。
- 对外开放模式不要求未知客户端经过人工审批；仍需以受控配置或后续管理入口暂停、停用具体客户端，并保留注册限流。
- 生产域名、TLS 证书、反向代理与签名私钥的 Secret/KMS 来源属于部署前置条件，实施前必须由部署环境提供。
