package bg.sravniceni.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разпознава разфасовката от името на продукта, напр. „Прясно мляко 3% 1 Л.“,
 * „Кайма ок.750г“, „Сирене 2Х5КГ“, „Банани на кг“, за да изчисли цена за кг/л.
 *
 * @param quantity количество в базова мярка (кг, л или брой); null при стока на тегло
 * @param unit     "kg", "l" или "pcs"
 */
public record PackageSize(BigDecimal quantity, String unit) {

    /** Стока на тегло („Банани на кг“) – цената е за 1 кг и се купува произволно количество. */
    public boolean byWeight() {
        return quantity == null;
    }

    private static final String NUMBER = "(\\d+(?:[.,]\\s?\\d+)?)";
    private static final String UNIT = "(кг|kg|гр|gr|г|g|мл|ml|л|l|бр)";
    private static final String END = "(?![\\p{L}\\d])";

    private static final Pattern MULTIPACK = Pattern.compile(
            "(\\d+)\\s*[xх*]\\s*" + NUMBER + "\\s*" + UNIT + "\\.?" + END,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SINGLE = Pattern.compile(
            NUMBER + "\\s*" + UNIT + "\\.?" + END,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** „на кг“, „кг.“ без число – стока на тегло, цената е за 1 кг. */
    private static final Pattern PER_KG = Pattern.compile(
            "(?<![\\p{L}\\d])(?:кг|kg)\\.?" + END, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static Optional<PackageSize> parse(String productName) {
        if (productName == null) {
            return Optional.empty();
        }
        String name = productName.toLowerCase(Locale.ROOT);

        Matcher multi = MULTIPACK.matcher(name);
        PackageSize found = null;
        while (multi.find()) {
            PackageSize single = of(multi.group(2), multi.group(3));
            if (single != null) {
                found = new PackageSize(single.quantity.multiply(new BigDecimal(multi.group(1))), single.unit);
            }
        }
        if (found == null) {
            Matcher m = SINGLE.matcher(name);
            while (m.find()) { // разфасовката обикновено е в края на името
                PackageSize candidate = of(m.group(1), m.group(2));
                if (candidate != null) {
                    found = candidate;
                }
            }
        }
        if (found == null && PER_KG.matcher(name).find()) {
            found = new PackageSize(null, "kg");
        }
        return Optional.ofNullable(found);
    }

    private static PackageSize of(String number, String unit) {
        BigDecimal value;
        try {
            value = new BigDecimal(number.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        if (value.signum() <= 0) {
            return null;
        }
        BigDecimal thousand = BigDecimal.valueOf(1000);
        PackageSize result = switch (unit) {
            case "кг", "kg" -> new PackageSize(value, "kg");
            case "гр", "gr", "г", "g" -> new PackageSize(value.divide(thousand, 3, RoundingMode.HALF_UP), "kg");
            case "л", "l" -> new PackageSize(value, "l");
            case "мл", "ml" -> new PackageSize(value.divide(thousand, 3, RoundingMode.HALF_UP), "l");
            case "бр" -> new PackageSize(value, "pcs");
            default -> null;
        };
        // Отхвърляме явно грешни стойности (напр. „3.6% 500“ без мярка не стига дотук)
        if (result == null || result.quantity.signum() <= 0 || result.quantity.compareTo(BigDecimal.valueOf(1000)) > 0) {
            return null;
        }
        return result;
    }
}
