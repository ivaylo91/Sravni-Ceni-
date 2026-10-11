package bg.sravniceni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import bg.sravniceni.domain.ImportRun;
import bg.sravniceni.importer.KzpImportService;

/**
 * Пълен сценарий: импорт на дневен ZIP → търсене → най-евтина кошница →
 * запазени списъци със Spring Security.
 */
@SpringBootTest(properties = {"app.import.enabled=false", "app.import.chains="})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {

    private static final LocalDate DAY_1 = LocalDate.now().minusDays(2);
    private static final LocalDate DAY_2 = LocalDate.now().minusDays(1);
    private static final String HEADER = "\"Населено място\",\"Търговски обект\",\"Наименование на продукта\","
            + "\"Код на продукта\",\"Категория\",\"Цена на дребно\",\"Цена в промоция\"\n";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestSupport.register(registry);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    KzpImportService importService;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    void importFixture(@TempDir Path dir) throws IOException {
        // Ден 1: има „Стар продукт“, който изчезва на ден 2
        Path day1 = zip(dir.resolve("day1.zip"), Map.of(
                "Кауфланд България_131129282.csv", HEADER
                        + "\"68134\",\"Кауфланд София 1\",\"Стар продукт\",\"999\",\"6\",\"9.99\",\"\"\n"
                        + "\"68134\",\"Кауфланд София 1\",\"Прясно мляко Верея 3% 1л\",\"100\",\"6\",\"1.99\",\"\"\n"));
        assertThat(importService.importLocalZip(day1, DAY_1).getStatus()).isEqualTo(ImportRun.Status.SUCCESS);

        var files = new LinkedHashMap<String, String>();
        files.put("Кауфланд България_131129282.csv", "﻿" + HEADER
                + "\"68134\",\"Кауфланд София 1\",\"Прясно мляко Верея 3% 1л\",\"100\",\"6\",\"1.89\",\"\"\n"
                + "\"68134\",\"Кауфланд София 2\",\"Прясно мляко Верея 3% 1л\",\"100\",\"6\",\"1.79\",\"1.59\"\n"
                + "\"68134\",\"Кауфланд София 1\",\"Хляб Добруджа 650г\",\"101\",\"2\",\"1.20\",\"\"\n"
                + "\"68134\",\"Кауфланд София 2\",\"Хляб Добруджа 650г\",\"101\",\"2\",\"1.20\",\"\"\n"
                + "\"68134\",\"Кауфланд София 1\",\"Банани на кг\",\"102\",\"52\",\"2.49\",\"\"\n"
                + "\"56784\",\"Кауфланд Пловдив\",\"Банани на кг\",\"102\",\"52\",\"2.29\",\"\"\n"
                + "\"68134\",\"Кауфланд София 1\",\"Олио Бисер 3 л\",\"103\",\"42\",\"6.30\",\"\"\n");
        files.put("Лидл България_131071587.csv", "Населено място;Търговски обект;Наименование на продукта;"
                + "Код на продукта;Категория;Цена на дребно;Цена в промоция\n"
                + "68134;Лидл София;Прясно мляко Верея 3% 1 Л.;5001;6;1,69;\n"
                + "68134;Лидл София;Хляб Добруджа 500 г;5002;2;1,10;\n"
                + "68134;Лидл София;Кашкавал \"Витоша 400г;5003;11;7,99;\n"
                + "68134;Лидл София;Невалидна категория;5004;150;1,00;\n"
                + "68134;Лидл София;Олио Бисер 1 л;5005;42;2,00;\n"
                + "68134;Лидл София;Олио Бисер 5 л;5006;42;8,50;\n");
        files.put("Нова верига (Нова ЕООД)_123456789.csv", HEADER
                + "\"12345\",\"Магазин Село\",\"Прясно мляко\",\"1\",\"6\",\"1.50\",\"\"\n");
        Path day2 = zip(dir.resolve("day2.zip"), files);
        ImportRun run = importService.importLocalZip(day2, DAY_2);
        assertThat(run.getStatus()).isEqualTo(ImportRun.Status.SUCCESS);
        assertThat(run.getChains()).isEqualTo(3);
    }

    @Test
    void importKeepsOnlyLatestPricesAndBuildsHistory() {
        assertThat(jdbc.queryForObject("select count(*) from price pr join product p on p.id = pr.product_id "
                + "where p.code = '999'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select distinct price_date from price_history", java.sql.Date.class))
                .extracting(java.sql.Date::toLocalDate).contains(DAY_1, DAY_2);
        assertThat(jdbc.queryForObject("select name from settlement where code = '12345'", String.class))
                .isEqualTo("Населено място 12345");
        assertThat(jdbc.queryForObject("select short_name from chain where eik = '123456789'", String.class))
                .isEqualTo("Нова верига");
        // Разфасовката е разпозната от името; „Банани на кг“ е стока на тегло
        assertThat(jdbc.queryForObject("select package_unit from product where code = '102'", String.class)).isEqualTo("kg");
        assertThat(jdbc.queryForObject("select package_qty from product where code = '102'", java.math.BigDecimal.class)).isNull();
    }

    @Test
    void skipsChainFilesThatAreCopiesOfAnotherChain(@TempDir Path dir) throws IOException {
        // Реален случай от 08–09.10.2026: файлът на Кауфланд съдържа цените на Lilly Drogerie
        String copy = HEADER + "\"68134\",\"ЛИЛИ ДРОГЕРИЕ София\",\"Шампоан\",\"1\",\"82\",\"5.99\",\"\"\n";
        var files = new LinkedHashMap<String, String>();
        files.put("Кауфланд България_131129282.csv", copy);
        files.put("Lilly Drogerie (Лили Дрогерие ЕООД)_201035329.csv", copy);

        ImportRun run = importService.importLocalZip(zip(dir.resolve("dup.zip"), files), DAY_1.minusDays(5));

        assertThat(run.getStatus()).isEqualTo(ImportRun.Status.SUCCESS);
        assertThat(run.getChains()).isZero();
        assertThat(run.getMessage()).contains("Кауфланд България: файлът е идентичен");
        assertThat(jdbc.queryForObject("select count(*) from product where name = 'Шампоан'", Integer.class)).isZero();
    }

    @Test
    void listsSettlementsChainsAndCategories() throws Exception {
        mvc.perform(get("/api/public/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("София"))
                .andExpect(jsonPath("$[0].storeCount").value(3));
        mvc.perform(get("/api/public/chains").param("settlement", "68134"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Kaufland")))
                .andExpect(jsonPath("$[*].name", hasItem("Lidl")));
        mvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[1].name").value("Мляко и млечни"))
                .andExpect(jsonPath("$[1].categories[0].name").value("Прясно мляко"));
    }

    @Test
    void searchFindsCheapestOfferPerChainIgnoringCaseAndWordOrder() throws Exception {
        mvc.perform(get("/api/public/products").param("q", "ВЕРЕЯ мляко").param("settlement", "68134"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].chainName").value("Kaufland"))
                .andExpect(jsonPath("$.items[0].price").value(1.59))
                .andExpect(jsonPath("$.items[0].regularPrice").value(1.79))
                .andExpect(jsonPath("$.items[0].promo").value(true))
                .andExpect(jsonPath("$.items[0].storeCount").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1.59))
                .andExpect(jsonPath("$.items[0].unit").value("l"))
                .andExpect(jsonPath("$.items[1].chainName").value("Lidl"));
    }

    @Test
    void searchRequiresTextOrCategoryAndValidatesSettlement() throws Exception {
        mvc.perform(get("/api/public/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Въведете продукт за търсене или изберете категория"));
        mvc.perform(get("/api/public/products").param("q", "мляко").param("settlement", "abc"))
                .andExpect(status().isBadRequest());
        // LIKE специалните символи се третират буквално
        mvc.perform(get("/api/public/products").param("q", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void productDetailShowsStoresHistoryAndAlternatives() throws Exception {
        Long id = jdbc.queryForObject("select id from product where code = '100'", Long.class);
        mvc.perform(get("/api/public/products/{id}", id).param("settlement", "68134"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainName").value("Kaufland"))
                .andExpect(jsonPath("$.categoryName").value("Прясно мляко"))
                .andExpect(jsonPath("$.stores.length()").value(2))
                .andExpect(jsonPath("$.stores[0].promoPrice").value(1.59))
                .andExpect(jsonPath("$.history.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.alternatives[0].chainName").value("Lidl"));
        mvc.perform(get("/api/public/products/{id}", 987654)).andExpect(status().isNotFound());
    }

    @Test
    void basketFindsCheapestStoreChainAndSplit() throws Exception {
        String body = """
                {"settlementCode":"68134","items":[
                  {"categoryId":6,"quantity":2},
                  {"categoryId":2,"keyword":"добруджа","quantity":1},
                  {"categoryId":52,"quantity":1}]}
                """;
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // Само Кауфланд София 1 има всичките 3 продукта
                .andExpect(jsonPath("$.bestStores[0].address").value("Кауфланд София 1"))
                .andExpect(jsonPath("$.bestStores[0].foundCount").value(3))
                .andExpect(jsonPath("$.bestStores[0].total").value(7.47))
                // При равно покритие печели по-ниската сума: Кауфланд София 2 (4.38) пред Лидл (4.48)
                .andExpect(jsonPath("$.bestStores[1].address").value("Кауфланд София 2"))
                .andExpect(jsonPath("$.bestStores[1].total").value(4.38))
                .andExpect(jsonPath("$.bestStores[2].chainName").value("Lidl"))
                .andExpect(jsonPath("$.bestStores[2].missingItems[0]").value(2))
                .andExpect(jsonPath("$.bestPerChain.length()").value(2))
                // Разделено пазаруване: мляко (Кауфланд 2) + хляб (Лидл) + банани (Кауфланд 1)
                .andExpect(jsonPath("$.split.total").value(6.77))
                .andExpect(jsonPath("$.split.storeCount").value(3));
    }

    @Test
    void basketByAmountComparesTheCostOfReachingTheAmount() throws Exception {
        // 5 л олио: Лидл 1 × 5 л = 8.50 € (по-евтино от 5 × 1 л = 10 €), Кауфланд 2 × 3 л = 12.60 €
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[{\"categoryId\":42,\"quantity\":1,\"amount\":5,\"unit\":\"l\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestPerChain[0].chainName").value("Lidl"))
                .andExpect(jsonPath("$.bestPerChain[0].total").value(8.50))
                .andExpect(jsonPath("$.bestPerChain[0].lines[0].productName").value("Олио Бисер 5 л"))
                .andExpect(jsonPath("$.bestPerChain[0].lines[0].quantity").value(1))
                .andExpect(jsonPath("$.bestPerChain[0].lines[0].packageQty").value(5))
                .andExpect(jsonPath("$.bestPerChain[1].chainName").value("Kaufland"))
                .andExpect(jsonPath("$.bestPerChain[1].lines[0].quantity").value(2))
                .andExpect(jsonPath("$.bestPerChain[1].total").value(12.60));

        // Стока на тегло: 1.5 кг банани × 2.49 €/кг = 3.74 € (не 2 „опаковки“)
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[{\"categoryId\":52,\"quantity\":1,\"amount\":1.5,\"unit\":\"kg\"}]}"))
                .andExpect(jsonPath("$.bestPerChain[0].total").value(3.74))
                .andExpect(jsonPath("$.bestPerChain[0].lines[0].packageUnit").value("kg"));

        // По брой опаковки: най-евтината единична опаковка
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[{\"categoryId\":42,\"keyword\":\"бисер\",\"quantity\":1}]}"))
                .andExpect(jsonPath("$.bestPerChain[0].lines[0].productName").value("Олио Бисер 1 л"))
                .andExpect(jsonPath("$.bestPerChain[0].total").value(2.00));

        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[{\"categoryId\":42,\"quantity\":1,\"amount\":5,\"unit\":\"pcs\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Количеството трябва да е между 0,01 и 100 кг или л"));
    }

    @Test
    void backfillingAnOlderDayAddsHistoryWithoutTouchingCurrentPrices(@TempDir Path dir) throws Exception {
        LocalDate older = DAY_1.minusDays(3);
        Path zip = zip(dir.resolve("old.zip"), Map.of("Кауфланд България_131129282.csv", HEADER
                + "\"68134\",\"Кауфланд София 1\",\"Прясно мляко Верея СТАРО ИМЕ\",\"100\",\"6\",\"9.99\",\"\"\n"));

        assertThat(importService.importLocalZip(zip, older).getStatus()).isEqualTo(ImportRun.Status.SUCCESS);

        assertThat(jdbc.queryForObject("select name from product where code = '100'", String.class))
                .isEqualTo("Прясно мляко Верея 3% 1л");
        assertThat(jdbc.queryForList("select pr.price from price pr join product p on p.id = pr.product_id "
                + "where p.code = '100' order by pr.price", java.math.BigDecimal.class))
                .extracting(java.math.BigDecimal::toPlainString).containsExactly("1.79", "1.89");
        assertThat(jdbc.queryForObject("select h.min_price from price_history h join product p on p.id = h.product_id "
                + "where p.code = '100' and h.price_date = ?", java.math.BigDecimal.class, java.sql.Date.valueOf(older)))
                .isEqualByComparingTo("9.99");
    }

    @Test
    void savedListsKeepAmounts() throws Exception {
        UUID user = UUID.randomUUID();
        mvc.perform(withUser(post("/api/lists"), user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Олио\",\"items\":[{\"categoryId\":42,\"quantity\":1,\"amount\":2.5,\"unit\":\"l\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].amount").value(2.5))
                .andExpect(jsonPath("$.items[0].unit").value("l"));
    }

    @Test
    void basketValidatesInput() throws Exception {
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[{\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Изберете категория или въведете ключова дума"));
        mvc.perform(post("/api/public/basket").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCode\":\"68134\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shoppingListsRequireLoginAndAreIsolatedPerUser() throws Exception {
        mvc.perform(get("/api/lists")).andExpect(status().isUnauthorized());

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String json = mvc.perform(withUser(post("/api/lists"), alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Седмично\",\"items\":[{\"categoryId\":6,\"quantity\":2},{\"keyword\":\"банани\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(withUser(get("/api/lists"), alice))
                .andExpect(jsonPath("$[0].name").value("Седмично"));
        mvc.perform(withUser(get("/api/lists"), bob))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withUser(get("/api/lists/{id}", id), bob)).andExpect(status().isNotFound());
        mvc.perform(withUser(delete("/api/lists/{id}", id), bob)).andExpect(status().isNotFound());

        mvc.perform(withUser(put("/api/lists/{id}", id), alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Уикенд\",\"items\":[{\"categoryId\":52,\"keyword\":\"\",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Уикенд"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mvc.perform(withUser(delete("/api/lists/{id}", id), alice)).andExpect(status().isNoContent());
        mvc.perform(withUser(get("/api/lists/{id}", id), alice)).andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mvc.perform(withUser(get("/api/admin/imports"), UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request, UUID userId) {
        return request.with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "test@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static Path zip(Path target, Map<String, String> files) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            for (var e : files.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return target;
    }
}
