package run.ikaros.plugin.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import run.ikaros.api.core.attachment.AttachmentDriver;

/**
 * S3 驱动配置解析单元测试.
 *
 * @author Nekoli
 */
class S3DriverConfigTest {

    @Test
    void parseWithCommentConfig() {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setRemotePath("my-bucket");
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");
        driver.setComment("{\"endpoint\":\"http://127.0.0.1:9000\","
            + "\"region\":\"us-east-1\",\"pathStyle\":true,"
            + "\"domain\":\"https://static.example.com\"}");

        S3DriverConfig cfg = S3DriverConfig.parse(driver);

        assertThat(cfg.getBucket()).isEqualTo("my-bucket");
        assertThat(cfg.getAccessKey()).isEqualTo("AKIAEXAMPLE");
        assertThat(cfg.getSecretKey()).isEqualTo("secret");
        assertThat(cfg.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(cfg.getRegion()).isEqualTo("us-east-1");
        assertThat(cfg.isPathStyle()).isTrue();
        assertThat(cfg.getDomain()).isEqualTo("https://static.example.com");
        assertThat(cfg.hasCustomDomain()).isTrue();
        assertThat(cfg.getScheme()).isEqualTo("http");
        assertThat(cfg.getHost()).isEqualTo("127.0.0.1:9000");
        assertThat(cfg.getRequestHost()).isEqualTo("127.0.0.1:9000");
    }

    @Test
    void parseWithDefaultsWhenCommentMissing() {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setRemotePath("my-bucket");
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");

        S3DriverConfig cfg = S3DriverConfig.parse(driver);

        assertThat(cfg.getEndpoint()).isEqualTo(S3Const.DEFAULT_ENDPOINT);
        assertThat(cfg.getRegion()).isEqualTo(S3Const.DEFAULT_REGION);
        assertThat(cfg.isPathStyle()).isFalse();
        assertThat(cfg.getHost()).isEqualTo("s3.amazonaws.com");
        assertThat(cfg.getRequestHost()).isEqualTo("my-bucket.s3.amazonaws.com");
    }

    @Test
    void autoEnablePathStyleForIpEndpoint() {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setRemotePath("my-bucket");
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");
        driver.setComment("{\"endpoint\":\"http://192.168.1.10:9000\"}");

        S3DriverConfig cfg = S3DriverConfig.parse(driver);

        assertThat(cfg.isPathStyle()).isTrue();
        assertThat(cfg.getRequestHost()).isEqualTo("192.168.1.10:9000");
        assertThat(cfg.buildListUri()).isEqualTo("/my-bucket");
        assertThat(cfg.buildObjectUri("a/b.txt")).isEqualTo("/my-bucket/a/b.txt");
    }

    @Test
    void parseFailsWhenBucketMissing() {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");

        assertThatThrownBy(() -> S3DriverConfig.parse(driver))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("remotePath");
    }

    @Test
    void invalidCommentFallsBackToDefaults() {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setRemotePath("my-bucket");
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");
        driver.setComment("not a json");

        S3DriverConfig cfg = S3DriverConfig.parse(driver);

        assertThat(cfg.getEndpoint()).isEqualTo(S3Const.DEFAULT_ENDPOINT);
        assertThat(cfg.isPathStyle()).isFalse();
    }
}