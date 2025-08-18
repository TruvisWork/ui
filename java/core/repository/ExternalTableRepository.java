package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.ExternalTableEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExternalTableRepository {
    private static final Logger logger = LoggerFactory.getLogger(ExternalTableRepository.class);

    public List<ExternalTableEntity> fetchAllExternalTables() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from external_table_e");
            return session.createQuery("FROM ExternalTableEntity", ExternalTableEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching external tables", e);
            throw e;
        }
    }

    public List<ExternalTableEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding external tables for schema: {}", schema);
            return session.createQuery(
                    "FROM ExternalTableEntity WHERE schema = :schema",
                    ExternalTableEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding external tables by schema: {}", schema, e);
            throw e;
        }
    }

    public List<ExternalTableEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding external tables for database: {}", database);
            return session.createQuery(
                    "FROM ExternalTableEntity WHERE database = :database",
                    ExternalTableEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding external tables by database: {}", database, e);
            throw e;
        }
    }

    public List<ExternalTableEntity> findByProduct(String product) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding external tables for product: {}", product);
            return session.createQuery(
                    "FROM ExternalTableEntity WHERE product = :product",
                    ExternalTableEntity.class)
                .setParameter("product", product)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding external tables by product: {}", product, e);
            throw e;
        }
    }

    public List<ExternalTableEntity> findByType(String type) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding external tables by type: {}", type);
            return session.createQuery(
                    "FROM ExternalTableEntity WHERE type = :type",
                    ExternalTableEntity.class)
                .setParameter("type", type)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding external tables by type: {}", type, e);
            throw e;
        }
    }
}
