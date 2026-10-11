package bg.sravniceni.importer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import bg.sravniceni.config.AppProperties;
import bg.sravniceni.domain.ImportRun;

/**
 * Еднократен импорт и изход – за GitHub Actions и за попълване на история:
 * <pre>
 *   java -jar app.jar --spring.main.web-application-type=none --app.import.run-once=yesterday
 *   java -jar app.jar ... --app.import.run-once=2026-09-27..2026-10-09
 * </pre>
 * Изходен код 1, ако някоя дата не е импортирана успешно.
 */
@Component
public class RunOnceImport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunOnceImport.class);

    private final KzpImportService importService;
    private final AppProperties.Import config;
    private final ConfigurableApplicationContext context;

    public RunOnceImport(KzpImportService importService, AppProperties props, ConfigurableApplicationContext context) {
        this.importService = importService;
        this.config = props.importer();
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (config.runOnce() == null || config.runOnce().isBlank()) {
            return;
        }
        List<LocalDate> dates = parse(config.runOnce(), LocalDate.now(ZoneId.of(config.zone())));
        int failed = 0;
        for (LocalDate date : dates) {
            ImportRun run = importService.importDateWithRetry(date);
            if (run.getStatus() != ImportRun.Status.SUCCESS) {
                failed++;
                log.error("{}: {}", date, run.getMessage());
            }
        }
        log.info("Еднократен импорт: {} дати, {} неуспешни", dates.size(), failed);
        int exitCode = failed == 0 ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** "yesterday", "ГГГГ-ММ-ДД" или "ГГГГ-ММ-ДД..ГГГГ-ММ-ДД" (винаги от най-старата дата). */
    static List<LocalDate> parse(String spec, LocalDate today) {
        String value = spec.trim();
        if (value.equalsIgnoreCase("yesterday")) {
            return List.of(today.minusDays(1));
        }
        String[] parts = value.split("\\.\\.");
        LocalDate from = LocalDate.parse(parts[0].trim());
        LocalDate to = parts.length > 1 ? LocalDate.parse(parts[1].trim()) : from;
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Невалиден период: " + spec);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }
}
