# plugin-s3

S3 协议对象存储插件，为 ikaros 提供对兼容 S3 协议服务（AWS S3、MinIO、阿里云 OSS、腾讯云 COS 等）的驱动附件挂载。

## 功能特性

- 基于 ikaros 附件驱动（CUSTOM 类型）挂载 S3 存储桶，自动同步桶内对象为附件
- 支持目录浏览：通过 ListObjectsV2 的公共前缀（CommonPrefixes）识别虚拟目录
- 支持读取、下载与流式播放：按需生成 AWS SigV4 预签名直链，不暴露 AccessKey/SecretKey
- 支持视频/音频 Range 分段流式传输
- 支持自定义访问域名（CDN/公开读存储桶场景，直链不携带签名）
- 不依赖 AWS SDK，SigV4 签名由插件自行实现

## 驱动配置

在 ikaros 控制台的「附件驱动」中新建驱动：

| 类型 | CUSTOM |
|------|--------|
| 名称 | S3 |
| 挂载名 | 任意显示名称，如 `my-minio` |
| 远程路径 | **S3 存储桶名称**（bucket） |
| 访问令牌 | Access Key ID |
| 刷新令牌 | Secret Access Key |
| 备注 | JSON 补充配置（可选），见下表 |

### 备注（comment）JSON 补充配置

```json
{
  "endpoint": "https://s3.amazonaws.com",
  "region": "us-east-1",
  "pathStyle": false,
  "domain": "https://static.example.com"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| endpoint | 否 | S3 服务端点。AWS 默认 `https://s3.amazonaws.com`；MinIO 等自建服务填 `http://127.0.0.1:9000` 等实际地址 |
| region | 否 | 区域，默认 `us-east-1`。MinIO 等自建服务通常任意值即可 |
| pathStyle | 否 | 是否路径风格访问。不填时自动推断：IP/localhost 端点自动使用路径风格（true），域名端点默认虚拟主机风格（false）。MinIO 一般填 `true` |
| domain | 否 | 自定义访问域名。配置后直链改为 `{domain}/{bucket}/{key}` 不带签名，适用于已公开读/CDN 的存储桶；此时无法强制下载响应头，请通过 CDN/桶策略配置 |

### 生效方式

- 配置修改后，在「附件驱动」列表对驱动执行启用/停用（或刷新）即可重新挂载
- 读取/下载直链为预签名 URL（有效期 1 小时），由插件按需生成，密钥不会下发到前端

## 核心版本适配

插件版本 1.x.x 需要 core 版本 >= 0.11.4。

## 开发

```bash
# 构建（含前端 console）
./gradlew build

# 仅编译后端
./gradlew compileJava -x buildFrontend -x pnpmInstall

# 运行测试
./gradlew test
```

## 变更日志

见 [CHANGELOG.MD](CHANGELOG.MD)。