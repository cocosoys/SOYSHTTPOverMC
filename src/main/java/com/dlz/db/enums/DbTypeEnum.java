package com.dlz.db.enums;

public enum DbTypeEnum {
    MYSQL("_mysql"),
    H2("_h2"),
    POSTGRESQL("_postgresql"),
    ORACLE("_oracle"),
    DM8("_dm8"),
    SQLITE("_sqlite"),
    MSSQL("_sqlserver");
    private final String end;

    DbTypeEnum(String end) {
        this.end = end;
    }

    public String getEnd() {
        return end;
    }
}