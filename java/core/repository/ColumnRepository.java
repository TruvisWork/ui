package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.ColumnEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ColumnRepository {
    private static final Logger logger = LoggerFactory.getLogger(ColumnRepository.class);

    public List<ColumnEntity> fetchAllColumns() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                "FROM ColumnEntity", 
                ColumnEntity.class)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching columns", e);
            throw e;
        }
    }

    public List<ColumnEntity> findByTableName(String tableName) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                "FROM ColumnEntity WHERE table = :tableName",
                ColumnEntity.class)
                .setParameter("tableName", tableName)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching columns for table: {}", tableName, e);
            throw e;
        }
    }

    public List<ColumnEntity> findBySchemaAndTable(String schema, String tableName) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                "FROM ColumnEntity WHERE schema = :schema AND table = :tableName",
                ColumnEntity.class)
                .setParameter("schema", schema)
                .setParameter("tableName", tableName)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching columns for schema: {} and table: {}", schema, tableName, e);
            throw e;
        }
    }

    public List<ColumnEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                "FROM ColumnEntity WHERE schema = :schema",
                ColumnEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching columns for schema: {}", schema, e);
            throw e;
        }
    }
}