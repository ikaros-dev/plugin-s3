package run.ikaros.plugin.s3.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S3 列表返回的单个条目，可能是对象文件或公共前缀（虚拟目录）.
 *
 * @author Nekoli
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3ObjectEntry {

    /**
     * 是否为公共前缀（虚拟目录）.
     */
    private boolean dir;

    /**
     * 对象键或公共前缀.
     */
    private String key;

    /**
     * 对象大小（字节），目录时为 0.
     */
    private Long size;

    /**
     * 对象 ETag，目录时为空.
     */
    private String etag;

    /**
     * 对象最后修改时间，目录时为 null.
     */
    private LocalDateTime lastModified;
}