package bg.sravniceni.importer;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Толерантен CSV четец. Файловете на веригите не винаги са валиден CSV
 * (кавички в средата на поле, различни разделители, BOM), затова парсваме
 * като Python csv модула: грешно поставените кавички се приемат буквално,
 * вместо целият файл да се отхвърли.
 */
final class LenientCsvReader {

    private final Reader in;
    private final char delimiter;
    private int pushedBack = -2;

    LenientCsvReader(Reader in, char delimiter) {
        this.in = in;
        this.delimiter = delimiter;
    }

    /** Следващият ред или null в края на файла. */
    List<String> next() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean atFieldStart = true;
        boolean any = false;

        int c;
        while ((c = read()) != -1) {
            any = true;
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    int nextChar = read();
                    if (nextChar == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (nextChar != -1) {
                            unread(nextChar);
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && atFieldStart) {
                quoted = true;
                atFieldStart = false;
            } else if (ch == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                atFieldStart = true;
            } else if (ch == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (ch != '\r') {
                field.append(ch);
                atFieldStart = false;
            }
        }
        if (!any) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    private int read() throws IOException {
        if (pushedBack != -2) {
            int c = pushedBack;
            pushedBack = -2;
            return c;
        }
        return in.read();
    }

    private void unread(int c) {
        pushedBack = c;
    }

    /** Определя разделителя по заглавния ред. */
    static char detectDelimiter(String headerLine) {
        long semicolons = headerLine.chars().filter(ch -> ch == ';').count();
        long commas = headerLine.chars().filter(ch -> ch == ',').count();
        return semicolons > commas ? ';' : ',';
    }
}
