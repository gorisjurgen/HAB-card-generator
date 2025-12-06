package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.config.CardDataConfig;
import com.goris.habcardgenerator.model.CardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CardLabelGenerator {

    private static final String TEMPLATE_PATH = "Avery_64x34-R.docx";
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int LABELS_PER_PAGE = 24;
    private static final int LABELS_PER_ROW = 3;

    @Value("${label.background.color:FFFFFF}")
    private String backgroundColor;

    private final CardDataConfig cardDataConfig;
    private List<XWPFTable> allTables = new ArrayList<>();

    public void generateCardLabels(List<CardData> cardDataList) {
        try {
            log.info("Starting card label generation with {} labels...", cardDataList.size());

            allTables.clear(); // Reset tables list

            XWPFDocument document = loadTemplate();

            analyzeTemplate(document);

            // Store the first table
            allTables.add(document.getTables().get(0));

            // Add empty labels when street changes if useNewLine is enabled
            List<CardData> adjustedCardDataList = addEmptyLabelsForStreetChanges(cardDataList);

            // Calculate how many pages we need
            int pagesNeeded = (int) Math.ceil((double) adjustedCardDataList.size() / LABELS_PER_PAGE);
            log.info("Pages needed for {} labels: {}", adjustedCardDataList.size(), pagesNeeded);

            // Duplicate pages if we need more than one
            if (pagesNeeded > 1) {
                duplicatePages(document, pagesNeeded);
            }

            populateLabels(adjustedCardDataList);

            // Remove any trailing paragraphs after the last table to avoid empty pages
            removeTrailingContent(document);

            String outputPath = saveDocument(document);

            log.info("Card labels generated successfully at: {}", outputPath);

        } catch (IOException e) {
            log.error("Error generating card labels", e);
            throw new RuntimeException("Failed to generate card labels", e);
        }
    }

    private XWPFDocument loadTemplate() throws IOException {
        log.info("Loading template: {}", TEMPLATE_PATH);
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);

        XWPFDocument document;
        try (InputStream inputStream = resource.getInputStream()) {
            document = new XWPFDocument(inputStream);
        }

        // Remove any trailing content from the template itself
        List<IBodyElement> bodyElements = document.getBodyElements();
        log.info("Template has {} body elements", bodyElements.size());

        // Find first table and remove everything after it
        int firstTableIndex = -1;
        for (int i = 0; i < bodyElements.size(); i++) {
            if (bodyElements.get(i).getElementType() == BodyElementType.TABLE) {
                firstTableIndex = i;
                break;
            }
        }

        if (firstTableIndex != -1 && firstTableIndex < bodyElements.size() - 1) {
            for (int i = bodyElements.size() - 1; i > firstTableIndex; i--) {
                document.removeBodyElement(i);
                log.info("Removed trailing element from template at index {}", i);
            }
        }

        return document;
    }

    private void analyzeTemplate(XWPFDocument document) {
        log.info("=== Analyzing Template Structure ===");

        int totalTables = document.getTables().size();
        log.info("Total tables in document: {}", totalTables);

        int totalLabels = 0;

        for (int i = 0; i < document.getTables().size(); i++) {
            XWPFTable table = document.getTables().get(i);
            int rows = table.getRows().size();

            log.info("Table {} details:", i + 1);

            for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
                XWPFTableRow row = table.getRows().get(rowIndex);
                int cellsInRow = row.getTableCells().size();
                log.info("  Row {}: {} cells", rowIndex + 1, cellsInRow);
            }

            // Based on user feedback: 8 rows x 3 columns
            int labelRows = 8;
            int labelCols = 3;
            int labelsInTable = labelRows * labelCols;
            totalLabels += labelsInTable;

            log.info("Table {}: {} rows x {} columns = {} labels",
                     i + 1, labelRows, labelCols, labelsInTable);
        }

        log.info("=== Total labels in template: {} ===", totalLabels);
    }

    private List<CardData> addEmptyLabelsForStreetChanges(List<CardData> cardDataList) {
        if (!cardDataConfig.isUseNewLine()) {
            log.info("useNewLine is false, no street-based row splitting needed");
            return cardDataList;
        }

        boolean splitOddEven = cardDataConfig.isSplitOddEven();
        log.info("=== Adding empty labels for street changes (splitOddEven: {}) ===", splitOddEven);

        List<CardData> adjustedList = new ArrayList<>();
        String previousStreet = null;
        Boolean previousWasEven = null;
        int labelsInCurrentRow = 0;

        for (CardData cardData : cardDataList) {
            String currentStreet = cardData.street();
            boolean currentIsEven = isEvenStreetNumber(cardData.streetNumber());

            boolean needsNewRow = false;
            String reason = "";

            // Check if street has changed
            if (previousStreet != null && !previousStreet.equals(currentStreet)) {
                needsNewRow = true;
                reason = String.format("Street changed from '%s' to '%s'", previousStreet, currentStreet);
            }
            // Check if switching from even to odd (only if splitOddEven is enabled and on same street)
            else if (splitOddEven && previousWasEven != null && previousWasEven && !currentIsEven
                     && previousStreet != null && previousStreet.equals(currentStreet)) {
                needsNewRow = true;
                reason = "Switching from even to odd numbers";
            }

            if (needsNewRow) {
                // Add empty labels to complete the current row
                int emptyLabelsNeeded = (LABELS_PER_ROW - labelsInCurrentRow % LABELS_PER_ROW) % LABELS_PER_ROW;

                if (emptyLabelsNeeded > 0) {
                    log.info("{}, adding {} empty labels to complete row", reason, emptyLabelsNeeded);

                    for (int i = 0; i < emptyLabelsNeeded; i++) {
                        adjustedList.add(null); // null represents an empty label
                    }
                    labelsInCurrentRow = 0;
                }
            }

            adjustedList.add(cardData);
            labelsInCurrentRow++;
            previousStreet = currentStreet;
            previousWasEven = currentIsEven;
        }

        log.info("Original list size: {}, Adjusted list size: {}", cardDataList.size(), adjustedList.size());
        return adjustedList;
    }

    private boolean isEvenStreetNumber(String streetNumber) {
        if (streetNumber == null || streetNumber.isEmpty()) {
            return false;
        }
        try {
            // Extract numeric part from street number
            String numericPart = streetNumber.replaceAll("[^0-9]", "");
            if (numericPart.isEmpty()) {
                return false;
            }
            int number = Integer.parseInt(numericPart);
            return number % 2 == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void duplicatePages(XWPFDocument document, int totalPages) {
        log.info("=== Duplicating Pages ===");
        log.info("Creating {} total pages...", totalPages);

        // Get the original table (template)
        XWPFTable originalTable = allTables.get(0);

        // Duplicate the table for each additional page needed
        for (int page = 1; page < totalPages; page++) {
            // Create a minimal page break paragraph with no spacing
            XWPFParagraph pageBreak = document.createParagraph();
            pageBreak.setPageBreak(true);

            // Remove spacing before and after the paragraph
            if (pageBreak.getCTP().getPPr() == null) {
                pageBreak.getCTP().addNewPPr();
            }
            if (pageBreak.getCTP().getPPr().getSpacing() == null) {
                pageBreak.getCTP().getPPr().addNewSpacing();
            }
            pageBreak.getCTP().getPPr().getSpacing().setBefore(0);
            pageBreak.getCTP().getPPr().getSpacing().setAfter(0);
            pageBreak.getCTP().getPPr().getSpacing().setLine(0);

            // Create a new empty table with the same structure
            XWPFTable newTable = document.createTable();

            // Deep copy all table properties including borders and layout
            if (originalTable.getCTTbl().getTblPr() != null) {
                newTable.getCTTbl().setTblPr((CTTblPr) originalTable.getCTTbl().getTblPr().copy());
            }
            if (originalTable.getCTTbl().getTblGrid() != null) {
                newTable.getCTTbl().setTblGrid((CTTblGrid) originalTable.getCTTbl().getTblGrid().copy());
            }

            // Remove the default row that gets created
            newTable.removeRow(0);

            // Copy all rows from the original table
            for (XWPFTableRow originalRow : originalTable.getRows()) {
                XWPFTableRow newRow = newTable.createRow();

                // Deep copy row properties
                if (originalRow.getCtRow().getTrPr() != null) {
                    newRow.getCtRow().setTrPr((CTTrPr) originalRow.getCtRow().getTrPr().copy());
                }

                // Remove default cells
                while (newRow.getTableCells().size() > 0) {
                    newRow.removeCell(0);
                }

                // Copy all cells with full properties
                for (XWPFTableCell originalCell : originalRow.getTableCells()) {
                    XWPFTableCell newCell = newRow.addNewTableCell();

                    // Deep copy cell properties including borders, width, shading, etc.
                    if (originalCell.getCTTc().getTcPr() != null) {
                        newCell.getCTTc().setTcPr((CTTcPr) originalCell.getCTTc().getTcPr().copy());
                    }

                    // Copy all paragraphs and their formatting from the original cell
                    // Remove the default paragraph
                    while (newCell.getParagraphs().size() > 0) {
                        newCell.removeParagraph(0);
                    }

                    // Copy each paragraph from the original cell
                    for (XWPFParagraph originalPara : originalCell.getParagraphs()) {
                        XWPFParagraph newPara = newCell.addParagraph();

                        // Copy paragraph properties
                        if (originalPara.getCTP().getPPr() != null) {
                            newPara.getCTP().setPPr((CTPPr) originalPara.getCTP().getPPr().copy());
                        }
                    }
                }
            }

            // Add to our list
            allTables.add(newTable);

            log.info("Created page {} with duplicated table", page + 1);
        }

        log.info("=== Successfully created {} pages with {} tables ===", totalPages, allTables.size());
    }

    private void populateLabels(List<CardData> cardDataList) {
        log.info("=== Populating Labels ===");

        int labelIndex = 0;
        int tableCount = allTables.size();

        // Iterate through all tables (pages)
        for (int tableIndex = 0; tableIndex < tableCount; tableIndex++) {
            XWPFTable table = allTables.get(tableIndex);
            log.info("Populating table {} (page {})", tableIndex + 1, tableIndex + 1);

            // Iterate through 8 rows and use cells 0, 2, 4 (skipping 1 and 3 which are spacing)
            for (int rowIndex = 0; rowIndex < 8; rowIndex++) {
                XWPFTableRow row = table.getRows().get(rowIndex);

                // Access label cells at positions 0, 2, 4 (3 labels per row)
                int[] labelCellIndices = {0, 2, 4};

                for (int cellIndex : labelCellIndices) {
                    XWPFTableCell cell = row.getCell(cellIndex);

                    // Use card data if available, otherwise leave empty
                    CardData cardData = labelIndex < cardDataList.size() ? cardDataList.get(labelIndex) : null;

                    // Apply background color only if we have card data
                    if (cardData != null) {
                        applyBackgroundColor(cell);
                    }

                    // Clear existing content
                    while (cell.getParagraphs().size() > 0) {
                        cell.removeParagraph(0);
                    }

                    // Add card data to cell
                    if (cardData != null) {
                        // Calculate font size based on name length (same logic as name)
                        int nameFontSize = cardData.name().length() > 25 ? 12 : 14;
                        int yearFontSize = nameFontSize + 2; // Year slightly bigger than name

                        // Line 1: Year (bold) - Member ID
                        XWPFParagraph paragraph1 = cell.addParagraph();

                        // Year part (bold, slightly bigger than name)
                        XWPFRun yearRun = paragraph1.createRun();
                        yearRun.setText(cardDataConfig.getYear());
                        yearRun.setBold(true);
                        yearRun.setFontSize(yearFontSize);

                        // Separator
                        XWPFRun separatorRun = paragraph1.createRun();
                        separatorRun.setText(" - ");
                        separatorRun.setFontSize(10);

                        // Member ID part
                        XWPFRun run1 = paragraph1.createRun();
                        run1.setText(cardData.memberId());
                        run1.setFontSize(10);

                        // Line 2: Name (bold and larger font, reduce size if too long)
                        XWPFParagraph paragraph2 = cell.addParagraph();
                        XWPFRun run2 = paragraph2.createRun();
                        run2.setText(cardData.name());
                        run2.setBold(true);
                        run2.setFontSize(nameFontSize);

                        // Reduce spacing after name paragraph
                        if (paragraph2.getCTP().getPPr() == null) {
                            paragraph2.getCTP().addNewPPr();
                        }
                        if (paragraph2.getCTP().getPPr().getSpacing() == null) {
                            paragraph2.getCTP().getPPr().addNewSpacing();
                        }
                        paragraph2.getCTP().getPPr().getSpacing().setAfter(0);

                        // Line 3: Street and street number (medium font)
                        XWPFParagraph paragraph3 = cell.addParagraph();
                        XWPFRun run3 = paragraph3.createRun();
                        run3.setText(cardData.street() + " " + cardData.streetNumber());
                        run3.setFontSize(12);

                        // Reduce spacing before street paragraph
                        if (paragraph3.getCTP().getPPr() == null) {
                            paragraph3.getCTP().addNewPPr();
                        }
                        if (paragraph3.getCTP().getPPr().getSpacing() == null) {
                            paragraph3.getCTP().getPPr().addNewSpacing();
                        }
                        paragraph3.getCTP().getPPr().getSpacing().setBefore(0);

                        log.debug("Added card data for member {} to table {}, cell [{}, {}]",
                                cardData.memberId(), tableIndex + 1, rowIndex, cellIndex);
                    } else {
                        // Add empty paragraph to maintain structure
                        cell.addParagraph();
                    }

                    labelIndex++;
                }
            }
        }

        log.info("=== Successfully populated {} labels across {} pages ===", Math.min(labelIndex, cardDataList.size()), tableCount);
    }

    private void applyBackgroundColor(XWPFTableCell cell) {
        // Ensure cell properties exist
        if (cell.getCTTc().getTcPr() == null) {
            cell.getCTTc().addNewTcPr();
        }

        // Add shading (background color)
        CTShd shd = cell.getCTTc().getTcPr().getShd();
        if (shd == null) {
            shd = cell.getCTTc().getTcPr().addNewShd();
        }

        // Set the background color using the hex value from properties
        shd.setFill(backgroundColor);
        shd.setVal(STShd.CLEAR);
    }

    private void removeTrailingContent(XWPFDocument document) {
        // Get all body elements
        List<IBodyElement> bodyElements = document.getBodyElements();

        log.info("Total body elements before cleanup: {}", bodyElements.size());

        // Find the index of the last table
        int lastTableIndex = -1;
        for (int i = bodyElements.size() - 1; i >= 0; i--) {
            if (bodyElements.get(i).getElementType() == BodyElementType.TABLE) {
                lastTableIndex = i;
                break;
            }
        }

        // Remove ALL elements after the last table (including page breaks)
        if (lastTableIndex != -1 && lastTableIndex < bodyElements.size() - 1) {
            int elementsToRemove = bodyElements.size() - 1 - lastTableIndex;
            log.info("Found last table at index {}, removing {} trailing elements", lastTableIndex, elementsToRemove);

            // Remove elements from the end backwards to avoid index issues
            for (int i = bodyElements.size() - 1; i > lastTableIndex; i--) {
                IBodyElement element = bodyElements.get(i);
                document.removeBodyElement(i);
                log.info("Removed trailing element at index {} (type: {})", i, element.getElementType());
            }
        }

        log.info("Total body elements after cleanup: {}", document.getBodyElements().size());
    }

    private String saveDocument(XWPFDocument document) throws IOException {
        String outputDirectory = cardDataConfig.getOutputDirectory();
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        String filename = String.format("generated_labels_%s.docx", timestamp);

        // Use configured output directory, or fallback to user's Downloads
        Path outputPath;
        if (outputDirectory != null && !outputDirectory.isEmpty()) {
            outputPath = Paths.get(outputDirectory, filename);
            // Create directory if it doesn't exist
            Files.createDirectories(outputPath.getParent());
        } else {
            String userHome = System.getProperty("user.home");
            outputPath = Paths.get(userHome, "Downloads", filename);
        }

        log.info("Saving document to: {}", outputPath);

        try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
            document.write(out);
        } finally {
            document.close();
        }

        return outputPath.toString();
    }
}
