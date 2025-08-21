package com.calcite_new.sql.processing.local;

import com.calcite_new.sql.processing.local.service.QueryLogProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Collections;

@SpringBootApplication(scanBasePackages = {
        "com.calcite_new.sql.core.processor",
        "com.calcite_new.sql.processing.local",
        "com.calcite_new.core"
})
@EntityScan(basePackages = {
        "com.calcite_new.sql.model.entity",
        "com.calcite_new.core.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.calcite_new.sql.processing.local.repository",
        "com.calcite_new.core.repository"
})

@EnableTransactionManagement
@Slf4j
public class QueryLogProcessingApplication {

    public static void main(String[] args) {
        log.info("--- Starting Query Log Processing Application ---");
        try {
            SpringApplication app = new SpringApplication(QueryLogProcessingApplication.class);
            app.setDefaultProperties(Collections.emptyMap());

            try (ConfigurableApplicationContext context = app.run(args)) {
                QueryLogProcessingService processingService = context.getBean(QueryLogProcessingService.class);
                processingService.processQueryLogs();
                log.info("--- Query log processing completed successfully ---");
            }
        } catch (Exception e) {
            log.error("--- Error during query log processing: {} ----", e.getMessage(), e);
            System.exit(1);
        }
    }
}