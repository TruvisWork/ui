package com.calcite_new.core.repository;


import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.QueryLog;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for handling query_log_e table
 */
@Repository
public class QueryLogRepository {
    private static final Logger logger = LoggerFactory.getLogger(QueryLogRepository.class);

    public List<QueryLog> fetchAllLogs() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching all records from QueryLog");
            return session.createQuery("from QueryLog", QueryLog.class).getResultList();
        } catch (Exception e) {
            logger.error("Error fetching query logs", e);
            throw e;
        }
    }

    /**
     * Find query logs containing specific text
     *
     * @param queryText text to search for
     * @return List of matching QueryLog objects
     */
    public List<QueryLog> findByQueryText(String queryText) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Searching for query logs containing text: {}", queryText);
            return session.createQuery(
                    "from QueryLog where sql_text like :queryText", 
                    QueryLog.class)
                .setParameter("queryText", "%" + queryText + "%")
                .getResultList();
        } catch (Exception e) {
            logger.error("Error searching query logs by text: {}", queryText, e);
            throw e;
        }
    }

    public List<QueryLog> findAll(int page, int size) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            logger.info("Fetching page {} of size {} from QueryLog", page, size);
            return session.createQuery("from QueryLog", QueryLog.class)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        } catch (Exception e) {
            logger.error("Error fetching paged query logs", e);
            throw e;
        }
    }
    /**
     * Save a new query log entry
     *
     * @param queryLog the entity to save
     * @return the saved entity with generated ID
     */
    public QueryLog save(QueryLog queryLog) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                session.persist(queryLog);
                transaction.commit();
                logger.info("Successfully saved query log with ID: {}", queryLog.getLogId());
                return queryLog;
            } catch (Exception e) {
                if (transaction != null && transaction.isActive()) {
                    transaction.rollback();
                }
                logger.error("Error saving query log", e);
                throw e;
            }
        }
    }
}
