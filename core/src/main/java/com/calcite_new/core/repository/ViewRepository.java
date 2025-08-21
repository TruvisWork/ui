package com.calcite_new.core.repository;

import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.ViewEntity;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ViewRepository {
    private static final Logger logger = LoggerFactory.getLogger(ViewRepository.class);

    public List<ViewEntity> fetchAllViews() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM ViewEntity", ViewEntity.class)
                   .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching views", e);
            throw e;
        }
    }

    public List<ViewEntity> findBySchema(String schema) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM ViewEntity WHERE schema = :schema",
                    ViewEntity.class)
                    .setParameter("schema", schema)
                    .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching views for schema: {}", schema, e);
            throw e;
        }
    }

    public List<ViewEntity> findByDatabase(String database) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM ViewEntity WHERE database = :database",
                    ViewEntity.class)
                    .setParameter("database", database)
                    .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching views for database: {}", database, e);
            throw e;
        }
    }
}
