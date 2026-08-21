package run.ikaros.plugin.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import run.ikaros.api.core.attachment.Attachment;
import run.ikaros.api.core.attachment.AttachmentDriver;
import run.ikaros.api.store.enums.AttachmentType;
import run.ikaros.plugin.s3.model.S3ObjectEntry;

/**
 * S3 附件驱动抓取器转换逻辑单元测试.
 *
 * @author Nekoli
 */
class S3AttachmentDriverFetcherTest {

    private static final UUID DRIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void normalizePrefixUsesEmptyForRoot() {
        S3DriverConfig cfg = buildConfig("my-bucket");

        assertThat(fetcher().normalizePrefix(cfg, null)).isEmpty();
        assertThat(fetcher().normalizePrefix(cfg, "")).isEmpty();
        // 驱动根目录的 fsPath 为存储桶名本身
        assertThat(fetcher().normalizePrefix(cfg, "my-bucket")).isEmpty();
    }

    @Test
    void normalizePrefixEndsWithSlash() {
        S3DriverConfig cfg = buildConfig("my-bucket");

        assertThat(fetcher().normalizePrefix(cfg, "movies")).isEqualTo("movies/");
        assertThat(fetcher().normalizePrefix(cfg, "movies/")).isEqualTo("movies/");
        assertThat(fetcher().normalizePrefix(cfg, "/movies/")).isEqualTo("movies/");
    }

    @Test
    void toAttachmentForDirectory() {
        S3ObjectEntry entry = S3ObjectEntry.builder()
            .dir(true)
            .key("movies/")
            .size(0L)
            .build();

        Attachment attachment = fetcher().toAttachment(DRIVER_ID, PARENT_ID, entry);

        assertThat(attachment.getType()).isEqualTo(AttachmentType.Driver_Directory);
        assertThat(attachment.getName()).isEqualTo("movies");
        assertThat(attachment.getFsPath()).isEqualTo("movies/");
        assertThat(attachment.getPath()).isEqualTo("movies/");
        assertThat(attachment.getParentId()).isEqualTo(PARENT_ID);
        assertThat(attachment.getDriverId()).isEqualTo(DRIVER_ID);
    }

    @Test
    void toAttachmentForFile() {
        S3ObjectEntry entry = S3ObjectEntry.builder()
            .dir(false)
            .key("movies/star-wars.mp4")
            .size(2048L)
            .etag("abc123")
            .build();

        Attachment attachment = fetcher().toAttachment(DRIVER_ID, PARENT_ID, entry);

        assertThat(attachment.getType()).isEqualTo(AttachmentType.Driver_File);
        assertThat(attachment.getName()).isEqualTo("star-wars.mp4");
        assertThat(attachment.getFsPath()).isEqualTo("movies/star-wars.mp4");
        assertThat(attachment.getUrl()).isEqualTo("movies/star-wars.mp4");
        assertThat(attachment.getSize()).isEqualTo(2048L);
        assertThat(attachment.getSha1()).isEqualTo("abc123");
    }

    private S3AttachmentDriverFetcher fetcher() {
        return new S3AttachmentDriverFetcher(null, null);
    }

    private S3DriverConfig buildConfig(String bucket) {
        AttachmentDriver driver = new AttachmentDriver();
        driver.setRemotePath(bucket);
        driver.setAccessToken("AKIAEXAMPLE");
        driver.setRefreshToken("secret");
        return S3DriverConfig.parse(driver);
    }
}