package run.ikaros.plugin.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import run.ikaros.plugin.s3.model.S3ObjectEntry;

/**
 * S3Client 单元测试：ListObjectsV2 响应 XML 解析、直链生成、配置解析.
 *
 * @author Nekoli
 */
class S3ClientTest {

    private static final String SAMPLE_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          <Name>examplebucket</Name>
          <Prefix></Prefix>
          <KeyCount>3</KeyCount>
          <MaxKeys>1000</MaxKeys>
          <Delimiter>/</Delimiter>
          <IsTruncated>true</IsTruncated>
          <NextContinuationToken>abcdef123</NextContinuationToken>
          <Contents>
            <Key>photos/</Key>
            <LastModified>2024-01-01T00:00:00.000Z</LastModified>
            <ETag>&quot;d41d8cd98f00b204e9800998ecf8427e&quot;</ETag>
            <Size>0</Size>
            <StorageClass>STANDARD</StorageClass>
          </Contents>
          <Contents>
            <Key>doc.txt</Key>
            <LastModified>2024-02-02T12:30:00.000Z</LastModified>
            <ETag>&quot;abc123def456&quot;</ETag>
            <Size>1024</Size>
            <StorageClass>STANDARD</StorageClass>
          </Contents>
          <CommonPrefixes>
            <Prefix>movies/</Prefix>
          </CommonPrefixes>
          <CommonPrefixes>
            <Prefix>images/</Prefix>
          </CommonPrefixes>
        </ListBucketResult>
        """;

    @Test
    void parseListBucketResultParsesContentsAndCommonPrefixes() {
        S3Client.ListBucketResult result = S3Client.parseListBucketResult(SAMPLE_XML);

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getNextToken()).isEqualTo("abcdef123");
        assertThat(result.getEntries()).hasSize(4);

        S3ObjectEntry dirEntry = result.getEntries().stream()
            .filter(S3ObjectEntry::isDir)
            .findFirst()
            .orElseThrow();
        assertThat(dirEntry.getKey()).isEqualTo("movies/");

        S3ObjectEntry fileEntry = result.getEntries().stream()
            .filter(entry -> "doc.txt".equals(entry.getKey()))
            .findFirst()
            .orElseThrow();
        assertThat(fileEntry.isDir()).isFalse();
        assertThat(fileEntry.getSize()).isEqualTo(1024L);
        assertThat(fileEntry.getEtag()).isEqualTo("abc123def456");
        assertThat(fileEntry.getLastModified()).isNotNull();
    }

    @Test
    void buildAccessUrlReplacesHostWithCustomDomain() {
        S3DriverConfig cfg = new S3DriverConfig();
        cfg.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        cfg.setRegion("cn-hangzhou");
        cfg.setBucket("my-bucket");
        cfg.setAccessKey("AKID");
        cfg.setSecretKey("SK");
        cfg.setDomain("https://cdn.example.com");
        S3Client client = new S3Client();

        String url = client.buildAccessUrl(cfg, "dir/obj.mp4", false,
            Instant.ofEpochSecond(1720000000L), 3600);

        assertThat(url).startsWith("https://cdn.example.com/dir/obj.mp4?");
        assertThat(url).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(url).contains("X-Amz-SignedHeaders=host");
        // 不应再出现 OSS 原生域名
        assertThat(url).doesNotContain("my-bucket.oss-cn-hangzhou.aliyuncs.com");
    }

    @Test
    void buildAccessUrlKeepsOriginalHostWithoutCustomDomain() {
        S3DriverConfig cfg = new S3DriverConfig();
        cfg.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        cfg.setRegion("cn-hangzhou");
        cfg.setBucket("my-bucket");
        cfg.setAccessKey("AKID");
        cfg.setSecretKey("SK");
        S3Client client = new S3Client();

        String url = client.buildAccessUrl(cfg, "dir/obj.mp4", false,
            Instant.ofEpochSecond(1720000000L), 3600);

        assertThat(url).startsWith("https://my-bucket.oss-cn-hangzhou.aliyuncs.com/dir/obj.mp4?");
    }

    @Test
    void parseListBucketResultHandlesEmptyXmlTags() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>a.txt</Key>
                <LastModified>2024-01-01T00:00:00.000Z</LastModified>
                <ETag>&quot;etag1&quot;</ETag>
                <Size>10</Size>
              </Contents>
            </ListBucketResult>
            """;

        S3Client.ListBucketResult result = S3Client.parseListBucketResult(xml);

        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getNextToken()).isNull();
        assertThat(result.getEntries()).hasSize(1);
    }
}