package com.calcite_new.core.config;

import com.calcite_new.core.repository.*;

public class RepositoryConfig {
    private final QueryLogRepository queryLogRepo;
    private final TableRepository tableRepo;
    private final ColumnRepository columnRepo;
    private final ViewRepository viewRepo;
    private final ExternalTableRepository externalTableRepo;
    private final FunctionRepository functionRepo;
    private final IndicesRepository indicesRepo;
    private final MacroRepository macroRepo;
    private final PartitionRepository partitionRepo;
    private final ProcedureRepository procedureRepo;

    public RepositoryConfig() {
        this.queryLogRepo = new QueryLogRepository();
        this.tableRepo = new TableRepository();
        this.columnRepo = new ColumnRepository();
        this.viewRepo = new ViewRepository();
        this.externalTableRepo = new ExternalTableRepository();
        this.functionRepo = new FunctionRepository();
        this.indicesRepo = new IndicesRepository();
        this.macroRepo = new MacroRepository();
        this.partitionRepo = new PartitionRepository();
        this.procedureRepo = new ProcedureRepository();
    }

    public QueryLogRepository getQueryLogRepo() { return queryLogRepo; }
    public TableRepository getTableRepo() { return tableRepo; }
    public ColumnRepository getColumnRepo() { return columnRepo; }
    public ViewRepository getViewRepo() { return viewRepo; }
    public ExternalTableRepository getExternalTableRepo() { return externalTableRepo; }
    public FunctionRepository getFunctionRepo() { return functionRepo; }
    public IndicesRepository getIndicesRepo() { return indicesRepo; }
    public MacroRepository getMacroRepo() { return macroRepo; }
    public PartitionRepository getPartitionRepo() { return partitionRepo; }
    public ProcedureRepository getProcedureRepo() { return procedureRepo; }
}
