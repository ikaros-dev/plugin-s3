package run.ikaros.plugin.s3.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.Assert;
import run.ikaros.plugin.s3.S3Const;

/**
 * AWS Signature Version 4 签名工具.
 *
 * <p>支持两种签名方式：
 * <ul>
 *     <li>header 签名：生成 Authorization 请求头，用于 ListObjectsV2 等列表请求；</li>
 *     <li>query 预签名：生成带 X-Amz- 参数的预签名 URL，用于对象读取与下载。</li>
 * </ul>
 *
 * <p>编码规则遵循 S3 规范：canonical URI 中 "/" 作为路径分隔符不编码，
 * query 字符串中的字符全部百分号编码（含 "/" 编码为 %2F）。
 *
 * @author Nekoli
 */
public class AwsSigV4Signer {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";

    /**
     * 无请求体（或请求体不参与签名）时的 payload 哈希标识.
     */
    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final DateTimeFormatter AMZ_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_SCOPE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigV4Signer() {
    }

    /**
     * 生成带 Authorization 请求头的签名（用于 GET 列表请求等）.
     *
     * @param method        HTTP 方法
     * @param canonicalUri  规范化路径（已按 S3 规则编码）
     * @param query         查询参数（按参数名字典序）
     * @param signedHeaders 参与签名的请求头（header 名小写，按名字典序）
     * @param payloadHash   请求体哈希，无请求体时传 UNSIGNED_PAYLOAD
     * @param now           签名时间
     * @param region        区域
     * @param accessKey     Access Key
     * @param secretKey     Secret Key
     * @return Authorization 请求头值
     */
    public static String signHeaders(String method, String canonicalUri,
                                     TreeMap<String, String> query,
                                     LinkedHashMap<String, String> signedHeaders,
                                     String payloadHash, Instant now,
                                     String region, String accessKey, String secretKey) {
        Assert.hasText(method, "'method' must has text.");
        Assert.hasText(canonicalUri, "'canonicalUri' must has text.");
        Assert.notNull(query, "'query' must not null.");
        Assert.notNull(signedHeaders, "'signedHeaders' must not null.");
        Assert.isTrue(signedHeaders.containsKey("host"), "'signedHeaders' must contain 'host' header.");
        Assert.hasText(region, "'region' must has text.");
        Assert.hasText(accessKey, "'accessKey' must has text.");
        Assert.hasText(secretKey, "'secretKey' must has text.");

        String amzDate = amzDate(now);
        String dateScope = DATE_SCOPE_FORMATTER.format(now);
        String scope = dateScope + "/" + region + "/" + S3Const.SERVICE_NAME + "/" + TERMINATOR;

        // x-amz-date 与 x-amz-content-sha256 由签名器统一计算填充，避免与调用方不一致
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(signedHeaders);
        if (headers.containsKey("x-amz-date")) {
            headers.put("x-amz-date", amzDate);
        }
        if (headers.containsKey("x-amz-content-sha256")) {
            headers.put("x-amz-content-sha256", payloadHash);
        }

        String canonicalQuery = buildCanonicalQuery(query);
        String canonicalHeaders = buildCanonicalHeaders(headers);
        // signed headers 必须与 canonical headers 保持一致的字典序
        String signedHeaderNames = headers.keySet().stream()
            .sorted()
            .collect(java.util.stream.Collectors.joining(";"));

        String canonicalRequest = method + "\n"
            + canonicalUri + "\n"
            + canonicalQuery + "\n"
            + canonicalHeaders + "\n"
            + signedHeaderNames + "\n"
            + payloadHash;

        String stringToSign = ALGORITHM + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + sha256Hex(canonicalRequest);

        byte[] signingKey = buildSigningKey(dateScope, region, secretKey);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        return ALGORITHM
            + " Credential=" + accessKey + "/" + scope
            + ", SignedHeaders=" + signedHeaderNames
            + ", Signature=" + signature;
    }

    /**
     * 生成预签名 GET URL（用于对象读取/下载/流式传输）.
     *
     * @param scheme        协议（http 或 https）
     * @param host          请求主机（含端口），如 s3.amazonaws.com 或 127.0.0.1:9000
     * @param canonicalUri  规范化路径（已按 S3 规则编码）
     * @param extraQuery    额外查询参数（如 response-content-disposition）
     * @param expiresSeconds 预签名有效期（秒）
     * @param now           签名时间
     * @param region        区域
     * @param accessKey     Access Key
     * @param secretKey     Secret Key
     * @return 预签名 URL
     */
    public static String presignGetUrl(String scheme, String host, String canonicalUri,
                                       TreeMap<String, String> extraQuery,
                                       int expiresSeconds, Instant now,
                                       String region, String accessKey, String secretKey) {
        Assert.hasText(scheme, "'scheme' must has text.");
        Assert.hasText(host, "'host' must has text.");
        Assert.hasText(canonicalUri, "'canonicalUri' must has text.");
        Assert.notNull(extraQuery, "'extraQuery' must not null.");
        Assert.isTrue(expiresSeconds > 0, "'expiresSeconds' must be positive.");

        String amzDate = AMZ_DATE_FORMATTER.format(now);
        String dateScope = DATE_SCOPE_FORMATTER.format(now);
        String scope = dateScope + "/" + region + "/" + S3Const.SERVICE_NAME + "/" + TERMINATOR;

        // 预签名查询参数（值保持原始形式，构造 canonical 时会统一编码）
        TreeMap<String, String> query = new TreeMap<>(extraQuery);
        query.put("X-Amz-Algorithm", ALGORITHM);
        query.put("X-Amz-Credential", accessKey + "/" + scope);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", String.valueOf(expiresSeconds));
        query.put("X-Amz-SignedHeaders", "host");

        String canonicalQuery = buildCanonicalQuery(query);
        String canonicalRequest = "GET\n" + canonicalUri + "\n" + canonicalQuery + "\n"
            + "host:" + host + "\n\nhost\n" + UNSIGNED_PAYLOAD;
        String stringToSign = ALGORITHM + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + sha256Hex(canonicalRequest);

        byte[] signingKey = buildSigningKey(dateScope, region, secretKey);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // 最终 URL 查询串：参数编码后追加签名（签名本身为十六进制，无需编码）
        query.put("X-Amz-Signature", signature);
        return scheme + "://" + host + canonicalUri + "?" + buildCanonicalQuery(query);
    }

    /**
     * 获取 AWS 日期时间格式（yyyyMMdd'T'HHmmss'Z'）.
     *
     * @param now 当前时间
     * @return 日期时间字符串
     */
    public static String amzDate(Instant now) {
        return AMZ_DATE_FORMATTER.format(now);
    }

    /**
     * 构造规范查询串：参数名与值全部按 S3 规则百分号编码（值中的 "/" 编码为 %2F）.
     */
    public static String buildCanonicalQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(query.size());
        query.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> parts.add(percentEncode(entry.getKey())
                + "=" + percentEncode(entry.getValue())));
        return String.join("&", parts);
    }

    /**
     * 构造规范请求头：header 名小写并字典序排序，值为 trim 后的原始内容.
     */
    public static String buildCanonicalHeaders(Map<String, String> signedHeaders) {
        if (signedHeaders == null || signedHeaders.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        signedHeaders.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> sb.append(entry.getKey().toLowerCase())
                .append(':')
                .append(entry.getValue().trim())
                .append('\n'));
        return sb.toString();
    }

    /**
     * 生成签名密钥：HMAC-SHA256 逐级派生.
     */
    public static byte[] buildSigningKey(String dateScope, String region, String secretKey) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateScope);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, S3Const.SERVICE_NAME);
        return hmacSha256(kService, TERMINATOR);
    }

    /**
     * RFC3986 百分号编码：保留字符不编码，其余字符编码为 %XX（大写十六进制）.
     * 与 UTF-8 字符集配合，正确处理中文、空格等字符.
     */
    public static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            if (isUnreserved(b)) {
                sb.append((char) b);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    private static boolean isUnreserved(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
            || b == '-' || b == '_' || b == '.' || b == '~';
    }

    /**
     * 规范化 S3 对象路径：按 "/" 分段，每段分别百分号编码，"/" 本身保留为分隔符.
     * 空路径返回 "/".
     */
    public static String canonicalizePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }
        // S3 对象键不以 "/" 开头，去掉可能的开头斜杠避免产生双斜杠
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        StringBuilder sb = new StringBuilder();
        for (String segment : normalized.split("/", -1)) {
            sb.append('/');
            if (!segment.isEmpty()) {
                sb.append(percentEncode(segment));
            }
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }


    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return hex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available.", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available.", e);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return hex(hmacSha256(key, data));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

}