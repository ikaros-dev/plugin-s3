package run.ikaros.plugin.s3;

import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.ikaros.api.plugin.BasePlugin;

/**
 * S3 对象存储插件入口.
 *
 * @author Nekoli
 */
@Slf4j
@Component
public class S3Plugin extends BasePlugin {

    public S3Plugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("plugin [S3Plugin] start success");
    }

    @Override
    public void stop() {
        log.info("plugin [S3Plugin] stop success");
    }

    @Override
    public void delete() {
        log.info("plugin [S3Plugin] delete success");
    }
}