SET search_path to {schema};
DROP VIEW IF EXISTS rule_master_vw CASCADE;
CREATE view rule_master_vw AS
 SELECT rm.rule_id,
        rm.rule_title,
        rm.rule_description,
        rm.rule_category,
        rm.version_id,
        CASE WHEN code_refactoring = 1 THEN 'YES'
             ELSE 'NO' END AS query_or_code_change_required,
        CASE WHEN datamodel = 1 THEN 'YES'
             ELSE 'NO' END AS schema_change_required,
        CASE WHEN storage  = 1 THEN  'Storage'
             WHEN performance_effective = 1 THEN 'Performance'
             WHEN storage = 1 AND performance_effective = 1 THEN 'Storage & Performance'
             ELSE 'NA' END AS optimization_category,
        rm.recommendation,
        rt.project_id,
        rt.no_of_queries as query_count
        FROM rule_master rm
        JOIN recommendation_table rt
        ON rm.rule_id = rt.rule_id;