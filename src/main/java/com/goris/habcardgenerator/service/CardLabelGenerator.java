package com.goris.habcardgenerator.service;

import com.goris.habcardgenerator.model.CardData;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CardLabelGenerator {

    private static final String TEMPLATE_PATH = "Avery_64x34-R.docx";
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int LABELS_PER_PAGE = 24;

    @Value("${label.background.color:FFFFFF}")
    private String backgroundColor;

    private List<XWPFTable> allTables = new ArrayList<>();

    public void generateCardLabels(List<CardData> cardDataList) {
        try {
            log.info("Starting card label generation with {} labels...", cardDataList.size());

            allTables.clear(); // Reset tables list

            XWPFDocument document = loadTemplate();

            analyzeTemplate(document);

            // Store the first table
            allTables.add(document.getTables().get(0));

            // Calculate how many pages we need
            int pagesNeeded = (int) Math.ceil((double) cardDataList.size() / LABELS_PER_PAGE);
            log.info("Pages needed for {} labels: {}", cardDataList.size(), pagesNeeded);

            // Duplicate pages if we need more than one
            if (pagesNeeded > 1) {
                duplicatePages(document, pagesNeeded);
            }

            populateLabels(cardDataList);

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
                        // Line 1: Member ID
                        XWPFParagraph paragraph1 = cell.addParagraph();
                        XWPFRun run1 = paragraph1.createRun();
                        run1.setText(cardData.memberId());
                        run1.setFontSize(10);

                        // Line 2: Name (bold and larger font, reduce size if too long)
                        XWPFParagraph paragraph2 = cell.addParagraph();
                        XWPFRun run2 = paragraph2.createRun();
                        run2.setText(cardData.name());
                        run2.setBold(true);
                        // If name is longer than 25 characters, use same font size as street
                        int nameFontSize = cardData.name().length() > 25 ? 12 : 14;
                        run2.setFontSize(nameFontSize);

                        // Line 3: Street and street number (medium font)
                        XWPFParagraph paragraph3 = cell.addParagraph();
                        XWPFRun run3 = paragraph3.createRun();
                        run3.setText(cardData.street() + " " + cardData.streetNumber());
                        run3.setFontSize(12);

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
        String userHome = System.getProperty("user.home");
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        String filename = String.format("generated_labels_%s.docx", timestamp);
        Path downloadsPath = Paths.get(userHome, "Downloads", filename);

        log.info("Saving document to: {}", downloadsPath);

        try (FileOutputStream out = new FileOutputStream(downloadsPath.toFile())) {
            document.write(out);
        } finally {
            document.close();
        }

        return downloadsPath.toString();
    }
}
