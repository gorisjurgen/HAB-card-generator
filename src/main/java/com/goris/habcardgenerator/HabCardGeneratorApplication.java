package com.goris.habcardgenerator;

import com.goris.habcardgenerator.service.CardLabelGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class HabCardGeneratorApplication implements CommandLineRunner {

    private final CardLabelGenerator cardLabelGenerator;

    public static void main(String[] args) {
        SpringApplication.run(HabCardGeneratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        cardLabelGenerator.generateCardLabels();
    }

}
