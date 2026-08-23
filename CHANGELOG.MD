# CHANGELOG

更新日志文档，版本顺序从新到旧，最新版本在最前(上)面。

# 1.0.5

# 1.0.4

- 修正项目名拼写 `plguin-s3` 为 `plugin-s3`（settings.gradle、plugin.yaml homepage、S3Const.REPO_GITHUB_NAME、README、IMPLEMENTATION）

# 1.0.3

- 修复控制台插件页面 scoped 样式在库模式打包下失效的问题，改用带唯一前缀的普通样式
- 插件菜单分组由 content 调整为 tool

# 1.0.2

- 配置自定义访问域名时，预签名直链改用自定义域名的协议+主机作为签名 host 重新签名，确保签名与实际请求 host 一致（修复阿里云 OSS 通过自定义域名访问时报 SignatureDoesNotMatch 的问题）

# 1.0.1

- 修复自定义域名下流式直链丢失签名导致 403 的问题，服务端流式代理始终携带签名
- 所有附件（图片与非图片）统一返回预签名直链，移除自定义域名下不签名的公开直链逻辑

# 1.0.0

- 实现基于 S3 协议的附件驱动挂载（CUSTOM 类型驱动，驱动名 S3）
- 实现 ListObjectsV2 目录/对象列表浏览与同步
- 实现 AWS SigV4 预签名直链（读取、下载、流式传输）
- 实现视频/音频 Range 分段流式传输
- 支持自定义访问域名（CDN/公开读场景）
- 提供 S3 驱动配置说明与控制台页面
- 提供 SigV4 签名、XML 解析、配置解析单元测试