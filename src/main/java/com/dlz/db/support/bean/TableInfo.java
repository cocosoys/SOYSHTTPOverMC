package com.dlz.db.support.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TableInfo {
    private String tableName;
    private String tableComment;
    private List<String> primaryKeys;
    private List<ColumnInfo> columnInfos;
}