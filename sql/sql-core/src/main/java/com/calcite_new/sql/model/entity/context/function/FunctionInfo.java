package com.calcite_new.sql.model.entity.context.function;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FunctionInfo {
    private String function;
    private Boolean isDeterministic;
}
