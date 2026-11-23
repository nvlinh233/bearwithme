package dev.bearwithme.card;

import dev.bearwithme.card.CardController.CardRequest;
import dev.bearwithme.card.CardService.CardInput;
import dev.bearwithme.card.CardService.ImportCounts;
import dev.bearwithme.card.CsvParser.CsvFormatException;
import dev.bearwithme.card.CsvParser.CsvRecord;
import dev.bearwithme.common.error.InvalidRequestException;
import jakarta.validation.Validator;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * FR-I1..I4. Parsing and validation happen here, outside the transaction; only the valid rows go to
 * {@link CardService#importRows}, which saves them in one transaction.
 */
@Service
class CardImportService {

    static final List<String> HEADER = List.of("term", "meaning", "example", "tags");
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;

    private final CardService cards;
    private final Validator validator;

    CardImportService(CardService cards, Validator validator) {
        this.cards = cards;
        this.validator = validator;
    }

    record RowError(int line, String reason) {
    }

    record ImportResult(int created, int skipped, List<RowError> errors) {
    }

    ImportResult importCsv(byte[] content) {
        if (content.length > MAX_BYTES) {
            throw new InvalidRequestException("File is larger than 2 MB");
        }
        List<CsvRecord> records = parse(decodeUtf8(content));
        if (records.isEmpty()) {
            throw new InvalidRequestException("File is empty; expected header " + String.join(",", HEADER));
        }
        requireHeader(records.getFirst());
        List<CsvRecord> rows = records.subList(1, records.size());
        if (rows.size() > MAX_ROWS) {
            throw new InvalidRequestException("File has " + rows.size() + " rows; the maximum is " + MAX_ROWS);
        }

        List<CardInput> valid = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (CsvRecord row : rows) {
            String reason = validate(row);
            if (reason == null) {
                valid.add(toInput(row));
            } else {
                errors.add(new RowError(row.line(), reason));
            }
        }
        ImportCounts counts = cards.importRows(valid);
        return new ImportResult(counts.created(), counts.skipped(), errors);
    }

    /** FR-I1: UTF-8 only. A strict decoder fails instead of silently replacing bad bytes with '?'. */
    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidRequestException("File is not valid UTF-8");
        }
    }

    private static List<CsvRecord> parse(String text) {
        try {
            return CsvParser.parse(text);
        } catch (CsvFormatException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private static void requireHeader(CsvRecord header) {
        List<String> actual = header.fields().stream().map(f -> f.strip().toLowerCase(Locale.ROOT)).toList();
        if (!actual.equals(HEADER)) {
            throw new InvalidRequestException(
                    "Line 1: expected header " + String.join(",", HEADER) + " but found " + String.join(",", actual));
        }
    }

    /** Same rules as the card editor (FR-C1, FR-C3): reuse the constraints on {@link CardRequest}. */
    private String validate(CsvRecord row) {
        if (row.fields().size() != HEADER.size()) {
            return "expected " + HEADER.size() + " columns but found " + row.fields().size();
        }
        CardInput input = toInput(row);
        var request = new CardRequest(input.term(), input.meaning(), input.example(), null, input.tags());
        var violations = validator.validate(request);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private static CardInput toInput(CsvRecord row) {
        List<String> f = row.fields();
        List<String> tags = Arrays.stream(f.get(3).split("\\|"))
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .toList();
        return new CardInput(f.get(0), f.get(1), f.get(2), null, tags);
    }
}
