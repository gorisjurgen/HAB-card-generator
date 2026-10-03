package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.config.CardDataConfig;
import com.goris.habcardgenerator.model.CardData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardDataServiceTest {

    /** Header row exactly as Assistonline exports it. */
    private static final String[] EXPORT_HEADER = {
            "Lidnr.", "Voornaam + naam", "Naam + voornaam", "Roepnaam", "Gender", "GSM", "E-mailadres",
            "Adres", "Gemeente", "Ledengroep(en)", "Functie", "Saldo", "Status", "Opmerking"
    };

    @TempDir
    Path inputDir;

    @Test
    void parsesRowsFromAssistonlineExport() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER,
                exportRow("637", "Kristin  Van Gaever", "Max Hermanlei 118 "),
                exportRow("12", "Familie Korstjens – Jansen", "Rustoordlei 75-77 "),
                exportRow("5", "Gemeente", "Gemeentepark  "),
                exportRow("8", "Jan Peeters", "Bredabaan 12 bus 3"));

        List<CardData> result = service(false, false).importCardData();

        assertThat(result).containsExactly(
                new CardData("8", "Jan Peeters", "Bredabaan", "12", "bus 3"),
                new CardData("5", "Gemeente", "Gemeentepark", "", ""),
                new CardData("637", "Kristin Van Gaever", "Max Hermanlei", "118", ""),
                new CardData("12", "Familie Korstjens – Jansen", "Rustoordlei", "75-77", ""));
    }

    @Test
    void sortsByStreetThenEvenOddThenLeadingNumber() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER,
                exportRow("1", "A", "Bredabaan 7"),
                exportRow("2", "B", "Bredabaan 12"),
                exportRow("3", "C", "Bredabaan 1B"),
                exportRow("4", "D", "Akkerlaan 3"),
                exportRow("5", "E", "Bredabaan 75-77"),
                exportRow("6", "F", "Bredabaan"),
                exportRow("7", "G", "Bredabaan 4"));

        List<CardData> result = service(true, false).importCardData();

        // street, then even numbers, then odd numbers by leading number, no number last
        assertThat(result).extracting(CardData::memberId).containsExactly("4", "7", "2", "3", "1", "5", "6");
    }

    @Test
    void smallStreetsLastPutsLargeStreetsFirst() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER,
                exportRow("1", "A", "Zandstraat 2"),
                exportRow("2", "B", "Akkerlaan 9"),
                exportRow("3", "C", "Zandstraat 1"),
                exportRow("4", "D", "Zandstraat 4"));

        List<CardData> result = service(true, true).importCardData();

        assertThat(result).extracting(CardData::memberId).containsExactly("1", "4", "3", "2");
    }

    @Test
    void convertsUpperCaseNamesToTitleCaseWhenEnabled() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER,
                exportRow("1", "JAN KERREMANS", "Zandstraat 2"),
                exportRow("2", "Fam. Teunen - De Witte", "Zandstraat 4"));

        assertThat(service(false, false).importCardData()).extracting(CardData::name)
                .containsExactly("Jan Kerremans", "Fam. Teunen - De Witte");

        CardDataConfig config = config(false, false);
        config.setTitleCaseUpperCaseNames(false);
        assertThat(new CardDataService(config).importCardData()).extracting(CardData::name)
                .containsExactly("JAN KERREMANS", "Fam. Teunen - De Witte");
    }

    @Test
    void ignoresEmptyTrailingRows() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER,
                exportRow("1", "A", "Zandstraat 2"),
                exportRow("", "", ""));

        assertThat(service(false, false).importCardData()).hasSize(1);
    }

    @Test
    void matchesHeadersIgnoringSurroundingWhitespace() throws IOException {
        writeWorkbook("export.xlsx", new String[]{" Lidnr. ", "Voornaam + naam ", " Adres"},
                new String[]{"9", "Jan", "Kerkstraat 3"});

        assertThat(service(false, false).importCardData())
                .containsExactly(new CardData("9", "Jan", "Kerkstraat", "3", ""));
    }

    @Test
    void failsFastWhenConfiguredColumnIsMissingFromHeader() throws IOException {
        writeWorkbook("export.xlsx", new String[]{"Lidnr.", "Voornaam + naam", "Gemeente"},
                new String[]{"9", "Jan", "2930 Brasschaat"});

        CardDataService service = service(false, false);

        assertThatThrownBy(service::importCardData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Adres")
                .hasMessageContaining("card.data.columns.address")
                .hasMessageContaining("Gemeente");
    }

    @Test
    void failsFastWhenColumnIsNotConfigured() throws IOException {
        writeWorkbook("export.xlsx", EXPORT_HEADER, exportRow("9", "Jan", "Kerkstraat 3"));

        CardDataConfig config = config(false, false);
        config.getColumns().setAddress(null);
        CardDataService service = new CardDataService(config);

        assertThatThrownBy(service::importCardData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("card.data.columns.address");
    }

    private CardDataService service(boolean splitOddEven, boolean smallStreetsLast) {
        return new CardDataService(config(splitOddEven, smallStreetsLast));
    }

    private CardDataConfig config(boolean splitOddEven, boolean smallStreetsLast) {
        CardDataConfig.Columns columns = new CardDataConfig.Columns();
        columns.setMemberId("Lidnr.");
        columns.setName("Voornaam + naam");
        columns.setAddress("Adres");

        CardDataConfig config = new CardDataConfig();
        config.setColumns(columns);
        config.setSplitOddEven(splitOddEven);
        config.setSmallStreetsLast(smallStreetsLast);
        config.setInputDirectory(inputDir.toString());
        return config;
    }

    /** A full export row; the columns the importer does not use get filler values. */
    private static Object[] exportRow(String memberId, String name, String address) {
        return new Object[]{memberId, name, "", "", "-", "", "", address, "2930 Brasschaat", "HAB vzw", "KLE LID", 10, "", ""};
    }

    private void writeWorkbook(String fileName, String[] header, Object[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("all");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < header.length; c++) {
                headerRow.createCell(c).setCellValue(header[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            try (OutputStream out = Files.newOutputStream(inputDir.resolve(fileName))) {
                workbook.write(out);
            }
        }
    }
}
