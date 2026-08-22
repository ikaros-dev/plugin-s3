package run.ikaros.plugin.s3;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.ikaros.api.constant.OpenApiConst;
import run.ikaros.api.core.attachment.Attachment;
import run.ikaros.api.core.attachment.AttachmentDriver;
import run.ikaros.api.core.attachment.AttachmentDriverFetcher;
import run.ikaros.api.core.attachment.AttachmentDriverOperate;
import run.ikaros.api.infra.utils.FileUtils;
import run.ikaros.api.infra.utils.StringUtils;
import run.ikaros.api.store.enums.AttachmentDriverType;
import run.ikaros.api.store.enums.AttachmentType;
import run.ikaros.plugin.s3.model.S3ObjectEntry;

/**
 * S3 协议附件驱动抓取器：将兼容 S3 协议的对象存储（AWS S3、MinIO、阿里云 OSS 等）<br>
 * 挂载为 ikaros 附件驱动，支持目录浏览、读取、下载与流式播放.
 *
 * @author Nekoli
 */
@Slf4j
@Extension
public class S3AttachmentDriverFetcher implements AttachmentDriverFetcher {

    private final S3Client s3Client;
    private final AttachmentDriverOperate driverOperate;

    public S3AttachmentDriverFetcher(S3Client s3Client, AttachmentDriverOperate driverOperate) {
        this.s3Client = s3Client;
        this.driverOperate = driverOperate;
    }

    @Override
    public AttachmentDriverType getDriverType() {
        return AttachmentDriverType.CUSTOM;
    }

    @Override
    public String getDriverName() {
        return S3Const.DRIVER_NAME;
    }

    @Override
    public Flux<Attachment> getChildren(UUID driverId, UUID pid, String remotePath) {
        Assert.notNull(driverId, "'driverId' must not null.");
        Assert.notNull(pid, "'pid' must not null.");
        return findDriver(driverId)
            .flatMapMany(driver -> {
                S3DriverConfig cfg = S3DriverConfig.parse(driver);
                String prefix = normalizePrefix(cfg, remotePath);
                return s3Client.listObjects(cfg, prefix)
                    // 跳过与查询前缀相同的“目录标记对象”，避免将目录自身当成文件
                    .filter(entry -> entry.isDir() || !StringUtils.isNotBlank(prefix)
                        || !entry.getKey().equals(prefix))
                    .map(entry -> toAttachment(driverId, pid, entry));
            });
    }

    @Override
    public Mono<String> parseReadUrl(Attachment attachment) {
        Assert.notNull(attachment, "'attachment' must not null.");
        AttachmentType type = attachment.getType();
        if (AttachmentType.Driver_Directory.equals(type)) {
            return Mono.just(attachment.getUrl());
        }
        String key = attachment.getUrl();
        Assert.hasText(key, "'attachment.url' must has text for S3 object key.");
        UUID driverId = attachment.getDriverId();
        return findDriver(driverId)
                .map(driver -> s3Client.buildAccessUrl(S3DriverConfig.parse(driver), key, false));
    }

    @Override
    public Mono<String> parseDownloadUrl(Attachment attachment) {
        Assert.notNull(attachment, "'attachment' must not null.");
        AttachmentType type = attachment.getType();
        if (AttachmentType.Driver_Directory.equals(type)) {
            return Mono.just(attachment.getUrl());
        }
        String key = attachment.getUrl();
        Assert.hasText(key, "'attachment.url' must has text for S3 object key.");
        return findDriver(attachment.getDriverId())
            .map(driver -> s3Client.buildAccessUrl(S3DriverConfig.parse(driver), key, true));
    }

    @Override
    public Flux<DataBuffer> getSteam(Attachment attachment) {
        Assert.notNull(attachment, "'attachment' must not null.");
        if (AttachmentType.Driver_Directory.equals(attachment.getType())) {
            return Flux.empty();
        }
        String key = attachment.getUrl();
        Assert.hasText(key, "'attachment.url' must has text for S3 object key.");
        return findDriver(attachment.getDriverId())
            .flatMapMany(driver ->
                s3Client.getObjectStream(S3DriverConfig.parse(driver), key, null, null));
    }

    @Override
    public Flux<DataBuffer> getSteam(Attachment attachment, long start, long end) {
        Assert.notNull(attachment, "'attachment' must not null.");
        if (AttachmentType.Driver_Directory.equals(attachment.getType())) {
            return Flux.empty();
        }
        String key = attachment.getUrl();
        Assert.hasText(key, "'attachment.url' must has text for S3 object key.");
        return findDriver(attachment.getDriverId())
            .flatMapMany(driver ->
                s3Client.getObjectStream(S3DriverConfig.parse(driver), key, start, end));
    }

    /**
     * 按驱动 ID 查找驱动，缺失时返回错误.
     */
    private Mono<AttachmentDriver> findDriver(UUID driverId) {
        Assert.notNull(driverId, "'driverId' must not null.");
        return driverOperate.findById(driverId)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "Attachment driver not found for id=" + driverId)));
    }

    /**
     * 归一化列表前缀：驱动根目录（remotePath 为存储桶名或空）使用空前缀，<br>
     * 其余场景保证前缀以 "/" 结尾且不以 "/" 开头（与 S3 公共前缀格式一致）.
     */
    String normalizePrefix(S3DriverConfig cfg, String remotePath) {
        if (!StringUtils.isNotBlank(remotePath) || remotePath.equals(cfg.getBucket())) {
            return "";
        }
        String prefix = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    /**
     * 将 S3 列表条目转换为 ikaros 附件.
     */
    Attachment toAttachment(UUID driverId, UUID pid, S3ObjectEntry entry) {
        if (entry.isDir()) {
            String prefix = entry.getKey();
            String name = parseName(prefix);
            return Attachment.builder()
                .parentId(pid)
                .type(AttachmentType.Driver_Directory)
                .name(name)
                .path(prefix)
                .url("")
                .fsPath(prefix)
                .size(0L)
                .sha1("")
                .updateTime(LocalDateTime.now())
                .deleted(false)
                .driverId(driverId)
                .build();
        }
        String key = entry.getKey();
        String name = parseName(key);
        return Attachment.builder()
            .parentId(pid)
            .type(AttachmentType.Driver_File)
            .name(name)
            .path(key)
            // 对象键作为持久标识，读取/下载/流式时按需生成预签名直链
            .url(key)
            .fsPath(key)
            .size(entry.getSize())
            .sha1(entry.getEtag() == null ? "" : entry.getEtag())
            .updateTime(entry.getLastModified() == null
                ? LocalDateTime.now() : entry.getLastModified())
            .deleted(false)
            .driverId(driverId)
            .build();
    }

    /**
     * 从对象键/公共前缀中解析名称：去掉尾部 "/" 后取最后一段.
     */
    private String parseName(String key) {
        String name = key;
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }
}