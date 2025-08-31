SET search_path to {schema};
DROP TABLE IF EXISTS rule_master CASCADE;

CREATE TABLE rule_master (
    rule_id INTEGER,
    {version} AS version_id BIGINT,
    rule_title TEXT,
    rule_description TEXT,
    rule_category TEXT,
    created_date DATE,
    created_by TEXT,
    modified_date DATE,
    modified_by TEXT,
    comments TEXT,
    is_active INTEGER,
    performance INTEGER,
    storage INTEGER,
    datamodel INTEGER,
    code_refactoring INTEGER,
    recommendation TEXT,
    cost_effective INTEGER,
    performance_effective INTEGER,
    priority INTEGER
);

INSERT INTO rule_master (
    rule_id, rule_title, rule_description, rule_category, created_date, created_by,
    modified_date, modified_by, comments, is_active, performance, storage, datamodel,
    code_refactoring, recommendation, cost_effective, performance_effective, priority)
VALUES
-- 1
(1, 'Case-insensitive comparison(LOWER/ UPPER)', 'Case-insensitive comparison(LOWER/UPPER)', 'Script/Configuration', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 0, 1, 0, 0, 1, 'Use case-insensitive collation', 1, 1, 1),
-- 2
(2, 'Truncate tables', 'Identify jobs referring delete statements without filter conditions', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 0, 0, 1, 'Use Truncate ,when delete is happening without filter condition', 1, 1, 1),
-- 3
(3, 'IN with constants', 'Queries with constants in IN clause in the WHERE condition', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 0, 0, 1, 'Use UNNEST with arrays or reference lookup tables instead of long IN list', 1, 1, 1),
-- 4
(4, 'IN clause with subquery', 'Queries with Subquery in IN clause in the WHERE condition', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 0, 0, 1, 'Prefer  to use JOIN or EXISTS instead of IN with subqueries to improve query performance', 1, 1, 1),
-- 5
(5, 'Frequent failures', 'Identify Jobs those are failing very frequently', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 0, 0, 1, 1, 'Investigate root causes, refactor query logic, and add monitoring/alerts for failed jobs', 1, 1, 1),
-- 6
(6, 'Jobs failing due to resource error', 'Identify Jobs those are failing due to resource error', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 1, 1, 1, 'Remove data skewness and refactor the query', 1, 1, 3),
-- 7
(7, 'High volume scan jobs', 'Identify Jobs scanning high volume of data (> ~20 GB)', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 1, 1, 1, 'Code refactoring and Use column pruning, filters, and table partitioning to minimize scanned data', 1, 1, 3),
-- 8
(8, 'Consolidation of similar updates', 'Multiple where clauses are used to update a single table', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 1, 0, 1, 'Combine updates using CASE logic inside a single statement', 1, 1, 2),
-- 9
(9, 'OrderBy clause inside SubQuery', 'OrderBy clause inside SubQuery', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 0, 1, 0, 1, 0, 'Code refactoring', 1, 1, 1),
-- 10
(10, 'Schema duplication', 'Table with same DDL is present in multiple Dataset(schema)', 'Table', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 0, 1, 1, 1, 'Recommend to drop copies of table and create views for different purposes', 1, 0, 1),
-- 11
(11, 'Table cloning', 'Creating copy of table(create table as select * from)', 'Script', CURRENT_DATE, 'admin',
 NULL, NULL, NULL, 1, 1, 1, 1, 1, 'Recommend to use table clone instead of creating new copy', 1, 1, 1);


