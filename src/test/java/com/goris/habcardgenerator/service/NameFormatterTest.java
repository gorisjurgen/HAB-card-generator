package com.goris.habcardgenerator.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NameFormatterTest {

    static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of("JAN KERREMANS", "Jan Kerremans"),
                Arguments.of("SWA COENEN", "Swa Coenen"),
                Arguments.of("FAM. VAN TICHELEN - HENS", "Fam. Van Tichelen - Hens"),
                Arguments.of("RONNY -LIEVE SCHUERMAN - STEVENS", "Ronny -Lieve Schuerman - Stevens"),
                Arguments.of("D'HONDT", "D'Hondt"),
                Arguments.of("MARIE-LOUISE PEETERS", "Marie-Louise Peeters"),
                Arguments.of("JOSÉ ÅBERG", "José Åberg"),
                // mixed case is left untouched
                Arguments.of("Fam. Teunen - De Witte", "Fam. Teunen - De Witte"),
                Arguments.of("Sofie Van Itterbeeck", "Sofie Van Itterbeeck"),
                Arguments.of("Org. ARRO tav Dhr. Smits J.", "Org. ARRO tav Dhr. Smits J."),
                Arguments.of("ROM0006", "Rom0006"),
                Arguments.of("", ""),
                Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("names")
    void convertsOnlyAllUpperCaseNames(String input, String expected) {
        assertThat(NameFormatter.titleCaseIfUpperCase(input)).isEqualTo(expected);
    }
}
