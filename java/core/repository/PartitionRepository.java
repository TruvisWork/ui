package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.calcite_new.core.entity.PartitionEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PartitionRepository {
    private static final Logger logger = LoggerFactory.getLogger(PartitionRepository.class);

    public List<PartitionEntity> fetchAllPartitions() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from partition_e");
            return session.createQuery("FROM PartitionEntity", PartitionEntity.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching partitions", e);
            throw e;
        }
    }

    public List<PartitionEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding partitions for schema: {}", schema);
            return session.createQuery(
                    "FROM PartitionEntity WHERE schema = :schema",
                    PartitionEntity.class)
                .setParameter("schema", schema)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding partitions by schema: {}", schema, e);
            throw e;
        }
    }

    public List<PartitionEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding partitions for database: {}", database);
            return session.createQuery(
                    "FROM PartitionEntity WHERE database = :database",
                    PartitionEntity.class)
                .setParameter("database", database)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding partitions by database: {}", database, e);
            throw e;
        }
    }

    public List<PartitionEntity> findByTableName(String tableName) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Finding partitions for table: {}", tableName);
            return session.createQuery(
                    "FROM PartitionEntity WHERE tableName = :tableName",
                    PartitionEntity.class)
                .setParameter("tableName", tableName)
                .getResultList();
        } catch (Exception e) {
            logger.error("Error finding partitions by table name: {}", tableName, e);
            throw e;
        }
    }
}
