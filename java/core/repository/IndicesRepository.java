package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.IndicesEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class IndicesRepository {
    private static final Logger logger = LoggerFactory.getLogger(IndicesRepository.class);

    public List<IndicesEntity> fetchAllIndices() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from indices_e");
            return session.createQuery("FROM IndicesEntity", IndicesEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching indices", e);
            throw e;
        }
    }

    public List<IndicesEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding indices for schema: {}", schema);
            return session.createQuery(
                    "FROM IndicesEntity WHERE schema = :schema",
                    IndicesEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding indices by schema: {}", schema, e);
            throw e;
        }
    }

    public List<IndicesEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding indices for database: {}", database);
            return session.createQuery(
                    "FROM IndicesEntity WHERE database = :database",
                    IndicesEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding indices by database: {}", database, e);
            throw e;
        }
    }
}
