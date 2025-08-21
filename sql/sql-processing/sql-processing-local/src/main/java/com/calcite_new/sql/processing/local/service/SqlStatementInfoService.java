package com.calcite_new.sql.processing.local.service;

import com.calcite_new.sql.model.entity.SqlStatementInfo;
import com.calcite_new.sql.processing.local.repository.SqlStatementInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SqlStatementInfoService {

    private final SqlStatementInfoRepository repository;

    public void saveAll(List<SqlStatementInfo> records) {
        repository.saveAll(records);
    }
}
