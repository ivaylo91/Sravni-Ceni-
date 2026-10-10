package bg.sravniceni.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bg.sravniceni.domain.ImportRun;
import bg.sravniceni.importer.ImportScheduler;
import bg.sravniceni.importer.KzpImportService;
import bg.sravniceni.repository.ImportRunRepository;
import bg.sravniceni.service.ConflictException;

/** Администрация на импорта (изисква ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");

    private final ImportScheduler scheduler;
    private final KzpImportService importService;
    private final ImportRunRepository runs;

    public AdminController(ImportScheduler scheduler, KzpImportService importService, ImportRunRepository runs) {
        this.scheduler = scheduler;
        this.importService = importService;
        this.runs = runs;
    }

    @GetMapping("/imports")
    public List<ImportRun> imports() {
        return runs.findTop30ByOrderByStartedAtDesc();
    }

    /** Стартира импорт във фонов режим. По подразбиране – вчерашната дата. */
    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> startImport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now(SOFIA).minusDays(1);
        if (target.isAfter(LocalDate.now(SOFIA))) {
            throw new IllegalArgumentException("Датата не може да е в бъдещето");
        }
        if (importService.isRunning()) {
            throw new ConflictException("В момента тече друг импорт");
        }
        scheduler.runAsync(target);
        return Map.of("date", target, "message", "Импортът е стартиран");
    }
}
