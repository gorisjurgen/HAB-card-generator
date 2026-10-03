package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.config.CardDataConfig;
import com.goris.habcardgenerator.model.CardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class CardDataService {

    private final CardDataConfig cardDataConfig;

    /** Zero-based column indexes of the configured columns in a given sheet. */
    private record ColumnIndexes(int memberId, int name, int address) {
    }

    public List<CardData> importCardData() {
        String inputDirectory = cardDataConfig.getInputDirectory();
        log.info("Importing card data from directory: {}", inputDirectory);

        List<CardData> allCardData = new ArrayList<>();

        try {
            // Find all Excel files in the directory
            List<Path> excelFiles = findExcelFiles(inputDirectory);
            log.info("Found {} Excel file(s) in directory", excelFiles.size());

            // Process each Excel file
            for (Path excelFile : excelFiles) {
                log.info("Processing file: {}", excelFile.getFileName());
                List<CardData> fileData = readExcelFile(excelFile);
                allCardData.addAll(fileData);
            }

            // Sort the combined data
            allCardData = sortCardData(allCardData);

            log.info("Successfully imported and sorted {} card data records from {} file(s)",
                     allCardData.size(), excelFiles.size());

            // Print sorted data
            log.info("=== Sorted Card Data ===");
            for (CardData cardData : allCardData) {
                log.info("{}", cardData);
            }

            return allCardData;
        } catch (IOException e) {
            log.error("Error reading Excel files", e);
            throw new RuntimeException("Failed to import card data", e);
        }
    }

    private List<Path> findExcelFiles(String directory) throws IOException {
        Path dirPath = Paths.get(directory);

        if (!Files.exists(dirPath)) {
            log.error("Directory does not exist: {}", directory);
            throw new RuntimeException("Input directory does not exist: " + directory);
        }

        if (!Files.isDirectory(dirPath)) {
            log.error("Path is not a directory: {}", directory);
            throw new RuntimeException("Input path is not a directory: " + directory);
        }

        try (Stream<Path> paths = Files.walk(dirPath, 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private List<CardData> readExcelFile(Path filePath) throws IOException {
        List<CardData> cardDataList = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(filePath.toFile());
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            log.info("  Reading from sheet: {}", sheet.getSheetName());

            // Read header row to map column names to indices
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException("No header row found in " + filePath.getFileName());
            }
            Map<String, Integer> columnMap = buildColumnMap(headerRow);
            ColumnIndexes columns = resolveColumns(columnMap, filePath);

            // Read data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    CardData cardData = readCardDataFromRow(row, columns);
                    if (cardData != null) {
                        cardDataList.add(cardData);
                    }
                }
            }

            log.info("  Imported {} records from {}", cardDataList.size(), filePath.getFileName());
        }

        return cardDataList;
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> columnMap = new LinkedHashMap<>();
        DataFormatter formatter = new DataFormatter();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String columnName = formatter.formatCellValue(cell).trim();
                if (!columnName.isEmpty()) {
                    columnMap.putIfAbsent(columnName, i);
                }
            }
        }

        log.info("Column mappings: {}", columnMap);
        return columnMap;
    }

    /**
     * Looks up the configured column names in the header. Fails fast when a column is not configured
     * or not present, so that a wrong configuration does not silently produce empty labels.
     */
    private ColumnIndexes resolveColumns(Map<String, Integer> columnMap, Path filePath) {
        CardDataConfig.Columns configured = cardDataConfig.getColumns();
        if (configured == null) {
            throw new IllegalStateException("card.data.columns is not configured");
        }
        return new ColumnIndexes(
                resolveColumn(columnMap, "memberId", configured.getMemberId(), filePath),
                resolveColumn(columnMap, "name", configured.getName(), filePath),
                resolveColumn(columnMap, "address", configured.getAddress(), filePath));
    }

    private int resolveColumn(Map<String, Integer> columnMap, String key, String configuredName, Path filePath) {
        if (configuredName == null || configuredName.isBlank()) {
            throw new IllegalStateException("card.data.columns." + key + " is not configured");
        }
        Integer index = columnMap.get(configuredName.trim());
        if (index == null) {
            throw new IllegalStateException("Column '" + configuredName + "' (card.data.columns." + key
                    + ") not found in header of " + filePath.getFileName()
                    + ". Available columns: " + columnMap.keySet());
        }
        return index;
    }

    /** Returns {@code null} for rows without any data (e.g. trailing formatted rows in an export). */
    private CardData readCardDataFromRow(Row row, ColumnIndexes columns) {
        String memberId = AddressParser.normalizeWhitespace(getCellValueAsString(row, columns.memberId()));
        String name = AddressParser.normalizeWhitespace(getCellValueAsString(row, columns.name()));
        String rawAddress = getCellValueAsString(row, columns.address());

        if (memberId.isEmpty() && name.isEmpty() && rawAddress.isBlank()) {
            log.debug("Skipping empty row {}", row.getRowNum() + 1);
            return null;
        }

        AddressParser.ParsedAddress address = AddressParser.parse(rawAddress);
        if (!address.hasNumber()) {
            log.warn("Row {} (member {}): no house number found in address '{}'",
                     row.getRowNum() + 1, memberId, rawAddress);
        }

        return new CardData(memberId, name, address.street(), address.streetNumber(), address.bus());
    }

    private String getCellValueAsString(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }

        // For formula cells, get the cached/evaluated value instead of the formula text
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        return switch (cellType) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    List<CardData> sortCardData(List<CardData> cardDataList) {
        Comparator<CardData> comparator;

        if (cardDataConfig.isSmallStreetsLast()) {
            log.info("Sorting with smallStreetsLast enabled: street size (descending), then street name, then street number");

            // Count members per street
            Map<String, Long> streetCounts = cardDataList.stream()
                    .collect(Collectors.groupingBy(CardData::street, Collectors.counting()));

            log.info("Street member counts: {}", streetCounts);

            // Sort by street size (largest first), then by street name, then by street number
            comparator = Comparator
                    .<CardData>comparingLong(cardData -> -streetCounts.get(cardData.street())) // Negative for descending
                    .thenComparing(CardData::street)
                    .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber()));

            if (cardDataConfig.isSplitOddEven()) {
                log.info("  Also splitting odd/even within each street");
                comparator = Comparator
                        .<CardData>comparingLong(cardData -> -streetCounts.get(cardData.street()))
                        .thenComparing(CardData::street)
                        .thenComparing(cardData -> {
                            int number = parseStreetNumber(cardData.streetNumber());
                            return number % 2; // 0 for even, 1 for odd
                        })
                        .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber()));
            }
        } else if (cardDataConfig.isSplitOddEven()) {
            log.info("Sorting with splitOddEven enabled: street, then even numbers first, then odd numbers");
            comparator = Comparator
                    .comparing(CardData::street)
                    .thenComparing(cardData -> {
                        // Parse street number to determine if even or odd
                        int number = parseStreetNumber(cardData.streetNumber());
                        return number % 2; // 0 for even, 1 for odd
                    })
                    .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber()));
        } else {
            log.info("Sorting by street and street number");
            comparator = Comparator
                    .comparing(CardData::street)
                    .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber()));
        }

        return cardDataList.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /** Leading number of a house number ("12A" -> 12, "75-77" -> 75); empty values sort last. */
    private int parseStreetNumber(String streetNumber) {
        return AddressParser.houseNumberValue(streetNumber);
    }
}
