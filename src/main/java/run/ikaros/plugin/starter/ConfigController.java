package run.ikaros.plugin.starter;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.ikaros.api.core.setting.ConfigMap;
import run.ikaros.api.custom.ReactiveCustomClient;
import run.ikaros.api.infra.exception.NotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConfigController {
    private final ReactiveCustomClient reactiveCustomClient;
    /**
     * Please be consistent with [name] in the [plugin.yaml] file.
     */
    private static final String pluginName = "PluginStarter";

    public ConfigController(ReactiveCustomClient reactiveCustomClient) {
        this.reactiveCustomClient = reactiveCustomClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> getConfigMap() {
        return reactiveCustomClient.findOne(ConfigMap.class, pluginName)
            .onErrorResume(NotFoundException.class, e -> Mono.empty())
            .doOnSuccess(cm -> log.info("find config map: [{}].", cm))
            .then();
    }
}
