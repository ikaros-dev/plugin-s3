package run.ikaros.plugin.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import run.ikaros.api.custom.scheme.CustomSchemeManager;

/**
 * 自定义模型注册器：向 ikaros 注册本插件提供的自定义模型.
 *
 * @author Nekoli
 */
@Slf4j
@Component
public class CustomSchemeRegister implements InitializingBean {

    private final CustomSchemeManager customSchemeManager;

    public CustomSchemeRegister(CustomSchemeManager customSchemeManager) {
        this.customSchemeManager = customSchemeManager;
    }

    @Override
    public void afterPropertiesSet() {
        customSchemeManager.register(AttachmentS3Custom.class);
        log.debug("Register custom scheme [AttachmentS3Custom] success.");
    }
}