package com.goris.habcardgenerator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class CardLabelGenerator {

    private static final String TEMPLATE_PATH = "Avery_64x34-R.docx";
    private static final String OUTPUT_FILENAME = "generated_labels.docx";

    public void generateCardLabels() {
        try {
            log.info("Starting card label generation...");

            XWPFDocument document = loadTemplate();

            analyzeTemplate(document);

            populateLabels(document);

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

    private void populateLabels(XWPFDocument document) {
        log.info("=== Populating Labels ===");

        XWPFTable table = document.getTables().get(0);
        int labelNumber = 1;

        // Iterate through 8 rows and use cells 0, 2, 4 (skipping 1 and 3 which are spacing)
        for (int rowIndex = 0; rowIndex < 8; rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex);

            // Access label cells at positions 0, 2, 4 (3 labels per row)
            int[] labelCellIndices = {0, 2, 4};

            for (int cellIndex : labelCellIndices) {
                XWPFTableCell cell = row.getCell(cellIndex);
                String labelText = String.format("label-%02d", labelNumber);

                // Clear existing content
                cell.removeParagraph(0);

                // Add new paragraph with label text
                XWPFParagraph paragraph = cell.addParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(labelText);

                log.info("Added text '{}' to cell [{}, {}]", labelText, rowIndex, cellIndex);
                labelNumber++;
            }
        }

        log.info("=== Successfully populated {} labels ===", labelNumber - 1);
    }

    private String saveDocument(XWPFDocument document) throws IOException {
        String userHome = System.getProperty("user.home");
        Path downloadsPath = Paths.get(userHome, "Downloads", OUTPUT_FILENAME);

        log.info("Saving document to: {}", downloadsPath);

        try (FileOutputStream out = new FileOutputStream(downloadsPath.toFile())) {
            document.write(out);
        } finally {
            document.close();
        }

        return downloadsPath.toString();
    }
}
