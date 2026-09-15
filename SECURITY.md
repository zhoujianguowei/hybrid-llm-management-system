# Security Policy / 安全说明

## Reporting a Vulnerability / 漏洞披露

如果发现安全漏洞，请**不要**创建公开的 GitHub Issue，发邮件至 `zhoujianguowei@gmail.com`（可附 PGP 或加密附件）。我们会在确认后的合理期限内修复并致谢。

If you discover a security vulnerability, please **do not** open a public GitHub issue. Email `zhoujianguowei@gmail.com`. We will fix confirmed issues within a reasonable period and credit reporters if desired.

## Deployment Hardening / 部署安全建议

本系统面向内网/私有化场景，部署到任何非隔离环境前请务必：

1. **立即修改默认账号**：首次登录后立即修改默认的 `admin` / `admin`，使用高强度密码。
2. **关闭 API 文档**：文档站点默认开启（`swagger.enable: true`、`springdoc.swagger-ui.enabled: true`）。生产环境请设置为 `false`。
3. **不要直接暴露公网**：建议通过反向代理（Nginx/Caddy）访问并启用 HTTPS/TLS，同时在代理层限制来源。系统自身不提供 HTTPS；登录密码为明文传输，公网场景必须依赖 TLS 终止。
4. **收紧文件目录权限**：为上传/下载目录配置独立的低权限系统用户；操作系统层目录权限与系统内建的路径权限体系共同构成边界。
5. **限制注册与角色**：如无自助注册需求，关闭访客注册，按需收紧各路径最小角色（参见权限体系）。
6. **定期备份数据目录**：用户、权限、任务等状态以文件形式持久化在数据目录，请将其纳入备份与监控。

## Known Dependence Baseline / 已知依赖基线

为保持对 JDK 8 的兼容，当前版本锁定在以下依赖系列（已知情况如下，升级计划见下）：

| 依赖 | 当前版本 | 说明 |
| :--- | :--- | :--- |
| Spring Boot | 2.1.8 | 上游已停止公开维护（EOL），建议部署环境做好网络隔离以控制风险 |
| Fastjson | 1.2.83 | 已修复 CVE-2022-25845；仍建议关注上游安全公告 |
| Springfox / springdoc | 3.0.0 / 1.5.2 | 文档站点默认开启，生产按上文建议关闭 |

**升级计划 / Upgrade plan**：项目正在评估升级到 Spring Boot 2.7 LTS 的方向（仍保持 Java 8 兼容），升级计划会在 CHANGELOG 中明确。欢迎社区在兼容性与稳定性方面贡献测试与补丁。

## Scope of the Open-Source Edition / 开源版范围

本仓库为开源版（Base）源码。完全版（Ultimate）为另一发布形态，不在本仓库的漏洞披露与修复承诺范围内；涉及完全版的问题请通过邮件联系我们。
