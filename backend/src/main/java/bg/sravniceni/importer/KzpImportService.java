package bg.sravniceni.importer;

import static java.sql.Types.BIGINT;
import static java.sql.Types.DATE;
import static java.sql.Types.NUMERIC;
import static java.sql.Types.SMALLINT;
import static java.sql.Types.VARCHAR;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import bg.sravniceni.config.AppProperties;
import bg.sravniceni.domain.ImportRun;
import bg.sravniceni.repository.ImportRunRepository;
import bg.sravniceni.service.ConflictException;

/**
 * Импортира дневните отворени данни на КЗП: https://kolkostruva.bg/opendata_files/ГГГГ-ММ-ДД.zip
 * <p>
 * Всяка верига се записва в отделна транзакция с пакетни (batch) UPSERT заявки:
 * магазини → продукти → цени → дневна история. В таблица price остава само
 * последната цена за всеки продукт в магазин.
 */
@Service
public class KzpImportService {

    private static final Logger log = LoggerFactory.getLogger(KzpImportService.class);
    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ImportRunRepository runs;
    private final AppProperties.Import config;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final KzpArchive archive;
    private final CacheManager caches;

    public KzpImportService(JdbcTemplate jdbc, TransactionTemplate tx, ImportRunRepository runs, AppProperties props,
                            KzpArchive archive, CacheManager caches) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.runs = runs;
        this.config = props.importer();
        this.archive = archive;
        this.caches = caches;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Импортира данните за дадена дата. Източници по ред: локална папка с архив,
     * kolkostruva.bg (и архивиране в Supabase Storage), архивът в Supabase Storage.
     */
    public ImportRun importDate(LocalDate date) {
        return runExclusive(date, () -> {
            Path local = localArchiveFile(date);
            if (local != null) {
                archive.store(date, local);
                return importZip(local, date);
            }
            Path zip;
            try {
                zip = download(date);
                archive.store(date, zip);
            } catch (IOException e) {
                zip = archive.fetch(date).orElseThrow(() -> e);
                log.info("{} не е наличен на КЗП – използван е архивът", date);
            }
            try {
                return importZip(zip, date);
            } finally {
                Files.deleteIfExists(zip);
            }
        });
    }

    private Path localArchiveFile(LocalDate date) {
        if (config.localArchive() == null || config.localArchive().isBlank()) {
            return null;
        }
        Path file = Path.of(config.localArchive(), date + ".zip");
        return Files.isRegularFile(file) ? file : null;
    }

    /**
     * Импорт с един повторен опит след пауза. Импортът е идемпотентен, а връзката
     * през Supabase pooler-а понякога прекъсва (напр. „Unable to rollback against
     * JDBC Connection“) – вторият опит обикновено минава.
     */
    public ImportRun importDateWithRetry(LocalDate date) {
        ImportRun run = importDate(date);
        if (run.getStatus() == ImportRun.Status.FAILED) {
            log.warn("Импортът за {} се провали ({}) – нов опит след 15 секунди", date, run.getMessage());
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return run;
            }
            run = importDate(date);
        }
        return run;
    }

    /** Импортира вече изтеглен ZIP файл (използва се и в тестовете). */
    public ImportRun importLocalZip(Path zip, LocalDate date) {
        return runExclusive(date, () -> importZip(zip, date));
    }

    private record Summary(int chains, int rows, String warnings) {
    }

    private interface ImportJob {
        Summary run() throws IOException;
    }

    private ImportRun runExclusive(LocalDate date, ImportJob job) {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("В момента тече друг импорт");
        }
        ImportRun run = runs.save(ImportRun.start(date));
        try {
            Summary result = job.run();
            run.succeed(result.chains(), result.rows(), result.warnings());
            log.info("Импорт за {} завърши: {} вериги, {} цени", date, result.chains(), result.rows());
            caches.getCacheNames().forEach(name -> {
                Cache cache = caches.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        } catch (Exception e) {
            log.error("Импортът за {} се провали", date, e);
            run.fail(e.getMessage());
        } finally {
            running.set(false);
        }
        return runs.save(run);
    }

    private Path download(LocalDate date) throws IOException {
        URI uri = URI.create(config.baseUrl() + "/" + date + ".zip");
        Path target = Files.createTempFile("kzp-" + date, ".zip");
        try {
            HttpResponse<Path> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                throw new IOException("Няма данни за " + date + " (HTTP " + response.statusCode() + " от " + uri + ")");
            }
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Изтеглянето беше прекъснато", e);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    private Summary importZip(Path zip, LocalDate date) throws IOException {
        int chains = 0;
        int rows = 0;
        List<String> warnings = new ArrayList<>();
        try (var zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            Set<String> duplicated = duplicatedContent(zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Optional<KzpChainFile> header = entry.isDirectory() ? Optional.empty() : KzpChainFile.header(entry.getName());
                if (header.isEmpty() || !config.includesChain(header.get().eik())) {
                    continue;
                }
                if (duplicated.contains(entry.getName())) {
                    // Случва се КЗП да публикува файла на една верига под името на друга.
                    // Тогава не знаем кой е верният, затова пазим цените от предишния ден.
                    warnings.add(header.get().shortName() + ": файлът е идентичен с файла на друга верига и е пропуснат");
                    log.warn("{}: съдържанието съвпада с друг файл в архива – пропуснат", entry.getName());
                    continue;
                }
                KzpChainFile file;
                try (InputStream in = zipFile.getInputStream(entry)) {
                    file = header.get().withRows(in);
                }
                if (file.rows().isEmpty()) {
                    warnings.add(header.get().shortName() + ": няма валидни редове");
                    continue;
                }
                Integer imported = tx.execute(status -> importChain(file, date));
                rows += imported == null ? 0 : imported;
                chains++;
                log.info("{}: {} цени ({} пропуснати реда)", file.shortName(), imported, file.skippedRows());
            }
        }
        jdbc.update("delete from price_history where price_date < ?", Date.valueOf(date.minusDays(config.historyDays())));
        return new Summary(chains, rows, warnings.isEmpty() ? null : String.join("\n", warnings));
    }

    /** Имената на файловете в архива, чието съдържание се повтаря в друг файл. */
    private static Set<String> duplicatedContent(ZipFile zipFile) throws IOException {
        Map<String, List<String>> byHash = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            try (InputStream in = new DigestInputStream(zipFile.getInputStream(entry), sha256())) {
                in.transferTo(OutputStream.nullOutputStream());
                String hash = HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
                byHash.computeIfAbsent(hash, h -> new ArrayList<>()).add(entry.getName());
            }
        }
        Set<String> duplicated = new HashSet<>();
        byHash.values().stream().filter(names -> names.size() > 1).forEach(duplicated::addAll);
        return duplicated;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private int importChain(KzpChainFile file, LocalDate date) {
        Date sqlDate = Date.valueOf(date);
        Long chainId = jdbc.queryForObject("""
                insert into chain (eik, name, short_name) values (?, ?, ?)
                on conflict (eik) do update set name = excluded.name
                returning id
                """, Long.class, file.eik(), file.name(), file.shortName());
        // По-стара дата от вече заредената (попълване на история) – текущите цени не се пипат
        Date latest = jdbc.queryForObject("""
                select max(pr.price_date) from price pr join store s on s.id = pr.store_id where s.chain_id = ?
                """, Date.class, chainId);
        boolean historyOnly = latest != null && latest.toLocalDate().isAfter(date);

        // 1. Населени места, които още не са в справочника
        List<Object[]> settlements = file.rows().stream().map(KzpChainFile.Row::settlementCode).distinct()
                .map(code -> new Object[] {code, "Населено място " + code}).toList();
        batch("insert into settlement (code, name) values (?, ?) on conflict do nothing", settlements, VARCHAR, VARCHAR);

        // 2. Магазини
        Map<String, Object[]> stores = new LinkedHashMap<>();
        for (var r : file.rows()) {
            stores.putIfAbsent(storeKey(r.settlementCode(), r.address()), new Object[] {chainId, r.settlementCode(), r.address()});
        }
        batch("""
                insert into store (chain_id, settlement_code, address) values (?, ?, ?)
                on conflict (chain_id, settlement_code, address) do nothing
                """, new ArrayList<>(stores.values()), BIGINT, VARCHAR, VARCHAR);
        Map<String, Long> storeIds = new HashMap<>();
        Map<Long, String> storeSettlement = new HashMap<>();
        jdbc.query("select id, settlement_code, address from store where chain_id = ?",
                rs -> {
                    storeIds.put(storeKey(rs.getString(2), rs.getString(3)), rs.getLong(1));
                    storeSettlement.put(rs.getLong(1), rs.getString(2));
                }, chainId);

        // 3. Продукти (при повтарящ се код печели първото име)
        Map<String, Object[]> products = new LinkedHashMap<>();
        for (var r : file.rows()) {
            products.computeIfAbsent(r.productCode(), code -> {
                Optional<PackageSize> size = PackageSize.parse(r.productName());
                return new Object[] {chainId, code, r.productName(), r.categoryId(),
                        size.map(PackageSize::quantity).orElse(null), size.map(PackageSize::unit).orElse(null)};
            });
        }
        // При стара дата не презаписваме по-новите имена на продуктите
        String onConflict = historyOnly ? "do nothing" : """
                do update
                   set name = excluded.name, category_id = excluded.category_id,
                       package_qty = excluded.package_qty, package_unit = excluded.package_unit
                 where (product.name, product.category_id, product.package_qty, product.package_unit)
                       is distinct from (excluded.name, excluded.category_id, excluded.package_qty, excluded.package_unit)
                """;
        batch("""
                insert into product (chain_id, code, name, category_id, package_qty, package_unit)
                values (?, ?, ?, ?, ?, ?)
                on conflict (chain_id, code)
                """ + onConflict,
                new ArrayList<>(products.values()), BIGINT, VARCHAR, VARCHAR, SMALLINT, NUMERIC, VARCHAR);
        Map<String, Long> productIds = new HashMap<>();
        jdbc.query("select id, code from product where chain_id = ?",
                rs -> {
                    productIds.put(rs.getString(2), rs.getLong(1));
                }, chainId);

        // 4. Цени – по един ред на (магазин, продукт), най-ниската при дублирани редове
        Map<String, Object[]> prices = new HashMap<>();
        for (var r : file.rows()) {
            Long storeId = storeIds.get(storeKey(r.settlementCode(), r.address()));
            Long productId = productIds.get(r.productCode());
            if (storeId == null || productId == null) {
                continue;
            }
            prices.merge(storeId + ":" + productId,
                    new Object[] {storeId, productId, r.price(), r.promoPrice(), sqlDate},
                    (a, b) -> effective(b).compareTo(effective(a)) < 0 ? b : a);
        }
        if (!historyOnly) {
            batch("""
                    insert into price (store_id, product_id, price, promo_price, price_date) values (?, ?, ?, ?, ?)
                    on conflict (store_id, product_id) do update
                       set price = excluded.price, promo_price = excluded.promo_price, price_date = excluded.price_date
                     where price.price_date <= excluded.price_date
                    """, new ArrayList<>(prices.values()), BIGINT, BIGINT, NUMERIC, NUMERIC, DATE);

            // 5. Продукти, които вече не се предлагат в даден магазин
            jdbc.update("""
                    delete from price pr using store s
                    where s.id = pr.store_id and s.chain_id = ? and pr.price_date < ?
                    """, chainId, sqlDate);
        }

        // 6. Дневна история по населено място – изчислена от самия файл,
        //    за да работи и при попълване на минали дни
        Map<String, BigDecimal[]> history = new HashMap<>(); // ключ → [min, сума, брой]
        Map<String, Object[]> historyKeys = new HashMap<>();
        for (Object[] p : prices.values()) {
            Long storeId = (Long) p[0];
            Long productId = (Long) p[1];
            String settlement = storeSettlement.get(storeId);
            BigDecimal eff = effective(p);
            String key = productId + ":" + settlement;
            historyKeys.putIfAbsent(key, new Object[] {productId, settlement});
            history.merge(key, new BigDecimal[] {eff, eff, BigDecimal.ONE}, (a, b) -> new BigDecimal[] {
                    a[0].min(b[0]), a[1].add(b[1]), a[2].add(b[2])});
        }
        List<Object[]> historyRows = new ArrayList<>();
        history.forEach((key, agg) -> {
            Object[] k = historyKeys.get(key);
            historyRows.add(new Object[] {k[0], k[1], sqlDate, agg[0], agg[1].divide(agg[2], 2, RoundingMode.HALF_UP)});
        });
        batch("""
                insert into price_history (product_id, settlement_code, price_date, min_price, avg_price)
                values (?, ?, ?, ?, ?)
                on conflict (product_id, settlement_code, price_date) do update
                   set min_price = excluded.min_price, avg_price = excluded.avg_price
                """, historyRows, BIGINT, VARCHAR, DATE, NUMERIC, NUMERIC);
        return prices.size();
    }

    private void batch(String sql, List<Object[]> args, int... sqlTypes) {
        for (int i = 0; i < args.size(); i += BATCH) {
            jdbc.batchUpdate(sql, args.subList(i, Math.min(i + BATCH, args.size())), sqlTypes);
        }
    }

    private static BigDecimal effective(Object[] priceRow) {
        return priceRow[3] != null ? (BigDecimal) priceRow[3] : (BigDecimal) priceRow[2];
    }

    private static String storeKey(String settlement, String address) {
        return settlement + "|" + address;
    }
}
