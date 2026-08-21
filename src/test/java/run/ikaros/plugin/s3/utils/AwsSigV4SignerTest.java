package run.ikaros.plugin.s3.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * AWS SigV4 签名工具单元测试.
 *
 * <p>预签名用例采用 AWS 官方文档《Authenticating Requests: Using Query Parameters》中的示例，
 * 验证签名结果与官方示例一致。</p>
 *
 * @author Nekoli
 */
class AwsSigV4SignerTest {

    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String REGION = "us-east-1";
    private static final Instant NOW = Instant.parse("2013-05-24T00:00:00Z");

    @Test
    void presignGetUrlMatchesBotocoreReference() {
        // 黄金值来自 AWS SDK 官方参考实现 botocore（SigV4QueryAuth，时间冻结到同一时刻）
        // 交叉验证：botocore 1.43.77 generate_presigned_url(get_object) 输出完全相同
        String url = AwsSigV4Signer.presignGetUrl("https", "examplebucket.s3.amazonaws.com",
            "/test.txt", new TreeMap<>(), 86400, NOW, REGION, ACCESS_KEY, SECRET_KEY);

        assertThat(url).startsWith(
            "https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(url).contains("X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1"
            + "%2Fs3%2Faws4_request");
        assertThat(url).contains("X-Amz-Date=20130524T000000Z");
        assertThat(url).contains("X-Amz-Expires=86400");
        assertThat(url).contains("X-Amz-SignedHeaders=host");
        assertThat(url).contains(
            "X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void signHeadersProducesAuthorizationHeader() {
        TreeMap<String, String> query = new TreeMap<>();
        query.put("list-type", "2");
        query.put("prefix", "");
        query.put("delimiter", "/");
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "examplebucket.s3.amazonaws.com");
        headers.put("x-amz-date", "20130524T000000Z");
        headers.put("x-amz-content-sha256", AwsSigV4Signer.UNSIGNED_PAYLOAD);

        String authorization = AwsSigV4Signer.signHeaders("GET", "/", query, headers,
            AwsSigV4Signer.UNSIGNED_PAYLOAD, NOW, REGION, ACCESS_KEY, SECRET_KEY);

        assertThat(authorization).startsWith("AWS4-HMAC-SHA256 Credential=" + ACCESS_KEY
            + "/20130524/us-east-1/s3/aws4_request");
        assertThat(authorization).contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date");
        assertThat(authorization).matches(".*Signature=[0-9a-f]{64}");
    }

    @Test
    void canonicalQueryEncodesSlashAndSpace() {
        TreeMap<String, String> query = new TreeMap<>();
        query.put("prefix", "a b/中文");
        query.put("delimiter", "/");

        String canonicalQuery = AwsSigV4Signer.buildCanonicalQuery(query);

        assertThat(canonicalQuery).isEqualTo("delimiter=%2F&prefix=a%20b%2F%E4%B8%AD%E6%96%87");
    }

    @Test
    void canonicalizePathEncodesEachSegment() {
        assertThat(AwsSigV4Signer.canonicalizePath("")).isEqualTo("/");
        assertThat(AwsSigV4Signer.canonicalizePath("dir/file.txt"))
            .isEqualTo("/dir/file.txt");
        assertThat(AwsSigV4Signer.canonicalizePath("a b/中文.txt"))
            .isEqualTo("/a%20b/%E4%B8%AD%E6%96%87.txt");
        assertThat(AwsSigV4Signer.canonicalizePath("dir/"))
            .isEqualTo("/dir/");
    }

    @Test
    void percentEncodeFollowsRfc3986() {
        assertThat(AwsSigV4Signer.percentEncode("a~b-c_d.e")).isEqualTo("a~b-c_d.e");
        assertThat(AwsSigV4Signer.percentEncode("a b")).isEqualTo("a%20b");
        assertThat(AwsSigV4Signer.percentEncode("a%2Fb")).isEqualTo("a%252Fb");
    }
}