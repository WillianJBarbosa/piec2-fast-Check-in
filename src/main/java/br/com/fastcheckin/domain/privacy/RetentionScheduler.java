package br.com.fastcheckin.domain.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Roda a retenção todo dia (horário em {@code app.privacy.retention-cron}). */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.privacy.retention-cron:0 0 3 * * *}")
    public void run() {
        int anonymized = retentionService.anonymizeExpired();
        if (anonymized > 0) {
            log.info("Retenção: {} hóspede(s) anonimizado(s).", anonymized);
        }
    }
}
