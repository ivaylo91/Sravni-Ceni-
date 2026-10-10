package bg.sravniceni.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PackageSizeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Прясно Мляко Верея 3%, 1 Л.            | 1     | l",
            "Кисело Мляко Здравец 3.6%, Р 500 Гр.   | 0.5   | kg",
            "ROSО Прясно цяло пиле, ок. 1,5 кг      | 1.5   | kg",
            "Кайма смес ок.750г-атм                 | 0.75  | kg",
            "МАР КРАВЕ СИРЕНЕ ДАВИДОВО 2Х5КГ КОФА   | 10    | kg",
            "Минерална Вода Devin 1. 5 Л.           | 1.5   | l",
            "700МЛ ЯБЪЛКОВ ОЦЕТ VEDA                | 0.7   | l",
            "Лимони на кг                           | 1     | kg",
            "Яйца Медковец М 6Бр. Кутия             | 6     | pcs",
    })
    void parsesPackageSize(String name, BigDecimal qty, String unit) {
        var size = PackageSize.parse(name).orElseThrow();
        assertThat(size.quantity()).isEqualByComparingTo(qty);
        assertThat(size.unit()).isEqualTo(unit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ТАЛЦИД табл. 500мг х 20", "DUNHILL Select Rose 4.30€", "ЯЙЦА M10 ПОДОВО ОТГЛЕЖДАНЕ"})
    void ignoresNamesWithoutPackageSize(String name) {
        assertThat(PackageSize.parse(name)).isEmpty();
    }
}
