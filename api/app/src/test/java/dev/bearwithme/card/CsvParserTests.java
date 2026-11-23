package dev.bearwithme.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bearwithme.card.CsvParser.CsvFormatException;
import dev.bearwithme.card.CsvParser.CsvRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTests {

    @Test
    void parsesPlainFields() {
        assertThat(CsvParser.parse("a,b,c\nd,e,f\n")).containsExactly(
                new CsvRecord(1, List.of("a", "b", "c")),
                new CsvRecord(2, List.of("d", "e", "f")));
    }

    @Test
    void quotedFieldsKeepCommasAndEscapedQuotes() {
        assertThat(CsvParser.parse("\"a, b\",\"say \"\"hi\"\"\",\"\"").getFirst().fields())
                .containsExactly("a, b", "say \"hi\"", "");
    }

    @Test
    void lineBreakInsideQuotesKeepsLineNumbersOfLaterRecords() {
        List<CsvRecord> records = CsvParser.parse("h1,h2\n\"line one\nline two\",x\nlast,y\n");

        assertThat(records.get(1)).isEqualTo(new CsvRecord(2, List.of("line one\nline two", "x")));
        assertThat(records.get(2).line()).isEqualTo(4);
    }

    @Test
    void handlesCrlfBomEmptyFieldsAndMissingFinalNewline() {
        assertThat(CsvParser.parse("﻿a,,c\r\nd,e,"))
                .containsExactly(new CsvRecord(1, List.of("a", "", "c")), new CsvRecord(2, List.of("d", "e", "")));
    }

    @Test
    void skipsBlankLinesButCountsThem() {
        List<CsvRecord> records = CsvParser.parse("a,b\n\n\nc,d\n");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).line()).isEqualTo(4);
    }

    @Test
    void unclosedQuoteIsAnError() {
        assertThatThrownBy(() -> CsvParser.parse("a,b\n\"open,c\n"))
                .isInstanceOf(CsvFormatException.class)
                .hasMessage("Line 2: unclosed quote");
    }
}
