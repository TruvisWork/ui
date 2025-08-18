package com.calcite_new.core.repository;

import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.MacroEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MacroRepository {
    private static final Logger logger = LoggerFactory.getLogger(MacroRepository.class);

    public List<MacroEntity> fetchAllMacros() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from macro_e");
            return session.createQuery("FROM MacroEntity", MacroEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching macros", e);
            throw e;
        }
    }

    public List<MacroEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding macros for schema: {}", schema);
            return session.createQuery(
                    "FROM MacroEntity WHERE schema = :schema",
                    MacroEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding macros by schema: {}", schema, e);
            throw e;
        }
    }

    public List<MacroEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding macros for database: {}", database);
            return session.createQuery(
                    "FROM MacroEntity WHERE database = :database",
                    MacroEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding macros by database: {}", database, e);
            throw e;
        }
    }
}
