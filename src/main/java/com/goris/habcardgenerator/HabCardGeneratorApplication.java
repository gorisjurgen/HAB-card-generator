package com.goris.habcardgenerator;

import com.goris.habcardgenerator.model.CardData;
import com.goris.habcardgenerator.service.CardDataService;
import com.goris.habcardgenerator.service.CardLabelGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
public class HabCardGeneratorApplication implements CommandLineRunner {

    private final CardLabelGenerator cardLabelGenerator;
    private final CardDataService cardDataService;

    public static void main(String[] args) {
        SpringApplication.run(HabCardGeneratorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // Import card data from Excel
        List<CardData> cardDataList = cardDataService.importCardData();
    }

}
