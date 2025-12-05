package com.goris.habcardgenerator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
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

    private List<XWPFTable> allTables = new ArrayList<>();

    public void generateCardLabels(List<String> labelTexts) {
        try {
            log.info("Starting card label generation with {} labels...", labelTexts.size());

            allTables.clear(); // Reset tables list

            XWPFDocument document = loadTemplate();

            analyzeTemplate(document);

            // Store the first table
            allTables.add(document.getTables().get(0));

            // Calculate how many pages we need
            int pagesNeeded = (int) Math.ceil((double) labelTexts.size() / LABELS_PER_PAGE);
            log.info("Pages needed for {} labels: {}", labelTexts.size(), pagesNeeded);

            // Duplicate pages if we need more than one
            if (pagesNeeded > 1) {
                duplicatePages(document, pagesNeeded);
            }

            populateLabels(labelTexts);

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

        try (InputStream inputStream = resource.getInputStream()) {
            return new XWPFDocument(inputStream);
        }
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

    private void populateLabels(List<String> labelTexts) {
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

                    // Use text from input list if available, otherwise leave empty
                    String labelText = labelIndex < labelTexts.size() ? labelTexts.get(labelIndex) : "";

                    // Clear existing content
                    while (cell.getParagraphs().size() > 0) {
                        cell.removeParagraph(0);
                    }

                    // Add new paragraph with label text
                    XWPFParagraph paragraph = cell.addParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(labelText);

                    if (labelIndex < labelTexts.size()) {
                        log.debug("Added text '{}' to table {}, cell [{}, {}]", labelText, tableIndex + 1, rowIndex, cellIndex);
                    }
                    labelIndex++;
                }
            }
        }

        log.info("=== Successfully populated {} labels across {} pages ===", Math.min(labelIndex, labelTexts.size()), tableCount);
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
