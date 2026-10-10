package bg.sravniceni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.springframework.test.context.DynamicPropertyRegistry;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Истински PostgreSQL за тестовете (без Docker). Прилагат се миграциите от
 * supabase/migrations, без специфичните за Supabase RLS политики.
 */
public final class PostgresTestSupport {

    private static EmbeddedPostgres postgres;

    private PostgresTestSupport() {
    }

    public static synchronized void register(DynamicPropertyRegistry registry) {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
                applyMigrations();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres") + "&reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    private static void applyMigrations() throws IOException {
        Path dir = Path.of("..", "supabase", "migrations");
        try (Stream<Path> files = Files.list(dir); Connection c = postgres.getPostgresDatabase().getConnection()) {
            for (Path file : files.sorted().filter(f -> !f.getFileName().toString().contains("_rls")).toList()) {
                c.createStatement().execute(Files.readString(file));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Миграциите не можаха да се приложат", e);
        }
    }
}
