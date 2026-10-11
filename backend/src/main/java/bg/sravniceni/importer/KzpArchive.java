package bg.sravniceni.importer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import bg.sravniceni.config.AppProperties;

/**
 * Архив на дневните ZIP файлове на КЗП в Supabase Storage (REST API).
 * kolkostruva.bg пази само последните ~14 дни, затова всеки изтеглен файл
 * се качва тук и при нужда се чете обратно.
 */
@Component
public class KzpArchive {

    private static final Logger log = LoggerFactory.getLogger(KzpArchive.class);

    private final AppProperties.Archive config;
    private final String storageUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public KzpArchive(AppProperties props) {
        this.config = props.archive();
        String base = props.supabase().url();
        this.storageUrl = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/storage/v1/object/";
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** Качва ZIP файла за датата (презаписва съществуващ). Грешките не спират импорта. */
    public void store(LocalDate date, Path zip) {
        if (!enabled()) {
            return;
        }
        try {
            HttpResponse<String> response = http.send(
                    authorized(HttpRequest.newBuilder(objectUri(date)))
                            .header("Content-Type", "application/zip")
                            .header("x-upsert", "true")
                            .timeout(Duration.ofMinutes(5))
                            .POST(HttpRequest.BodyPublishers.ofFile(zip))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("Архивиран {} в Supabase Storage", date);
            } else {
                log.warn("Архивирането на {} не успя: HTTP {} {}", date, response.statusCode(), response.body());
            }
        } catch (IOException e) {
            log.warn("Архивирането на {} не успя", date, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Изтегля архивиран ZIP файл, ако има такъв. */
    public Optional<Path> fetch(LocalDate date) throws IOException {
        if (!enabled()) {
            return Optional.empty();
        }
        Path target = Files.createTempFile("kzp-archive-" + date, ".zip");
        try {
            HttpResponse<Path> response = http.send(
                    authorized(HttpRequest.newBuilder(objectUri(date))).timeout(Duration.ofMinutes(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() == 200) {
                return Optional.of(target);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Files.deleteIfExists(target);
        return Optional.empty();
    }

    private URI objectUri(LocalDate date) {
        return URI.create(storageUrl + config.bucket() + "/" + date + ".zip");
    }

    /**
     * Новите secret ключове (sb_secret_…) се подават само в apikey; старият
     * service_role ключ е JWT и се подава и като Bearer токен.
     */
    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        builder.header("apikey", config.serviceKey());
        if (config.serviceKey().startsWith("eyJ")) {
            builder.header("Authorization", "Bearer " + config.serviceKey());
        }
        return builder;
    }
}
