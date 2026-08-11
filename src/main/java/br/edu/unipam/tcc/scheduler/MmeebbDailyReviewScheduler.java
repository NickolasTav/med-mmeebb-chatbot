package br.edu.unipam.tcc.scheduler;

import br.edu.unipam.tcc.service.ReviewSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MmeebbDailyReviewScheduler {

    private final ReviewSchedulerService reviewSchedulerService;

    @Scheduled(cron = "${scheduling.mmeebb-cron:0 0 8 * * *}")
    public void executeDailyReviewDispatch() {
        log.info("Trigger agendado disparado: executando rotina de revisões MMEEBB...");
        reviewSchedulerService.executeDailyScheduledDispatches();
    }
}
