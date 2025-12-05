package com.goris.habcardgenerator;

import com.goris.habcardgenerator.service.CardLabelGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
public class HabCardGeneratorApplication implements CommandLineRunner {

    private final CardLabelGenerator cardLabelGenerator;

    public static void main(String[] args) {
        SpringApplication.run(HabCardGeneratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // Create test data with custom labels
        List<String> labelTexts = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            labelTexts.add(String.format("Custom Label %d", i));
        }

        cardLabelGenerator.generateCardLabels(labelTexts);
    }

}
