package bg.sravniceni.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class KzpChainFileTest {

    @Test
    void parsesChainNameAndEikFromFileName() {
        var header = KzpChainFile.header("T Market (Максима България ЕООД)_131324923.csv").orElseThrow();
        assertThat(header.eik()).isEqualTo("131324923");
        assertThat(header.shortName()).isEqualTo("T Market");
        assertThat(header.name()).isEqualTo("T Market (Максима България ЕООД)");

        assertThat(KzpChainFile.header("Кауфланд България_131129282.csv").orElseThrow().shortName())
                .isEqualTo("Кауфланд България");
        assertThat(KzpChainFile.header("readme.txt")).isEmpty();
    }

    @Test
    void parsesQuotedCommaSeparatedFileWithBom() throws Exception {
        String csv = "﻿\"Населено място\",\"Търговски обект\",\"Наименование на продукта\",\"Код на продукта\",\"Категория\",\"Цена на дребно\",\"Цена в промоция\"\n"
                + "\"68134\",\"гр. София, ул. Суходолска №2\",\"Прясно мляко БУЛГАРЧЕ 3,2% 1л\",\"1353856\",\"6\",\"1.4700\",\"\"\r\n"
                + "\"7079\",\"Бургас\",\"Хляб\",\"1\",\"1\",\"1.20\",\"0.99\"\n";
        var file = parse(csv);

        assertThat(file.rows()).hasSize(2);
        var milk = file.rows().get(0);
        assertThat(milk.address()).isEqualTo("гр. София, ул. Суходолска №2");
        assertThat(milk.productName()).isEqualTo("Прясно мляко БУЛГАРЧЕ 3,2% 1л");
        assertThat(milk.categoryId()).isEqualTo((short) 6);
        assertThat(milk.price()).isEqualByComparingTo("1.47");
        assertThat(milk.promoPrice()).isNull();

        var bread = file.rows().get(1);
        assertThat(bread.settlementCode()).isEqualTo("07079");
        assertThat(bread.promoPrice()).isEqualByComparingTo("0.99");
    }

    @Test
    void toleratesSemicolonsStrayQuotesAndBadRows() throws Exception {
        String csv = "Населено място;Търговски обект;Наименование на продукта;Код на продукта;Категория;Цена на дребно;Цена в промоция\n"
                + "68134-04;Лидл София;Кашкавал \"Витоша 400г;200;11;7,99;\n"   // кавичка в средата на полето
                + "068134;Лидл София;Нещо;201;150;1.00;\n"                      // категория извън 1–101
                + "68134;Лидл София;Мляко;202;6;0;\n"                           // нулева цена
                + "68134;Лидл София;Метро промо;203;6;2.00;0\n";                // промоция 0 = няма промоция
        var file = parse(csv);

        assertThat(file.rows()).extracting(KzpChainFile.Row::productCode).containsExactly("200", "203");
        assertThat(file.skippedRows()).isEqualTo(2);
        assertThat(file.rows().get(0).productName()).isEqualTo("Кашкавал \"Витоша 400г");
        assertThat(file.rows().get(0).settlementCode()).isEqualTo("68134");
        assertThat(file.rows().get(0).price()).isEqualTo(new BigDecimal("7.99"));
        assertThat(file.rows().get(1).promoPrice()).isNull();
    }

    @Test
    void normalizesSettlementCodes() {
        assertThat(KzpChainFile.normalizeSettlement("68134-04")).isEqualTo("68134");
        assertThat(KzpChainFile.normalizeSettlement("068134")).isEqualTo("68134");
        assertThat(KzpChainFile.normalizeSettlement("702")).isEqualTo("00702");
        assertThat(KzpChainFile.normalizeSettlement("abc")).isNull();
    }

    private static KzpChainFile parse(String csv) throws Exception {
        var header = KzpChainFile.header("Тест_123456789.csv").orElseThrow();
        return header.withRows(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }
}
