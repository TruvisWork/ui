package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.ProcedureEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProcedureRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProcedureRepository.class);

    public List<ProcedureEntity> fetchAllProcedures() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from procedure_e");
            return session.createQuery("FROM ProcedureEntity", ProcedureEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching procedures", e);
            throw e;
        }
    }

    public List<ProcedureEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding procedures for schema: {}", schema);
            return session.createQuery(
                    "FROM ProcedureEntity WHERE schema = :schema",
                    ProcedureEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding procedures by schema: {}", schema, e);
            throw e;
        }
    }

    public List<ProcedureEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding procedures for database: {}", database);
            return session.createQuery(
                    "FROM ProcedureEntity WHERE database = :database",
                    ProcedureEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding procedures by database: {}", database, e);
            throw e;
        }
    }
}
