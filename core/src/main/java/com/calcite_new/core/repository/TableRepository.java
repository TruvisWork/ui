package com.calcite_new.core.repository;

import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.TableEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TableRepository {
    private static final Logger logger = LoggerFactory.getLogger(TableRepository.class);

    public List<TableEntity> fetchAllTables() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from table_e");
            return session.createQuery("from TableEntity", TableEntity.class).list();
        } catch (Exception e) {
            logger.error("Error fetching tables", e);
            throw e;
        }
    }

    public List<TableEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching tables for schema: {}", schema);
            return session.createQuery(
                    "from TableEntity where schema = :schema",
                    TableEntity.class)
                    .setParameter("schema", schema)
                    .list();
        } catch (Exception e) {
            logger.error("Error fetching tables for schema: {}", schema, e);
            throw e;
        }
    }
}