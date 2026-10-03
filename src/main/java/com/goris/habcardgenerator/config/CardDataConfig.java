package com.goris.habcardgenerator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "card.data")
@Getter
@Setter
public class CardDataConfig {

    private Columns columns;
    private boolean splitOddEven;
    private boolean useNewLine;
    private boolean smallStreetsLast;
    /** Convert names that are entirely upper case ("JAN KERREMANS") to title case ("Jan Kerremans"). */
    private boolean titleCaseUpperCaseNames = true;
    private String inputDirectory;
    private String outputDirectory;
    private String year;

    /**
     * Header names of the columns in the Assistonline member export.
     * The address column holds street, house number and bus in one cell and is split by the importer.
     */
    @Getter
    @Setter
    public static class Columns {
        private String memberId;
        private String name;
        private String address;
    }
}
