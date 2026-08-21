package run.ikaros.plugin.s3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import run.ikaros.api.core.attachment.AttachmentDriver;
import run.ikaros.plugin.s3.utils.AwsSigV4Signer;

/**
 * S3 驱动配置，由 ikaros 附件驱动通用字段解析而来.
 *
 * <p>字段映射约定：
 * <ul>
 * <li>remotePath：S3 存储桶名称（bucket）</li>
 * <li>accessToken：Access Key ID</li>
 * <li>refreshToken：Secret Access Key</li>
 * <li>comment：JSON 补充配置，格式见 {@link S3Comment}</li>
 * </ul>
 * @author Nekoli
 */
@Slf4j
@Data
public class S3DriverConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * S3 服务端点，默认 AWS S3.
     */
    private String endpoint = S3Const.DEFAULT_ENDPOINT;

    /**
     * S3 区域，默认 us-east-1.
     */
    private String region = S3Const.DEFAULT_REGION;

    /**
     * 是否使用路径风格（path-style）访问，默认按端点主机名自动推断：
     * IP 或 localhost 地址自动使用路径风格，域名默认使用虚拟主机风格.
     */
    private boolean pathStyle;

    /**
     * 存储桶名称.
     */
    private String bucket;

    /**
     * Access Key ID.
     */
    private String accessKey;

    /**
     * Secret Access Key.
     */
    private String secretKey;

    /**
     * 自定义访问域名（可选），配置后直链将使用该域名拼接存储桶与对象键生成，
     * 适用于已通过 CDN/反代公开读的存储桶，此时不生成预签名 URL.
     */
    private String domain;

    /**
     * 从 ikaros 附件驱动解析 S3 配置.
     *
     * @param driver 附件驱动
     * @return S3 驱动配置
     */
    public static S3DriverConfig parse(AttachmentDriver driver) {
        Assert.notNull(driver, "'driver' must not null.");
        S3DriverConfig config = new S3DriverConfig();
        config.setBucket(driver.getRemotePath());
        config.setAccessKey(driver.getAccessToken());
        config.setSecretKey(driver.getRefreshToken());

        parseComment(driver.getComment()).ifPresent(comment -> {
            if (StringUtils.hasText(comment.getEndpoint())) {
                config.setEndpoint(comment.getEndpoint());
            }
            if (StringUtils.hasText(comment.getRegion())) {
                config.setRegion(comment.getRegion());
            }
            if (comment.getPathStyle() != null) {
                config.setPathStyle(comment.getPathStyle());
            }
            if (StringUtils.hasText(comment.getDomain())) {
                config.setDomain(comment.getDomain());
            }
        });

        Assert.hasText(config.getBucket(),
            "'remotePath' 必须填写 S3 存储桶名称(bucket)。请在驱动配置的远程路径中填写存储桶名称。");
        Assert.hasText(config.getAccessKey(),
            "'accessToken' 必须填写 S3 Access Key ID。请在驱动配置的访问令牌中填写。");
        Assert.hasText(config.getSecretKey(),
            "'refreshToken' 必须填写 S3 Secret Access Key。请在驱动配置的刷新令牌中填写。");

        // 未显式指定 pathStyle 时按端点主机自动推断：IP/localhost 端点自动使用路径风格
        if (commentPathStyleNotSet(driver) && looksLikeLocalHost(config.getHost())) {
            config.setPathStyle(true);
        }
        return config;
    }

    private static boolean commentPathStyleNotSet(AttachmentDriver driver) {
        return parseComment(driver.getComment()).map(c -> c.getPathStyle() == null)
            .orElse(true);
    }

    private static boolean looksLikeLocalHost(String host) {
        if (host == null) {
            return false;
        }
        return host.startsWith("127.") || host.startsWith("10.")
            || host.startsWith("192.168.") || host.startsWith("172.")
            || host.startsWith("169.254.") || host.equals("localhost");
    }

    /**
     * 解析驱动 comment 字段中的 JSON 补充配置，非 JSON 内容时返回空.
     */
    static Optional<S3Comment> parseComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readValue(comment, S3Comment.class));
        } catch (Exception e) {
            log.warn("Parse S3 driver comment fail, comment=[{}], use default config. "
                + "comment 需为 JSON 格式: {\"endpoint\":\"...\",\"region\":\"...\","
                + "\"pathStyle\":true,\"domain\":\"...\"}", comment);
            return Optional.empty();
        }
    }

    /**
     * 获取协议（http/https）.
     */
    public String getScheme() {
        return URI.create(endpoint).getScheme();
    }

    /**
     * 获取端点主机（含端口）.
     */
    public String getHost() {
        URI uri = URI.create(endpoint);
        int port = uri.getPort();
        return port > 0 ? uri.getHost() + ":" + port : uri.getHost();
    }

    /**
     * 获取请求主机：路径风格时为端点主机，虚拟主机风格时为 bucket.端点主机.
     */
    public String getRequestHost() {
        return pathStyle ? getHost() : bucket + "." + getHost();
    }

    /**
     * 构建对象访问规范路径（已编码）：包含存储桶前缀（路径风格）与对象键.
     *
     * @param key 对象键
     * @return 规范路径
     */
    public String buildObjectUri(String key) {
        String objectPath = AwsSigV4Signer.canonicalizePath(key);
        return pathStyle ? AwsSigV4Signer.canonicalizePath("/" + bucket + "/" + key)
            : objectPath;
    }

    /**
     * 构建列表请求规范路径：路径风格时为 /bucket，虚拟主机风格时为 /.
     *
     * @return 规范路径
     */
    public String buildListUri() {
        return pathStyle ? AwsSigV4Signer.canonicalizePath("/" + bucket) : "/";
    }

    /**
     * 判断是否配置了自定义访问域名.
     */
    public boolean hasCustomDomain() {
        return StringUtils.hasText(domain);
    }

    /**
     * S3 补充配置（comment 字段 JSON）.
     *
     * @author Nekoli
     */
    @Data
    public static class S3Comment {
        /**
         * S3 服务端点，如 https://s3.amazonaws.com 或 http://127.0.0.1:9000.
         */
        private String endpoint;
        /**
         * 区域，如 us-east-1、cn-north-1.
         */
        private String region;
        /**
         * 是否使用路径风格访问（MinIO 等自建服务通常为 true）.
         * 不填时自动推断：IP/localhost 端点使用路径风格.
         */
        private Boolean pathStyle;
        /**
         * 自定义访问域名（公开读/CDN 场景）. 配置后直链不再携带签名.
         */
        private String domain;
    }
}