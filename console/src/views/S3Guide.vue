<script setup lang="ts">
// S3 对象存储插件配置引导页
const configOptions = [
  { field: '类型', value: 'CUSTOM', desc: '附件驱动类型' },
  { field: '名称', value: 'S3', desc: '插件提供的驱动名，固定为 S3' },
  { field: '挂载名', value: 'my-minio', desc: '任意显示名称' },
  { field: '远程路径', value: 'my-bucket', desc: 'S3 存储桶名称（bucket）' },
  { field: '访问令牌', value: 'AKIA...', desc: 'Access Key ID' },
  { field: '刷新令牌', value: '********', desc: 'Secret Access Key' },
  { field: '备注', value: 'JSON', desc: '补充配置，见下方 JSON 示例' },
];
const commentJson = `{
  "endpoint": "http://127.0.0.1:9000",
  "region": "us-east-1",
  "pathStyle": true,
  "domain": "https://static.example.com"
}`;
const commentOptions = [
  { field: 'endpoint', required: '否', desc: 'S3 服务端点。AWS 默认 https://s3.amazonaws.com；MinIO 等自建服务填实际地址' },
  { field: 'region', required: '否', desc: '区域，默认 us-east-1；MinIO 等自建服务通常任意值即可' },
  { field: 'pathStyle', required: '否', desc: '是否路径风格访问。不填时自动推断：IP/localhost 端点自动为 true，域名端点默认 false（虚拟主机风格）' },
  { field: 'domain', required: '否', desc: '自定义访问域名（CDN/公开读）。配置后直链为 {domain}/{bucket}/{key}，不携带签名' },
];
</script>

<template>
  <div class="s3-guide-container">
    <h2 class="s3-guide-title">S3 对象存储插件</h2>
    <p class="s3-guide-desc">
      本插件将兼容 S3 协议的对象存储（AWS S3、MinIO、阿里云 OSS 等）挂载为 ikaros 附件驱动，
      支持目录浏览、附件读取、下载与流式播放。
    </p>

    <h3>使用步骤</h3>
    <ol class="s3-guide-steps">
      <li>在「附件驱动」页面新建驱动，按下方字段映射填写；</li>
      <li>驱动创建成功后，在附件页面打开对应挂载目录，ikaros 会自动同步 S3 对象为附件；</li>
      <li>播放/下载时插件按需生成 AWS SigV4 预签名直链，密钥不会下发到前端。</li>
    </ol>

    <h3>驱动字段映射</h3>
    <table class="s3-guide-table">
      <thead>
        <tr><th>字段</th><th>填写示例</th><th>说明</th></tr>
      </thead>
      <tbody>
        <tr v-for="opt in configOptions" :key="opt.field">
          <td>{{ opt.field }}</td>
          <td><code>{{ opt.value }}</code></td>
          <td>{{ opt.desc }}</td>
        </tr>
      </tbody>
    </table>

    <h3>备注（comment）JSON 补充配置</h3>
    <pre class="s3-guide-pre"><code>{{ commentJson }}</code></pre>
    <table class="s3-guide-table">
      <thead>
        <tr><th>字段</th><th>必填</th><th>说明</th></tr>
      </thead>
      <tbody>
        <tr v-for="opt in commentOptions" :key="opt.field">
          <td><code>{{ opt.field }}</code></td>
          <td>{{ opt.required }}</td>
          <td>{{ opt.desc }}</td>
        </tr>
      </tbody>
    </table>

    <p class="s3-guide-tip">
      提示：MinIO / 自建对象存储通常需要 <code>pathStyle: true</code>；
      配置修改后在「附件驱动」列表对驱动停用再启用即可重新挂载。
    </p>
  </div>
</template>

<style scoped>
.s3-guide-container {
  width: 100%;
  padding: 24px;
  box-sizing: border-box;
}

.s3-guide-title {
  margin: 0 0 8px;
  font-size: 22px;
}

.s3-guide-desc {
  color: #666;
  margin: 0 0 16px;
}

.s3-guide-steps li {
  margin-bottom: 6px;
}

.s3-guide-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 16px;
  font-size: 14px;
}

.s3-guide-table th,
.s3-guide-table td {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}

.s3-guide-table th {
  background-color: #f5f7fa;
}

.s3-guide-pre {
  background-color: #1e1e1e;
  color: #d4d4d4;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
}

.s3-guide-tip {
  color: #909399;
  font-size: 13px;
}
</style>