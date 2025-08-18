package com.calcite_new.sql.core.processor.utils;

import org.apache.calcite.sql.*;

public class InClauseAnalyzer {

    public static class InClauseInfo {
        private boolean hasInWithSubquery = false;
        private boolean hasInWithConstant = false;

        public boolean hasInWithSubquery() {
            return hasInWithSubquery;
        }

        public boolean hasInWithConstant() {
            return hasInWithConstant;
        }

        private void markInWithSubquery() {
            this.hasInWithSubquery = true;
        }

        private void markInWithConstant() {
            this.hasInWithConstant = true;
        }
    }

    public static InClauseInfo analyze(SqlNode node) {
        InClauseInfo info = new InClauseInfo();
        walk(node, info);
        return info;
    }

    private static void walk(SqlNode node, InClauseInfo info) {
        if (node instanceof SqlBasicCall call) {
            SqlKind kind = call.getOperator().getKind();

            if (kind == SqlKind.IN || kind == SqlKind.NOT_IN) {
                SqlNode rhs = call.getOperandList().get(1);
                if (rhs instanceof SqlSelect) {
                    info.markInWithSubquery();
                } else if (rhs instanceof SqlNodeList) {
                    info.markInWithConstant();
                }
            }

            for (SqlNode operand : call.getOperandList()) {
                walk(operand, info);
            }
        }
    }
}

