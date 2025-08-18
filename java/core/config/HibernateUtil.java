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
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateUtil {
    private static final Logger logger = LoggerFactory.getLogger(HibernateUtil.class);
    private static final SessionFactory sessionFactory = buildSessionFactory();

    public static int PAGE_SIZE = 300;
    public static int BATCH_SIZE = 50;
    public static int PARALLEL_BATCHES = 3;
    public static int PROCESSING_TIMEOUT_MINUTES = 5;

    private static SessionFactory buildSessionFactory() {
        try {
            logger.info("Initializing Hibernate SessionFactory");
            Configuration configuration = new Configuration();
            Properties props = new Properties();

            try {
                Throwable var2 = null;
                Object var3 = null;

                try {
                    InputStream is = HibernateUtil.class.getClassLoader().getResourceAsStream("application.properties");

                    try {
                        if (is == null) {
                            throw new RuntimeException("Cannot find application.properties in classpath");
                        }

                        props.load(is);
                        logger.debug("Loaded application.properties from classpath successfully");
                        configuration.setProperties(props);
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
                        String mappingClasses = props.getProperty("hibernate.mapping.classes", "");
                        if (!mappingClasses.isEmpty()) {
                            String[] var9;
                            for(String className : var9 = mappingClasses.split(",")) {
                                try {
                                    if (!className.trim().isEmpty()) {
                                        Class<?> entityClass = Class.forName(className.trim());
                                        configuration.addAnnotatedClass(entityClass);
                                    }
                                } catch (ClassNotFoundException e) {
                                    logger.error("Could not load entity class: {}", className, e);
                                    throw new RuntimeException("Failed to load entity class: " + className, e);
                                }
                            }
                        }
                        PAGE_SIZE = getIntProperty(props, "processing.page-size", 300);
                        BATCH_SIZE = getIntProperty(props, "processing.batch-size", 50);
                        PARALLEL_BATCHES = getIntProperty(props, "processing.parallel-batches", 3);
                        PROCESSING_TIMEOUT_MINUTES = getIntProperty(props, "processing.processing-timeout-minutes", 5);
                    } finally {
                        if (is != null) {
                            is.close();
                        }

                    }
                } catch (Throwable var23) {
                    if (var2 == null) {
                        var2 = var23;
                    } else if (var2 != var23) {
                        var2.addSuppressed(var23);
                    }

                    throw var2;
                }
            } catch (IOException e) {
                logger.error("Could not load application.properties", e);
                throw new RuntimeException("Could not load application.properties", e);
            }

            logger.info("Building SessionFactory");
            return configuration.buildSessionFactory();
        } catch (Throwable ex) {
            logger.error("Initial SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
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
