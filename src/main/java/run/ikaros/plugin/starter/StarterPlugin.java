package run.ikaros.plugin.starter;


import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.ikaros.api.core.task.TaskOperate;
import run.ikaros.api.plugin.BasePlugin;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Component
public class StarterPlugin extends BasePlugin {
    private final TaskOperate taskOperate;

    public StarterPlugin(PluginWrapper wrapper, TaskOperate taskOperate) {
        super(wrapper);
        this.taskOperate = taskOperate;
    }

    @Override
    public void start() {
        log.info("plugin [PluginStarter] start success");
        // submitParallelTask();
    }

    private void submitParallelTask() {
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            taskOperate.submit(getWrapper().getPluginId() + "-parallel-" + i,
                    new Runnable() {
                        @Override
                        public void run() {
                            log.info("Submit task with index:{}", finalI);
                            try {
                                Thread.sleep(new Random().nextLong(0, 5000));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            log.info("Finish task with index:{}", finalI);

                        }
                    })
                    .subscribe();
        }
    }

    @Override
    public void stop() {
        log.info("plugin [PluginStarter] stop success");
    }

    @Override
    public void delete() {
        log.info("plugin [PluginStarter] delete success");
    }
}