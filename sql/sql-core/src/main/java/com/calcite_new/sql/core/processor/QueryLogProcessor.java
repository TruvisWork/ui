package com.calcite_new.sql.core.processor;

import com.calcite_new.core.entity.QueryLog;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.sql.core.processor.utils.SqlStatementUtils;
import com.calcite_new.sql.core.processor.visitor.SqlNodeVisitor;
import com.calcite_new.sql.model.entity.SqlStatementInfo;
import com.calcite_new.sql.model.enums.StatementStatus;
import com.calcite_new.sql.parser.BigQuerySqlParser;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.calcite_new.core.service.EntityCatalogBuilder;
import com.calcite_new.core.config.RepositoryConfig;
import com.calcite_new.core.model.EntityCatalog;

/**
 * QueryLogProcessor is responsible for processing QueryLog object
 * and extracting SqlStatementInfo from them.
 */
@Component
public class QueryLogProcessor {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryLogProcessor.class);

  private final BigQuerySqlParser sqlParser;
  private final EntityResolver entityResolver;
  private final BigQuerySqlDialect bigQuerySqlDialect;
  private final EntityCatalog entityCatalog;


  public QueryLogProcessor() {
    this.sqlParser = new BigQuerySqlParser();
    RepositoryConfig repositoryConfig = new RepositoryConfig();
    EntityCatalogBuilder catalogBuilder = new EntityCatalogBuilder(repositoryConfig);
    this.entityCatalog = catalogBuilder.build();
    this.entityResolver = new EntityResolver(entityCatalog);
    this.bigQuerySqlDialect = new BigQuerySqlDialect();
  }

  public List<SqlStatementInfo> process(List<QueryLog> records) {
    List<SqlStatementInfo> results = new ArrayList<>(records.size());

    for (QueryLog queryLog : records) {
      SqlStatementInfo model = initializeModel(queryLog);

      String rawQuery = queryLog.getSqlQuery().trim();
      if (QueryLogProcessor.isTransactionalStatement(rawQuery)) {
        model.setStatementStatus(StatementStatus.IGNORED);
        results.add(model);
        continue;
      }

      SqlNode sqlNode;

      try {
        sqlNode = sqlParser.parse(queryLog.getSqlQuery());
        // if  (sqlNode == null) {
        //   throw new Exception("Unable to parse SQL: " + queryLog.getSqlQuery());
        // }
      } catch (Exception e) {
        model.setStatementStatus(StatementStatus.PARSE_ERROR);
        handleParseError(model, e, queryLog.getLogId());
        results.add(model);
        continue;
      }

      try {
        if (sqlNode!= null && SqlStatementUtils.isIgnored(sqlNode.getKind().name())) {
          model.setStatementStatus(StatementStatus.IGNORED);
          model.setErrorDescription((Exception) null);
        } else {
          RelationshipExtractor relationshipExtractor = new RelationshipExtractor(entityResolver, bigQuerySqlDialect);
          SqlNodeVisitor visitor = new SqlNodeVisitor(
                  queryLog.getUserName(),
                  queryLog.getDatabase(),
                  queryLog.getSchema(),
                  relationshipExtractor,
                  entityCatalog
          );
          SqlNodeVisitor.Result result = sqlNode.accept(visitor);
          populateModel(model, result);
        }
      } catch (Exception e) {
        model.setStatementStatus(StatementStatus.ERROR);
        handleVisitorError(model, e, queryLog.getLogId());
      }

      results.add(model);
    }

    return results;
  }

  private void handleParseError(SqlStatementInfo model, Exception e, String logId) {
    model.setStatementStatus(StatementStatus.PARSE_ERROR);
    model.setErrorDescription(e);
    log.error("Parsing error for logId: {}", logId, e.getMessage());
  }

  private void handleVisitorError(SqlStatementInfo model, Exception e, String logId) {
    model.setStatementStatus(StatementStatus.ERROR);
    model.setErrorDescription(e);
    log.error("Error during SQL processing for logId: {}", logId, e.getMessage());
  }

  private SqlStatementInfo initializeModel(QueryLog queryLog) {
    SqlStatementInfo model = new SqlStatementInfo();
    model.setProduct(queryLog.getSourceProduct());
    model.setLogId(queryLog.getLogId());
    model.setStatementId(1);
    model.setQueryExecutionTime(queryLog.getTotalExecutionTimeMs());
    model.setInstance(queryLog.getInstance());
    model.setDatabase(queryLog.getDatabase());
    model.setSchema(queryLog.getSchema());
    model.setSessionId(queryLog.getSessionId());
    model.setUserName(queryLog.getUserName());
    model.setVersionId(queryLog.getVersionId());
    return model;
  }

  private void populateModel(SqlStatementInfo model, SqlNodeVisitor.Result result) {
    model.setStatementStatus(StatementStatus.SUCCESS);
    model.setStatementType(result.getStatementType());
    model.setStatementContext(result.getContext());
    model.setEntityRelationships(result.getEntityRelationships());
  }

  private static boolean isTransactionalStatement(String sql) {
    final Pattern TRANSACTIONAL_PATTERN =
            Pattern.compile("^(ROLLBACK|BEGIN(\\s+TRANSACTION)?|COMMIT)(\\s+TRANSACTION)?.*", Pattern.CASE_INSENSITIVE);
    return TRANSACTIONAL_PATTERN.matcher(sql.trim()).matches();
  }

}
