package devops.pipeline.service;

import devops.pipeline.dao.PipelineRunDao;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时 Worker 只领取排队运行；执行细节完全委托给 PipelineRunService 和已注册插件。 */
@Component
@EnableScheduling
public class PipelineDispatchScheduler {
    private static final int BATCH_SIZE = 10;
    private final PipelineRunDao runDao;
    private final PipelineRunService runService;

    public PipelineDispatchScheduler(PipelineRunDao runDao, PipelineRunService runService) {
        this.runDao = runDao;
        this.runService = runService;
    }

    @Scheduled(fixedDelayString = "${app.pipeline.dispatch-delay-ms:1000}")
    public void dispatchQueuedRuns() {
        try {
            runDao.queued(BATCH_SIZE).forEach(run -> runService.dispatch(run.id()));
        } catch (DataAccessException exception) {
            // H2 本地测试和新实例启动时 Schema 可能仍在初始化；下一轮轮询会在迁移完成后继续领取任务。
        }
    }
}
