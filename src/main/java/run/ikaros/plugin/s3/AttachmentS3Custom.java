package run.ikaros.plugin.s3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.ikaros.api.custom.Custom;
import run.ikaros.api.custom.Name;

/**
 * S3 附件自定义数据：记录附件对应的 S3 对象信息，供控制台展示与检索.
 *
 * @author Nekoli
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Custom(group = "run.ikaros.plugin.s3", version = "v1",
    kind = "AttachmentS3Custom", singular = "attachment_s3", plural = "attachment_s3s")
public class AttachmentS3Custom {

    /**
     * 附件标题（名称）.
     */
    @Name
    private String title;

    /**
     * 附件 ID.
     */
    private Long attId;

    /**
     * S3 对象键.
     */
    private String key;

    /**
     * S3 对象 ETag.
     */
    private String etag;

    /**
     * 对象内容类型.
     */
    private String contentType;
}