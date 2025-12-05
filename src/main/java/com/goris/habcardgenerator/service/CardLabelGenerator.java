package com.goris.habcardgenerator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
