package bg.sravniceni.importer;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import bg.sravniceni.config.AppProperties;

/** Ежедневен импорт. Файлът за ден Д се публикува на КЗП в ~00:01 на Д+1. */
@Component
public class ImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportScheduler.class);

    private final KzpImportService importService;
    private final AppProperties.Import config;

    public ImportScheduler(KzpImportService importService, AppProperties props) {
        this.importService = importService;
        this.config = props.importer();
    }

    @Scheduled(cron = "${app.import.cron}", zone = "${app.import.zone}")
    public void daily() {
        if (!config.enabled()) {
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneId.of(config.zone())).minusDays(1);
        log.info("Планиран импорт на цените за {}", yesterday);
        importService.importDate(yesterday);
    }

    /** Ръчно пускане от администратор – изпълнява се във фонов режим. */
    @Async
    public void runAsync(LocalDate date) {
        importService.importDate(date);
    }
}
