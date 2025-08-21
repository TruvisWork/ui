package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "query_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryLog {

    @Id
    @Column(name = "log_id")
    private String logId;

    @Column(name = "source_product")
    private String sourceProduct;

    @Column(name = "database")
    private String database;

    @Column(name = "schema")
    private String schema;

    @Column(name = "sql_query", columnDefinition = "TEXT")
    private String sqlQuery;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "total_execution_time_ms")
    private Long totalExecutionTimeMs;

    @Column(name = "cpu_time_ms")
    private Long cpuTimeMs;

    @Column(name = "application_id")
    private String applicationId;

    @Column(name = "io_count")
    private Long ioCount;

    @Column(name = "is_stored_procedure_call")
    private Boolean isStoredProcedureCall;

    @Column(name = "proc_id")
    private Long procedureId;

    @Column(name = "instance")
    private String instance;

    @Column(name = "is_command")
    private Boolean isCommand;

    @Column(name = "command_entity_type")
    private String commandEntityType;

    @Column(name = "command_kind")
    private String commandCmdKind;

    @Column(name = "command_source")
    private String commandSource;

    @Column(name = "command_destination")
    private String commandDestination;

    @Column(name = "command_schema_json", columnDefinition = "TEXT")
    private String commandSchemaJson;

    @Column(name = "command_create_at")
    private Long commandCreateTime;

    @Column(name = "command_update_at")
    private Long commandUpdateTime;

    @Column(name = "command_entity_name")
    private String commandEntityName;

    @Column(name = "command_view_sql", columnDefinition = "TEXT")
    private String commandViewSql;

    @Column(name = "workload_reservation_id")
    private String workloadReservationId;

    @Column(name = "hardware_util_time_ms")
    private Long hardwareUtilTimeMs;

    @PrePersist
    protected void onCreate() {
        if (startTime == null) {
            startTime = Instant.now().toEpochMilli();
        }
    }
}