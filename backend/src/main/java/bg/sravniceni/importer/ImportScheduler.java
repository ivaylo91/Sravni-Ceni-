package bg.sravniceni.importer;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import bg.sravniceni.config.AppProperties;
import bg.sravniceni.domain.ImportRun;

/** Ежедневен импорт. Файлът за ден Д се публикува на КЗП в ~00:01 на Д+1. */
@Component
public class ImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportScheduler.class);

    private final KzpImportService importService;
    private final AppProperties.Import config;
    private final JdbcTemplate jdbc;

    public ImportScheduler(KzpImportService importService, AppProperties props, JdbcTemplate jdbc) {
        this.importService = importService;
        this.config = props.importer();
        this.jdbc = jdbc;
    }

    /** Първо стартиране с празна база – зареждаме цените, без да чакаме нощния импорт. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void importIfEmpty() {
        boolean runOnceMode = config.runOnce() != null && !config.runOnce().isBlank();
        if (!config.enabled() || !config.onStartup() || runOnceMode
                || Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from price)", Boolean.class))) {
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneId.of(config.zone())).minusDays(1);
        log.info("Базата е празна – импорт на цените за {}", yesterday);
        if (importService.importDateWithRetry(yesterday).getStatus() != ImportRun.Status.SUCCESS) {
            importService.importDate(yesterday.minusDays(1)); // файлът за вчера може още да не е публикуван
        }
    }

    @Scheduled(cron = "${app.import.cron}", zone = "${app.import.zone}")
    public void daily() {
        if (!config.enabled()) {
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneId.of(config.zone())).minusDays(1);
        log.info("Планиран импорт на цените за {}", yesterday);
        importService.importDateWithRetry(yesterday);
    }

    /** Ръчно пускане от администратор – изпълнява се във фонов режим. */
    @Async
    public void runAsync(LocalDate date) {
        importService.importDate(date);
    }
}
