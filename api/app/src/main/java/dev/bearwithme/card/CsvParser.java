package dev.bearwithme.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 parser: comma separator, double-quoted fields, {@code ""} for a quote inside quotes,
 * line breaks inside quotes, LF or CRLF line endings. Blank lines are skipped. Each record keeps the
 * physical line number where it starts (1-based), for error reports (FR-I3).
 */
final class CsvParser {

    record CsvRecord(int line, List<String> fields) {
    }

    static final class CsvFormatException extends RuntimeException {

        CsvFormatException(String message) {
            super(message);
        }
    }

    private CsvParser() {
    }

    static List<CsvRecord> parse(String text) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int line = 1;
        int recordLine = 1;

        int i = text.startsWith("﻿") ? 1 : 0; // UTF-8 BOM written by Excel
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    if (field.isEmpty()) {
                        inQuotes = true;
                    } else {
                        field.append(c); // stray quote inside an unquoted field: keep it as text
                    }
                }
                case ',' -> {
                    fields.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> {
                    // part of CRLF; the '\n' ends the record
                }
                case '\n' -> {
                    fields.add(field.toString());
                    addUnlessBlank(records, recordLine, fields);
                    fields = new ArrayList<>();
                    field.setLength(0);
                    line++;
                    recordLine = line;
                }
                default -> field.append(c);
            }
        }
        if (inQuotes) {
            throw new CsvFormatException("Line " + recordLine + ": unclosed quote");
        }
        if (!field.isEmpty() || !fields.isEmpty()) {
            fields.add(field.toString());
            addUnlessBlank(records, recordLine, fields);
        }
        return records;
    }

    private static void addUnlessBlank(List<CsvRecord> records, int line, List<String> fields) {
        boolean blank = fields.size() == 1 && fields.getFirst().isBlank();
        if (!blank) {
            records.add(new CsvRecord(line, List.copyOf(fields)));
        }
    }
}
