package com.calcite_new.core.config;

import com.calcite_new.core.entity.ColumnEntity;
import com.calcite_new.core.entity.ExternalTableEntity;
import com.calcite_new.core.entity.FunctionEntity;
import com.calcite_new.core.entity.IndicesEntity;
import com.calcite_new.core.entity.MacroEntity;
import com.calcite_new.core.entity.PartitionEntity;
import com.calcite_new.core.entity.ProcedureEntity;
import com.calcite_new.core.entity.QueryLog;
import com.calcite_new.core.entity.TableEntity;
import com.calcite_new.core.entity.ViewEntity;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class HibernateUtil {
    private static final Logger logger = LoggerFactory.getLogger(HibernateUtil.class);
    public static int PAGE_SIZE = 300;
    public static int BATCH_SIZE = 50;
    public static int PARALLEL_BATCHES = 3;
    public static int PROCESSING_TIMEOUT_MINUTES = 5;
    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            logger.info("Initializing Hibernate SessionFactory");
            Configuration configuration = new Configuration();

            String configPath = System.getProperty("spring.config.location");
            if (configPath == null || configPath.isEmpty()) {
                throw new RuntimeException("External application.yml path must be provided using -Dspring.config.location=file:/path/to/application.yml");
            }

            configPath = configPath.replace("file:", "");

            Yaml yaml = new Yaml();
            Properties hibernateProperties = new Properties();

            try (FileInputStream fis = new FileInputStream(configPath)) {
                Map<String, Object> yamlMap = yaml.load(fis);

                if (yamlMap.containsKey("spring")) {
                    Map<String, Object> spring = (Map<String, Object>) yamlMap.get("spring");

                    if (spring.containsKey("datasource")) {
                        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
                        
                        // Add JDBC driver properties
                        hibernateProperties.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
                        hibernateProperties.setProperty("hibernate.connection.url", (String) datasource.get("url"));
                        
                        // Add Socket Factory properties
                        hibernateProperties.setProperty("hibernate.connection.socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
//                        hibernateProperties.setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
                        
                        Map<String, Object> hikari = (Map<String, Object>) datasource.get("hikari");
                        hibernateProperties.setProperty("hibernate.hikari.maximumPoolSize", 
                            String.valueOf(hikari.get("maximum-pool-size")));
                        hibernateProperties.setProperty("hibernate.hikari.minimumIdle", 
                            String.valueOf(hikari.get("minimum-idle")));
                    }

                    if (spring.containsKey("jpa")) {
                        Map<String, Object> jpa = (Map<String, Object>) spring.get("jpa");
                        
                        // Set Hibernate properties from JPA config
                        Map<String, Object> hibernate = (Map<String, Object>) jpa.get("hibernate");
                        hibernateProperties.setProperty("hibernate.hbm2ddl.auto", 
                            (String) hibernate.get("ddl-auto"));
                        hibernateProperties.setProperty("hibernate.show_sql", 
                            String.valueOf(jpa.get("show-sql")));
                        hibernateProperties.setProperty("hibernate.dialect", 
                            (String) jpa.get("database-platform"));
                        
                        if (jpa.containsKey("properties")) {
                            Map<String, Object> properties = (Map<String, Object>) jpa.get("properties");
                            Map<String, Object> hibernateProps = (Map<String, Object>) properties.get("hibernate");
                            hibernateProperties.setProperty("hibernate.format_sql", 
                                String.valueOf(hibernateProps.get("format_sql")));
                        }
                    }
                }

                if (yamlMap.containsKey("processing")) {
                    Object processingObj = yamlMap.get("processing");
                    if (processingObj instanceof Map) {
                        Map<String, Object> processing = (Map<String, Object>) processingObj;
                        PAGE_SIZE = getIntYaml(processing, "page-size", 300);
                        BATCH_SIZE = getIntYaml(processing, "batch-size", 50);
                        PARALLEL_BATCHES = getIntYaml(processing, "parallel-batches", 3);
                        PROCESSING_TIMEOUT_MINUTES = getIntYaml(processing, "processing-timeout-minutes", 5);
                    } else {
                        logger.warn("'processing' section in YAML is not a map. Using default config values.");
                    }
                }

                configuration.setProperties(hibernateProperties);

                // Add entity mappings
                configuration.addAnnotatedClass(TableEntity.class);
                configuration.addAnnotatedClass(ColumnEntity.class);
                configuration.addAnnotatedClass(ViewEntity.class);
                configuration.addAnnotatedClass(FunctionEntity.class);
                configuration.addAnnotatedClass(ProcedureEntity.class);
                configuration.addAnnotatedClass(MacroEntity.class);
                configuration.addAnnotatedClass(ExternalTableEntity.class);
                configuration.addAnnotatedClass(IndicesEntity.class);
                configuration.addAnnotatedClass(PartitionEntity.class);
                configuration.addAnnotatedClass(QueryLog.class);

            } catch (Exception e) {
                logger.error("Could not load application.yml", e);
                throw new RuntimeException("Could not load application.yml", e);
            }

            logger.info("Building SessionFactory");
            return configuration.buildSessionFactory();
        } catch (Throwable ex) {
            logger.error("Initial SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static int getIntYaml(java.util.Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer for {}: {}. Using default {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        logger.info("Shutting down Hibernate SessionFactory");
        getSessionFactory().close();
    }
}
