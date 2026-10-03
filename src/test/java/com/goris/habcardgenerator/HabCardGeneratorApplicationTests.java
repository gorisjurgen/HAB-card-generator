package com.goris.habcardgenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest
class HabCardGeneratorApplicationTests {

    /**
     * The application is a CommandLineRunner, so loading the context runs the import.
     * Point it at an empty temporary directory instead of the configured production path.
     */
    @DynamicPropertySource
    static void useTemporaryDirectories(DynamicPropertyRegistry registry) throws IOException {
        Path tempDir = Files.createTempDirectory("hab-card-generator-test");
        registry.add("card.data.inputDirectory", tempDir::toString);
        registry.add("card.data.outputDirectory", tempDir::toString);
    }

    @Test
    void contextLoads() {
    }
}
