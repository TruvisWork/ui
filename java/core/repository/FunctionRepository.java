package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.FunctionEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FunctionRepository {
    private static final Logger logger = LoggerFactory.getLogger(FunctionRepository.class);

    public List<FunctionEntity> fetchAllFunctions() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from function_e");
            return session.createQuery("FROM FunctionEntity", FunctionEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching functions", e);
            throw e;
        }
    }

    public List<FunctionEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding functions for schema: {}", schema);
            return session.createQuery(
                    "FROM FunctionEntity WHERE schema = :schema",
                    FunctionEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding functions by schema: {}", schema, e);
            throw e;
        }
    }

    public List<FunctionEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding functions for database: {}", database);
            return session.createQuery(
                    "FROM FunctionEntity WHERE database = :database",
                    FunctionEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding functions by database: {}", database, e);
            throw e;
        }
    }
}
