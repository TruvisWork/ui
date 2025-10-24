package com.calcite_new.sql.model.entity.context.function;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindowFunctionInfo {
    private String functionName;
    private List<String> partitionByCol;
    private List<String> orderByCol;
    private String alias;
    private Boolean usedInWhere;
}
