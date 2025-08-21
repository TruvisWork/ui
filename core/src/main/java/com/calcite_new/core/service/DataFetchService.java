package com.calcite_new.core.service;


import com.calcite_new.core.entity.*;
import com.calcite_new.core.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class DataFetchService {
    private static final Logger logger = LoggerFactory.getLogger(DataFetchService.class);
    
    private final QueryLogRepository queryLogRepository;
    private final TableRepository tableRepository;
    private final ColumnRepository columnRepository;
    private final ViewRepository viewRepository;
    private final ExternalTableRepository externalTableRepository;
    private final FunctionRepository functionRepository;
    private final IndicesRepository indicesRepository;
    private final MacroRepository macroRepository;
    private final PartitionRepository partitionRepository;
    private final ProcedureRepository procedureRepository;

    public DataFetchService(
            QueryLogRepository queryLogRepository,
            TableRepository tableRepository,
            ColumnRepository columnRepository,
            ViewRepository viewRepository,
            ExternalTableRepository externalTableRepository,
            FunctionRepository functionRepository,
            IndicesRepository indicesRepository,
            MacroRepository macroRepository,
            PartitionRepository partitionRepository,
            ProcedureRepository procedureRepository) {
        this.queryLogRepository = queryLogRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.viewRepository = viewRepository;
        this.externalTableRepository = externalTableRepository;
        this.functionRepository = functionRepository;
        this.indicesRepository = indicesRepository;
        this.macroRepository = macroRepository;
        this.partitionRepository = partitionRepository;
        this.procedureRepository = procedureRepository;
    }

    /**
     * Retrieves all query logs from the database.
     *
     * @return List< of QueryExecutionLog objects
     */
    public List<QueryLog> getAllQueryLogs() {
        return queryLogRepository.fetchAllLogs();
    }

    /**
     * Retrieves query logs by specific query text.
     *
     * @param queryText the text to search for
     * @return List< of matching QueryExecutionLog objects
     */
    public List<QueryLog> getQueryLogsByText(String queryText) {
        logger.info("Fetching query logs containing text: {}", queryText);
        return queryLogRepository.findByQueryText(queryText);
    }

    public List<QueryLog> getQueryLogsPage(int page, int size) {
        return queryLogRepository.findAll(page, size);
    }

    /**
     * Saves a new query log entry.
     *
     * @param queryLog the QueryExecutionLog to save
     * @return the saved QueryExecutionLog with generated ID
     */
    public QueryLog saveQueryLog(QueryLog queryLog) {
        logger.info("Saving new query log entry");
        return queryLogRepository.save(queryLog);
    }

    /**
     * Retrieves all tables from the database.
     *
     * @return List of TableEntity objects
     */
    public List<TableEntity> getAllTables() {
        return tableRepository.fetchAllTables();
    }

    /**
     * Retrieves tables for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of TableEntity objects for the specified schema
     */
    public List<TableEntity> getTablesForSchema(String schema) {
        logger.info("Fetching tables for schema: {}", schema);
        return tableRepository.findBySchema(schema);
    }

    /**
     * Retrieves all columns from the database.
     *
     * @return List< of ColumnEntity objects
     */
    public List<ColumnEntity> getAllColumns() {
        return columnRepository.fetchAllColumns();
    }

    /**
     * Retrieves columns for a specific table.
     *
     * @param tableName the name of the table
     * @return List< of ColumnEntity objects for the specified table
     */
    public List<ColumnEntity> getColumnsForTable(String tableName) {
        logger.info("Fetching columns for table: {}", tableName);
        return columnRepository.findByTableName(tableName);
    }

    /**
     * Retrieves all views from the database.
     *
     * @return List< of ViewEntity objects
     */
    public List<ViewEntity> getAllViews() {
        return viewRepository.fetchAllViews();
    }

    /**
     * Retrieves views for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of ViewEntity objects for the specified schema
     */
    public List<ViewEntity> getViewsBySchema(String schema) {
        logger.info("Fetching views for schema: {}", schema);
        return viewRepository.findBySchema(schema);
    }

    /**
     * Retrieves views for a specific database.
     *
     * @param database the name of the database
     * @return List< of ViewEntity objects for the specified database
     */
    public List<ViewEntity> getViewsByDatabase(String database) {
        logger.info("Fetching views for database: {}", database);
        return viewRepository.findByDatabase(database);
    }

    /**
     * Retrieves all external tables from the database.
     *
     * @return List< of ExternalTableEntity objects
     */
    public List<ExternalTableEntity> getAllExternalTables() {
        return externalTableRepository.fetchAllExternalTables();
    }

    /**
     * Retrieves external tables by specific schema.
     *
     * @param schema the schema name to search for
     * @return List< of matching ExternalTableEntity objects
     */
    public List<ExternalTableEntity> getExternalTablesBySchema(String schema) {
        logger.info("Fetching external tables for schema: {}", schema);
        return externalTableRepository.findBySchema(schema);
    }

    /**
     * Retrieves external tables by specific database.
     *
     * @param database the database name to search for
     * @return List< of matching ExternalTableEntity objects
     */
    public List<ExternalTableEntity> getExternalTablesByDatabase(String database) {
        logger.info("Fetching external tables for database: {}", database);
        return externalTableRepository.findByDatabase(database);
    }

    /**
     * Retrieves external tables by specific product.
     *
     * @param product the product name to search for
     * @return List< of matching ExternalTableEntity objects
     */
    public List<ExternalTableEntity> getExternalTablesByProduct(String product) {
        logger.info("Fetching external tables for product: {}", product);
        return externalTableRepository.findByProduct(product);
    }

    /**
     * Retrieves external tables by specific type.
     *
     * @param type the type to search for
     * @return List< of matching ExternalTableEntity objects
     */
    public List<ExternalTableEntity> getExternalTablesByType(String type) {
        logger.info("Fetching external tables by type: {}", type);
        return externalTableRepository.findByType(type);
    }

    /**
     * Retrieves all functions from the database.
     *
     * @return List< of FunctionEntity objects
     */
    public List<FunctionEntity> getAllFunctions() {
        return functionRepository.fetchAllFunctions();
    }

    /**
     * Retrieves functions for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of FunctionEntity objects for the specified schema
     */
    public List<FunctionEntity> getFunctionsBySchema(String schema) {
        logger.info("Fetching functions for schema: {}", schema);
        return functionRepository.findBySchema(schema);
    }

    /**
     * Retrieves functions for a specific database.
     *
     * @param database the name of the database
     * @return List< of FunctionEntity objects for the specified database
     */
    public List<FunctionEntity> getFunctionsByDatabase(String database) {
        logger.info("Fetching functions for database: {}", database);
        return functionRepository.findByDatabase(database);
    }

    /**
     * Retrieves all indices from the database.
     *
     * @return List< of IndicesEntity objects
     */
    public List<IndicesEntity> getAllIndices() {
        return indicesRepository.fetchAllIndices();
    }

    /**
     * Retrieves indices for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of IndicesEntity objects for the specified schema
     */
    public List<IndicesEntity> getIndicesBySchema(String schema) {
        logger.info("Fetching indices for schema: {}", schema);
        return indicesRepository.findBySchema(schema);
    }

    /**
     * Retrieves indices for a specific database.
     *
     * @param database the name of the database
     * @return List< of IndicesEntity objects for the specified database
     */
    public List<IndicesEntity> getIndicesByDatabase(String database) {
        logger.info("Fetching indices for database: {}", database);
        return indicesRepository.findByDatabase(database);
    }

    /**
     * Retrieves all macros from the database.
     *
     * @return List< of MacroEntity objects
     */
    public List<MacroEntity> getAllMacros() {
        return macroRepository.fetchAllMacros();
    }

    /**
     * Retrieves macros for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of MacroEntity objects for the specified schema
     */
    public List<MacroEntity> getMacrosBySchema(String schema) {
        logger.info("Fetching macros for schema: {}", schema);
        return macroRepository.findBySchema(schema);
    }

    /**
     * Retrieves macros for a specific database.
     *
     * @param database the name of the database
     * @return List< of MacroEntity objects for the specified database
     */
    public List<MacroEntity> getMacrosByDatabase(String database) {
        logger.info("Fetching macros for database: {}", database);
        return macroRepository.findByDatabase(database);
    }

    /**
     * Retrieves all partitions from the database.
     *
     * @return List< of PartitionEntity objects
     */
    public List<PartitionEntity> getAllPartitions() {
        return partitionRepository.fetchAllPartitions();
    }

    /**
     * Retrieves partitions for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of PartitionEntity objects for the specified schema
     */
    public List<PartitionEntity> getPartitionsBySchema(String schema) {
        logger.info("Fetching partitions for schema: {}", schema);
        return partitionRepository.findBySchema(schema);
    }

    /**
     * Retrieves partitions for a specific database.
     *
     * @param database the name of the database
     * @return List< of PartitionEntity objects for the specified database
     */
    public List<PartitionEntity> getPartitionsByDatabase(String database) {
        logger.info("Fetching partitions for database: {}", database);
        return partitionRepository.findByDatabase(database);
    }

    /**
     * Retrieves partitions for a specific table.
     *
     * @param tableName the name of the table
     * @return List< of PartitionEntity objects for the specified table
     */
    public List<PartitionEntity> getPartitionsByTable(String tableName) {
        logger.info("Fetching partitions for table: {}", tableName);
        return partitionRepository.findByTableName(tableName);
    }

    /**
     * Retrieves all procedures from the database.
     *
     * @return List< of ProcedureEntity objects
     */
    public List<ProcedureEntity> getAllProcedures() {
        return procedureRepository.fetchAllProcedures();
    }

    /**
     * Retrieves procedures for a specific schema.
     *
     * @param schema the name of the schema
     * @return List< of ProcedureEntity objects for the specified schema
     */
    public List<ProcedureEntity> getProceduresBySchema(String schema) {
        logger.info("Fetching procedures for schema: {}", schema);
        return procedureRepository.findBySchema(schema);
    }

    /**
     * Retrieves procedures for a specific database.
     *
     * @param database the name of the database
     * @return List< of ProcedureEntity objects for the specified database
     */
    public List<ProcedureEntity> getProceduresByDatabase(String database) {
        logger.info("Fetching procedures for database: {}", database);
        return procedureRepository.findByDatabase(database);
    }
}