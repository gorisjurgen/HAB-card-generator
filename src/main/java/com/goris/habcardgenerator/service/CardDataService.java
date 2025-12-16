package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.config.CardDataConfig;
import com.goris.habcardgenerator.model.CardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
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
            Map<String, Integer> columnMap = buildColumnMap(headerRow);

            // Read data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    CardData cardData = readCardDataFromRow(row, columnMap);
                    cardDataList.add(cardData);
                }
            }

            log.info("  Imported {} records from {}", cardDataList.size(), filePath.getFileName());
        }

        return cardDataList;
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
        String bus = getCellValueAsString(row, columnMap.get(cardDataConfig.getColumns().getBus()));

        return new CardData(memberId, name, street, streetNumber, bus);
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
