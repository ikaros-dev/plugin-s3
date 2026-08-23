package run.ikaros.plugin.s3;

/**
 * S3 插件常量定义.
 *
 * @author Nekoli
 */
public interface S3Const {

    /**
     * 驱动名称，用于 ikaros 附件驱动中标识本插件提供的 S3 驱动.
     */
    String DRIVER_NAME = "S3";

    /**
     * 默认 S3 服务端点（AWS S3）.
     */
    String DEFAULT_ENDPOINT = "https://s3.amazonaws.com";

    /**
     * 默认区域.
     */
    String DEFAULT_REGION = "us-east-1";

    /**
     * 默认签名服务名.
     */
    String SERVICE_NAME = "s3";

    /**
     * 列表请求最大返回条目数（S3 ListObjectsV2 单次上限）.
     */
    int LIST_MAX_KEYS = 1000;

    /**
     * 读取/下载直链预签名有效期（秒）.
     */
    int PRE_SIGN_EXPIRE_SECONDS = 3600;

    /**
     * 流式传输预签名有效期（秒）.
     */
    int STREAM_PRE_SIGN_EXPIRE_SECONDS = 900;

    /**
     * 插件仓库主页.
     */
    String HOME_PAGE = "https://ikaros.run";

    /**
     * 插件 GitHub 仓库.
     */
    String REPO_GITHUB_NAME = "ikaros-dev/plugin-s3";

    /**
     * 请求 User-Agent.
     */
    String REST_TEMPLATE_USER_AGENT = REPO_GITHUB_NAME + " (" + HOME_PAGE + ")";
}