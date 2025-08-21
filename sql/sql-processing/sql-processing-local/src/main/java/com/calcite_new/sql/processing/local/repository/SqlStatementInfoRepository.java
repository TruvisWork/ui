package com.calcite_new.sql.processing.local.repository;

import com.calcite_new.sql.model.entity.SqlStatementInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SqlStatementInfoRepository extends JpaRepository<SqlStatementInfo, String> {
}
