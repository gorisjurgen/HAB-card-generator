package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.config.CardDataConfig;
import com.goris.habcardgenerator.model.CardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CardDataService {

    private static final String EXCEL_FILE_PATH = "test-data-HAB.xlsx";
    private final CardDataConfig cardDataConfig;

    public List<CardData> importCardData() {
        log.info("Importing card data from: {}", EXCEL_FILE_PATH);

        try {
            ClassPathResource resource = new ClassPathResource(EXCEL_FILE_PATH);
            try (InputStream inputStream = resource.getInputStream();
                 Workbook workbook = WorkbookFactory.create(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                log.info("Reading from sheet: {}", sheet.getSheetName());

                // Read header row to map column names to indices
                Row headerRow = sheet.getRow(0);
                Map<String, Integer> columnMap = buildColumnMap(headerRow);

                // Read data rows
                List<CardData> cardDataList = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        CardData cardData = readCardDataFromRow(row, columnMap);
                        cardDataList.add(cardData);
                    }
                }

                // Sort the data
                cardDataList = sortCardData(cardDataList);

                log.info("Successfully imported and sorted {} card data records", cardDataList.size());

                // Print sorted data
                log.info("=== Sorted Card Data ===");
                for (CardData cardData : cardDataList) {
                    log.info("{}", cardData);
                }

                return cardDataList;
            }
        } catch (IOException e) {
            log.error("Error reading Excel file", e);
            throw new RuntimeException("Failed to import card data", e);
        }
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String columnName = cell.getStringCellValue();
                columnMap.put(columnName, i);
            }
        }

        log.info("Column mappings: {}", columnMap);
        return columnMap;
    }

    private CardData readCardDataFromRow(Row row, Map<String, Integer> columnMap) {
        String memberId = getCellValueAsString(row, columnMap.get(cardDataConfig.getColumns().getMemberId()));
        String name = getCellValueAsString(row, columnMap.get(cardDataConfig.getColumns().getName()));
        String street = getCellValueAsString(row, columnMap.get(cardDataConfig.getColumns().getStreet()));
        String streetNumber = getCellValueAsString(row, columnMap.get(cardDataConfig.getColumns().getStreetNumber()));

        return new CardData(memberId, name, street, streetNumber);
    }

    private String getCellValueAsString(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return "";
        }

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

    private List<CardData> sortCardData(List<CardData> cardDataList) {
        if (cardDataConfig.isSplitOddEven()) {
            log.info("Sorting with splitOddEven enabled: street, then even numbers first, then odd numbers");
            return cardDataList.stream()
                    .sorted(Comparator
                            .comparing(CardData::street)
                            .thenComparing(cardData -> {
                                // Parse street number to determine if even or odd
                                int number = parseStreetNumber(cardData.streetNumber());
                                return number % 2; // 0 for even, 1 for odd
                            })
                            .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber())))
                    .collect(Collectors.toList());
        } else {
            log.info("Sorting by street and street number");
            return cardDataList.stream()
                    .sorted(Comparator
                            .comparing(CardData::street)
                            .thenComparing(cardData -> parseStreetNumber(cardData.streetNumber())))
                    .collect(Collectors.toList());
        }
    }

    private int parseStreetNumber(String streetNumber) {
        if (streetNumber == null || streetNumber.isEmpty()) {
            return Integer.MAX_VALUE; // Put empty values at the end
        }
        try {
            // Extract numeric part from street number (handles cases like "12A", "12-14", etc.)
            String numericPart = streetNumber.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
