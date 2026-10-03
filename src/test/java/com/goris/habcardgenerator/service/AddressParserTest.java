package com.goris.habcardgenerator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AddressParserTest {

    static Stream<Arguments> addresses() {
        return Stream.of(
                // values as they appear in the Assistonline export
                Arguments.of("Max Hermanlei 118 ", "Max Hermanlei", "118", ""),
                Arguments.of("De Romboutweg  39 ", "De Romboutweg", "39", ""),
                Arguments.of("Langestraat 1B ", "Langestraat", "1B", ""),
                Arguments.of("Magdalenalei 142b ", "Magdalenalei", "142b", ""),
                Arguments.of("Hoge Kaart 197c ", "Hoge Kaart", "197c", ""),
                Arguments.of("Rustoordlei 75-77 ", "Rustoordlei", "75-77", ""),
                Arguments.of("Castel del Vinolei 12", "Castel del Vinolei", "12", ""),
                Arguments.of("Schout De Moorstraat 5", "Schout De Moorstraat", "5", ""),
                Arguments.of("du Boislei 94b", "du Boislei", "94b", ""),
                Arguments.of("Gijsbrecht van Deurnelaan  ", "Gijsbrecht van Deurnelaan", "", ""),
                Arguments.of("Gemeentepark  ", "Gemeentepark", "", ""),
                // bus notations
                Arguments.of("Bredabaan 12 bus 3", "Bredabaan", "12", "bus 3"),
                Arguments.of("Bredabaan 12 Bus 3", "Bredabaan", "12", "bus 3"),
                Arguments.of("Bredabaan 12/3", "Bredabaan", "12", "bus 3"),
                Arguments.of("Bredabaan 12 / 3", "Bredabaan", "12", "bus 3"),
                Arguments.of("Bredabaan 12 A", "Bredabaan", "12", "A"),
                // non-breaking spaces and empty input
                Arguments.of("Hoge Akker 12 ", "Hoge Akker", "12", ""),
                Arguments.of("", "", "", ""),
                Arguments.of(null, "", "", "")
        );
    }

    @ParameterizedTest
    @MethodSource("addresses")
    void parsesStreetNumberAndBus(String raw, String street, String number, String bus) {
        AddressParser.ParsedAddress parsed = AddressParser.parse(raw);

        assertThat(parsed.street()).isEqualTo(street);
        assertThat(parsed.streetNumber()).isEqualTo(number);
        assertThat(parsed.bus()).isEqualTo(bus);
        assertThat(parsed.hasNumber()).isEqualTo(!number.isEmpty());
    }

    static Stream<Arguments> houseNumbers() {
        return Stream.of(
                Arguments.of("12", 12),
                Arguments.of("1B", 1),
                Arguments.of("142b", 142),
                Arguments.of("75-77", 75),
                Arguments.of("", Integer.MAX_VALUE),
                Arguments.of(null, Integer.MAX_VALUE),
                Arguments.of("abc", Integer.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("houseNumbers")
    void houseNumberValueUsesLeadingDigits(String streetNumber, int expected) {
        assertThat(AddressParser.houseNumberValue(streetNumber)).isEqualTo(expected);
    }

    @Test
    void normalizeWhitespaceCollapsesAndStrips() {
        assertThat(AddressParser.normalizeWhitespace("Kristin  Van Gaever")).isEqualTo("Kristin Van Gaever");
        assertThat(AddressParser.normalizeWhitespace("  Tabita\tClaes ")).isEqualTo("Tabita Claes");
        assertThat(AddressParser.normalizeWhitespace(null)).isEmpty();
    }
}
