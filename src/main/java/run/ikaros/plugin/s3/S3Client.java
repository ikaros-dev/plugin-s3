package run.ikaros.plugin.s3;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.HttpClientErrorException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.ikaros.plugin.s3.model.S3ObjectEntry;
import run.ikaros.plugin.s3.utils.AwsSigV4Signer;

/**
 * S3 协议访问客户端：基于 WebClient 实现 ListObjectsV2 列表、对象流式读取与预签名直链生成.
 *
 * <p>不依赖任何 AWS SDK，签名由 {@link AwsSigV4Signer} 自行实现.
 *
 * @author Nekoli
 */
@Slf4j
@Component
public class S3Client {

    /**
     * 防止服务端异常时无限翻页的安全上限.
 */
    private static final int MAX_LIST_PAGES = 100;

    /**
     * ListObjectsV2 列表参数名.
 */
    private static final String PARAM_LIST_TYPE = "list-type";
    private static final String PARAM_PREFIX = "prefix";
    private static final String PARAM_DELIMITER = "delimiter";
    private static final String PARAM_MAX_KEYS = "max-keys";
    private static final String PARAM_CONTINUATION_TOKEN = "continuation-token";

    private final WebClient webClient;

    public S3Client() {
        // 列表响应 XML 可能较大，适当提高内存缓冲上限
        final int size = 10 * 1024 * 1024;
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(size))
            .build();
        webClient = WebClient.builder()
            .exchangeStrategies(strategies)
            .defaultHeader(HttpHeaders.USER_AGENT, S3Const.REST_TEMPLATE_USER_AGENT)
            .build();
    }

    /**
     * 列出存储桶中指定前缀下的对象与虚拟目录（公共前缀）.
 *
 * @param cfg    S3 驱动配置
     * @param prefix 对象键前缀，根目录传空串
     * @return 条目流
     */
    public Flux<S3ObjectEntry> listObjects(S3DriverConfig cfg, String prefix) {
        Assert.notNull(cfg, "'cfg' must not null.");
        String normalizedPrefix = prefix == null ? "" : prefix;
        return listPage(cfg, normalizedPrefix, null, 1)
            .expand(holder -> holder.isTruncated()
                && StringUtils.hasText(holder.getNextToken())
                && holder.getPage() < MAX_LIST_PAGES
                ? listPage(cfg, normalizedPrefix, holder.getNextToken(), holder.getPage() + 1)
                : Mono.empty())
            .flatMapIterable(ListResultHolder::getEntries);
    }

    /**
     * 单页 ListObjectsV2 请求.
 */
    private Mono<ListResultHolder> listPage(S3DriverConfig cfg, String prefix,
                                            String continuationToken, int page) {
        TreeMap<String, String> query = new TreeMap<>();
        query.put(PARAM_LIST_TYPE, "2");
        query.put(PARAM_PREFIX, prefix);
        query.put(PARAM_DELIMITER, "/");
        query.put(PARAM_MAX_KEYS, String.valueOf(S3Const.LIST_MAX_KEYS));
        if (StringUtils.hasText(continuationToken)) {
            query.put(PARAM_CONTINUATION_TOKEN, continuationToken);
        }
        return requestWithHeaderAuth(cfg, "GET", cfg.buildListUri(), query)
            .flatMap(xml -> {
                ListBucketResult result = parseListBucketResult(xml);
                return Mono.just(new ListResultHolder(result.getEntries(),
                    result.isTruncated(), result.getNextToken(), page));
            })
            .doOnError(HttpClientErrorException.class, e ->
                log.warn("S3 list objects fail for bucket={}, prefix={}, reason={}",
                    cfg.getBucket(), prefix, e.getMessage()));
    }

    /**
     * 带 Authorization 请求头的签名请求，返回响应体字符串.
 *
 * <p>签名采用 AWS CLI 同款标准：签名 host、x-amz-date、x-amz-content-sha256 三个请求头.
 */
    private Mono<String> requestWithHeaderAuth(S3DriverConfig cfg, String method,
                                               String canonicalUri,
                                               TreeMap<String, String> query) {
        Instant now = Instant.now();
        LinkedHashMap<String, String> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put("host", cfg.getRequestHost());
        signedHeaders.put("x-amz-date", AwsSigV4Signer.amzDate(now));
        signedHeaders.put("x-amz-content-sha256", AwsSigV4Signer.UNSIGNED_PAYLOAD);
        String authorization = AwsSigV4Signer.signHeaders(method, canonicalUri, query,
            signedHeaders, AwsSigV4Signer.UNSIGNED_PAYLOAD, now, cfg.getRegion(),
            cfg.getAccessKey(), cfg.getSecretKey());
        String requestUrl = cfg.getScheme() + "://" + cfg.getRequestHost() + canonicalUri
            + "?" + AwsSigV4Signer.buildCanonicalQuery(query);
        // Host 头由 WebClient 根据 URL 自动生成，与签名一致，无需（也不应）手动覆盖。
        // 注意：必须用 URI.create 传入，否则 WebClient 会对已编码的查询串（%2F 等）二次编码导致签名失效。
        return webClient.get()
            .uri(java.net.URI.create(requestUrl))
            .header("x-amz-date", AwsSigV4Signer.amzDate(now))
            .header("x-amz-content-sha256", AwsSigV4Signer.UNSIGNED_PAYLOAD)
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .accept(MediaType.APPLICATION_XML)
            .retrieve()
            .bodyToMono(String.class);
    }

    /**
     * 构建对象访问直链：配置了自定义域名时返回无签名的公开直链，否则返回预签名 URL.
 *
 * @param cfg      S3 驱动配置
     * @param key      对象键
     * @param download 是否用于下载（追加 attachment 响应头参数）
     * @return 访问直链
     */
    public String buildAccessUrl(S3DriverConfig cfg, String key, boolean download) {
        return buildAccessUrl(cfg, key, download, Instant.now(),
            S3Const.PRE_SIGN_EXPIRE_SECONDS);
    }

    /**
     * 构建对象访问直链（指定时间与有效期，便于单元测试）.
 */
    public String buildAccessUrl(S3DriverConfig cfg, String key, boolean download,
                                 Instant now, int expiresSeconds) {
        Assert.notNull(cfg, "'cfg' must not null.");
        Assert.hasText(key, "'key' must has text.");
        TreeMap<String, String> query = new TreeMap<>();
        if (download) {
            query.put("response-content-disposition", "attachment");
        }
        return AwsSigV4Signer.presignGetUrl(cfg.getScheme(), cfg.getRequestHost(),
            cfg.buildObjectUri(key), query, expiresSeconds, now, cfg.getRegion(),
            cfg.getAccessKey(), cfg.getSecretKey());
    }

    /**
     * 构建流式传输用直链（较短有效期）.
 */
    String buildStreamUrl(S3DriverConfig cfg, String key) {
        // 服务端流式代理必须始终携带签名：不能因配置了自定义域名而放弃签名，
        // 否则对非公开读的存储桶请求会返回 403. 自定义域名仅用于给客户端的公开直链.
        return AwsSigV4Signer.presignGetUrl(cfg.getScheme(), cfg.getRequestHost(),
            cfg.buildObjectUri(key), new TreeMap<>(), S3Const.STREAM_PRE_SIGN_EXPIRE_SECONDS,
            Instant.now(), cfg.getRegion(), cfg.getAccessKey(), cfg.getSecretKey());
    }

    /**
     * 流式读取对象内容.
 *
 * @param cfg   S3 驱动配置
     * @param key   对象键
     * @param start 起始字节，null 表示从头
     * @param end   结束字节，null 表示到末尾
     * @return 数据缓冲流
     */
    public Flux<DataBuffer> getObjectStream(S3DriverConfig cfg, String key,
                                            Long start, Long end) {
        Assert.notNull(cfg, "'cfg' must not null.");
        Assert.hasText(key, "'key' must has text.");
        String url = buildStreamUrl(cfg, key);
        WebClient.RequestHeadersSpec<?> spec = webClient.get()
            .uri(java.net.URI.create(url))
            .accept(MediaType.APPLICATION_OCTET_STREAM);
        if (start != null && start >= 0) {
            String rangeHeader;
            if (end != null && end >= start) {
                rangeHeader = String.format("bytes=%d-%d", start, end);
            } else {
                rangeHeader = String.format("bytes=%d-", start);
            }
            spec = spec.header(HttpHeaders.RANGE, rangeHeader);
        }
        return spec.retrieve()
            .bodyToFlux(DataBuffer.class)
            .doOnError(HttpClientErrorException.class, e ->
                log.warn("S3 get object stream fail for key={}, start={}, end={}, reason={}",
                    key, start, end, e.getMessage()));
    }

    /**
     * 解析 ListObjectsV2 响应 XML.
 *
 * @param xml 响应体
     * @return 解析结果
     */
    public static ListBucketResult parseListBucketResult(String xml) {
        Assert.hasText(xml, "'xml' must has text.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用外部实体与 DTD，防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();

            boolean truncated = "true".equals(getFirstChildText(root, "IsTruncated"));
            String nextToken = getFirstChildText(root, "NextContinuationToken");

            List<S3ObjectEntry> entries = new ArrayList<>();
            NodeList contentsNodes = root.getElementsByTagName("Contents");
            for (int i = 0; i < contentsNodes.getLength(); i++) {
                Element content = (Element) contentsNodes.item(i);
                String key = getFirstChildText(content, "Key");
                long size = parseLong(getFirstChildText(content, "Size"));
                String etag = getFirstChildText(content, "ETag");
                String lastModified = getFirstChildText(content, "LastModified");
                entries.add(S3ObjectEntry.builder()
                    .dir(false)
                    .key(key)
                    .size(size)
                    .etag(stripQuotes(etag))
                    .lastModified(parseIso8601(lastModified))
                    .build());
            }
            NodeList commonPrefixesNodes = root.getElementsByTagName("CommonPrefixes");
            for (int i = 0; i < commonPrefixesNodes.getLength(); i++) {
                Element commonPrefixes = (Element) commonPrefixesNodes.item(i);
                String prefix = getFirstChildText(commonPrefixes, "Prefix");
                if (StringUtils.hasText(prefix)) {
                    entries.add(S3ObjectEntry.builder()
                        .dir(true)
                        .key(prefix)
                        .size(0L)
                        .build());
                }
            }
            return new ListBucketResult(entries, truncated, nextToken);
        } catch (Exception e) {
            throw new IllegalStateException("Parse S3 ListObjectsV2 xml fail.", e);
        }
    }

    private static String getFirstChildText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        Node node = nodeList.item(0);
        return node.getTextContent();
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        String trimmed = etag.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static long parseLong(String text) {
        if (text == null) {
            return 0;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static java.time.LocalDateTime parseIso8601(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return java.time.LocalDateTime
                .ofInstant(Instant.parse(text.trim()), java.time.ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 列表响应解析结果.
 *
 * @author Nekoli
 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ListBucketResult {
        /**
         * 条目列表.
 */
        private List<S3ObjectEntry> entries;
        /**
         * 是否还有更多结果（需翻页）.
 */
        private boolean truncated;
        /**
         * 下一页续传令牌.
 */
        private String nextToken;
    }

    /**
     * 翻页过程中的结果容器.
 *
 * @author Nekoli
 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ListResultHolder {
        private List<S3ObjectEntry> entries;
        private boolean truncated;
        private String nextToken;
        private int page;
    }
}