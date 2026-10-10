package bg.sravniceni.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Един CSV файл от дневния ZIP на КЗП – цените на една верига.
 * Име на файла: „Търговско име (Фирма)_ЕИК.csv“.
 * Колони: Населено място (ЕКАТТЕ), Търговски обект, Наименование на продукта,
 * Код на продукта, Категория (1–101), Цена на дребно, Цена в промоция.
 */
public record KzpChainFile(String eik, String name, String shortName, List<Row> rows, int skippedRows) {

    public record Row(String settlementCode, String address, String productName, String productCode,
                      short categoryId, BigDecimal price, BigDecimal promoPrice) {
    }

    /** Разчита ЕИК и името на веригата от името на файла в архива. */
    public static Optional<KzpChainFile> header(String entryName) {
        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if (!fileName.toLowerCase().endsWith(".csv")) {
            return Optional.empty();
        }
        String base = fileName.substring(0, fileName.length() - 4);
        int underscore = base.lastIndexOf('_');
        if (underscore <= 0) {
            return Optional.empty();
        }
        String eik = base.substring(underscore + 1).trim();
        String name = base.substring(0, underscore).trim();
        if (!eik.matches("\\d{9,13}")) {
            return Optional.empty();
        }
        int bracket = name.indexOf(" (");
        String shortName = bracket > 0 ? name.substring(0, bracket).trim() : name;
        return Optional.of(new KzpChainFile(eik, name, shortName, List.of(), 0));
    }

    /** Парсва съдържанието на файла. Невалидните редове се пропускат и броят. */
    public KzpChainFile withRows(InputStream in) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        reader.mark(4096);
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return this;
        }
        reader.reset();
        if (headerLine.startsWith("﻿")) {
            reader.skip(1);
        }
        var csv = new LenientCsvReader(reader, LenientCsvReader.detectDelimiter(headerLine));
        csv.next(); // заглавен ред

        List<Row> parsed = new ArrayList<>();
        int skipped = 0;
        List<String> f;
        while ((f = csv.next()) != null) {
            if (f.size() == 1 && f.get(0).isBlank()) {
                continue;
            }
            Row row = f.size() >= 6 ? toRow(f) : null;
            if (row == null) {
                skipped++;
            } else {
                parsed.add(row);
            }
        }
        return new KzpChainFile(eik, name, shortName, parsed, skipped);
    }

    private static Row toRow(List<String> f) {
        String settlement = normalizeSettlement(f.get(0));
        String address = truncate(f.get(1).trim(), 500);
        String productName = truncate(f.get(2).trim().replaceAll("\\s+", " "), 500);
        String code = f.get(3).trim();
        Short category = parseCategory(f.get(4));
        BigDecimal price = parsePrice(f.get(5));
        BigDecimal promo = f.size() > 6 ? parsePrice(f.get(6)) : null;
        if (settlement == null || address.isEmpty() || productName.isEmpty() || category == null || price == null) {
            return null;
        }
        if (promo != null && promo.compareTo(price) >= 0) {
            promo = null; // „промоция“, която не е по-ниска от редовната цена
        }
        if (code.isEmpty()) {
            code = "N" + Integer.toHexString(productName.toUpperCase().hashCode());
        }
        return new Row(settlement, address, productName, truncate(code, 64), category, price, promo);
    }

    /** ЕКАТТЕ е 5 цифри; срещат се „68134-04“, „7079“ и „068134“. */
    static String normalizeSettlement(String raw) {
        String s = raw.trim();
        int dash = s.indexOf('-');
        if (dash > 0) {
            s = s.substring(0, dash);
        }
        if (!s.matches("\\d{1,6}")) {
            return null;
        }
        int value = Integer.parseInt(s);
        return value > 99999 ? null : String.format("%05d", value);
    }

    private static Short parseCategory(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1 && value <= 101 ? (short) value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static BigDecimal parsePrice(String raw) {
        String s = raw.trim().replace(',', '.').replace("€", "").replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            return value.signum() > 0 && value.compareTo(BigDecimal.valueOf(100_000)) < 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
