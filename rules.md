# Database Rules Reference

| Rule ID | Rule Name | Description |
|---------|-----------|-------------|
| 1 | Case-insensitive comparison(LOWER/ UPPER) | Case-insensitive comparison(LOWER/UPPER) |
| 2 | Truncate tables | Identify jobs referring delete statements without filter conditions |
| 3 | IN with constants | Queries with constants in IN clause in the WHERE condition |
| 4 | IN clause with subquery | Queries with Subquery in IN clause in the WHERE condition |
| 5 | Frequent failures | Identify Jobs those are failing very frequently |
| 6 | Jobs failing due to resource error | Identify Jobs those are failing due to resource error |
| 7 | High volume scan jobs | Identify Jobs scanning high volume of data (> ~20 GB) |
| 8 | Consolidation of similar updates | Multiple where clauses are used to update a single table |
| 9 | OrderBy clause inside SubQuery | OrderBy clause inside SubQuery |
| 10 | Schema duplication | Table with same DDL is present in multiple Dataset(schema) |
| 11 | Table cloning | Creating copy of table(create table as select * from) |
| 12 | Join performance improvement | Identify If join columns are of string data type |
| 13 | Backup table identification | Identify backup tables |

