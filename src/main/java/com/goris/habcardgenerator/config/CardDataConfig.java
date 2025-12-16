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
    private String inputDirectory;
    private String outputDirectory;
    private String year;

    @Getter
    @Setter
    public static class Columns {
        private String memberId;
        private String name;
        private String street;
        private String streetNumber;
        private String bus;
    }
}
