package com.medicompare;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A CSV file opened for reading. Reads UTF-8 and falls back to Windows-1252
 * if the file is not valid UTF-8. A row the parser cannot read stops the
 * read with a warning instead of crashing; rows read before it are kept.
 */
final class CsvTable implements AutoCloseable {
    /** One data row; every value is already tidied. */
    static final class Row {
        private final CSVRecord record;

        Row(CSVRecord record) {
            this.record = record;
        }

        int size() {
            return record.size();
        }

        long number() {
            return record.getRecordNumber();
        }

        String get(int column) {
            if (column < 0 || column >= record.size()) {
                return "";
            }
            return TextUtil.tidy(record.get(column));
        }
    }

    final String charset;
    private final CSVParser parser;
    private final List<String> headers = new ArrayList<>();
    private int rowsRead;
    private String stoppedEarly = "";

    private CsvTable(CSVParser parser, String charset) {
        this.parser = parser;
        this.charset = charset;
        for (String header : parser.getHeaderNames()) {
            headers.add(TextUtil.tidy(header));
        }
    }

    List<String> headers() {
        return headers;
    }

    int rowsRead() {
        return rowsRead;
    }

    String stoppedEarly() {
        return stoppedEarly;
    }

    /** Index of the first header whose simplified name is in aliases, or -1. */
    int column(Set<String> aliases) {
        for (int i = 0; i < headers.size(); i++) {
            if (aliases.contains(TextUtil.headerKey(headers.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** Indexes of every header whose simplified name matches, in order. */
    List<Integer> columns(Pattern pattern) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (pattern.matcher(TextUtil.headerKey(headers.get(i)))
                    .matches()) {
                found.add(i);
            }
        }
        return found;
    }

    void forEachRow(Consumer<Row> consumer) {
        Iterator<CSVRecord> rows = parser.iterator();

        while (true) {
            CSVRecord record;
            try {
                if (!rows.hasNext()) {
                    return;
                }
                record = rows.next();
            } catch (IllegalStateException | UncheckedIOException exception) {
                // commons-csv reports read errors as one or the other,
                // depending on its version
                if (isCharsetProblem(exception)) {
                    throw exception;
                }
                stoppedEarly = "Unreadable CSV near record "
                        + (rowsRead + 1) + " (" + exception.getMessage()
                        + "); records after this point were not loaded.";
                return;
            }

            rowsRead++;
            consumer.accept(new Row(record));
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    /** Opens the file, runs the action, and retries as cp1252 if needed. */
    static <T> T read(Path path, Function<CsvTable, T> action)
            throws IOException {

        try {
            return open(path, StandardCharsets.UTF_8, action);
        } catch (RuntimeException | CharacterCodingException exception) {
            if (!isCharsetProblem(exception)) {
                throw exception;
            }
            return open(path, Charset.forName("windows-1252"), action);
        }
    }

    @SuppressWarnings("deprecation") // build() works in every 1.x release
    private static <T> T open(
            Path path, Charset charset, Function<CsvTable, T> action)
            throws IOException {

        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(path), decoder);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .setAllowMissingColumnNames(true)
                     .build()
                     .parse(reader);
             CsvTable table = new CsvTable(parser, charset.name())) {

            return action.apply(table);
        }
    }

    private static boolean isCharsetProblem(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CharacterCodingException) {
                return true;
            }
        }
        return false;
    }
}
