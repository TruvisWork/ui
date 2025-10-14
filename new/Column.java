package com.calcite_new.core.model.entity;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.model.Identifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Column implements Entity {
    private final List<Identifier> namespace;
    @Getter
    private final Identifier name;
    private final Dialect dialect;
    private final int ordinalPosition;
    private final DataType type;

    private Column(Builder builder) {
        this.namespace = builder.namespace;
        this.name = Objects.requireNonNull(builder.name, "name");
        this.dialect = Objects.requireNonNull(builder.dialect, "dialect");
        this.ordinalPosition = builder.ordinalPosition;
        this.type = Objects.requireNonNull(builder.type, "type");
    }

    public EntityKind getKind() {
        return EntityKind.COLUMN;
    }

    //  public Column(List<Identifier> namespace, String name, Dialect dialect, int ordinalPosition, DataType type) {
//    this(namespace, Identifier.of(name, dialect), dialect, ordinalPosition, type);
//  }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Identifier> namespace;
        private Identifier name;
        private Dialect dialect;
        private int ordinalPosition;
        private DataType type;

        public Builder() {
        }

        public Builder namespace(List<Identifier> namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder name(Identifier name) {
            this.name = name;
            return this;
        }

        public Builder dialect(Dialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder ordinalPosition(int ordinalPosition) {
            this.ordinalPosition = ordinalPosition;
            return this;
        }

        public Builder type(DataType type) {
            this.type = type;
            return this;
        }

        public Column build() {
            return new Column(this);
        }
    }

}
