package bg.sravniceni;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;

import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import bg.sravniceni.domain.ImportRun;
import bg.sravniceni.importer.KzpImportService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Локално стартиране без Supabase: вграден PostgreSQL с постоянни данни в
 * backend/.dev-db (изтрийте папката за чисто начало).
 * <pre>
 *   mvn spring-boot:test-run
 * </pre>
 * Каталогът, търсенето и кошницата работят напълно. За вход и запазени
 * списъци е нужен Supabase Auth (вижте README).
 */
public class DevApplication {

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(".dev-db").toAbsolutePath();
        boolean fresh = !Files.exists(dataDir.resolve("PG_VERSION"));
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(54329)
                .start();
        if (fresh) {
            applyMigrations(pg);
        }
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres") + "&reWriteBatchedInserts=true");
        System.setProperty("spring.datasource.password", "postgres");
        System.setProperty("app.import.enabled", "false");
        var context = SpringApplication.from(SravniCeniApplication::main).run(args).getApplicationContext();

        // Първо стартиране: зареждаме последните налични цени от КЗП
        var jdbc = context.getBean(JdbcTemplate.class);
        if (jdbc.queryForObject("select count(*) from price", Long.class) == 0) {
            var importer = context.getBean(KzpImportService.class);
            String localZip = System.getProperty("kzp.zip");
            LocalDate yesterday = LocalDate.now(ZoneId.of("Europe/Sofia")).minusDays(1);
            if (localZip != null) {
                importer.importLocalZip(Path.of(localZip), LocalDate.parse(System.getProperty("kzp.date", yesterday.toString())));
            } else if (importer.importDate(yesterday).getStatus() != ImportRun.Status.SUCCESS) {
                importer.importDate(yesterday.minusDays(1));
            }
        }
    }

    private static void applyMigrations(EmbeddedPostgres pg) throws IOException, SQLException {
        try (Stream<Path> files = Files.list(Path.of("..", "supabase", "migrations"));
             Connection c = pg.getPostgresDatabase().getConnection()) {
            for (Path file : files.sorted().filter(f -> !f.getFileName().toString().contains("_rls")).toList()) {
                c.createStatement().execute(Files.readString(file));
            }
        }
    }
}
