package com.calcite_new.core.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationArgumentsConfig {

    public static int VERSION;
    @Bean
    public Integer versionId(ApplicationArguments args) {
        if (!args.containsOption("version") || args.getOptionValues("version").isEmpty()) {
            System.err.println("Error: --version parameter is required");
            System.exit(1);
        }
        VERSION = Integer.parseInt(args.getOptionValues("version").get(0);
        return VERSION;
    }

}
