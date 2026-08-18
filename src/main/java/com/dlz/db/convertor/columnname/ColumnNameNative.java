package com.dlz.db.convertor.columnname;

public class ColumnNameNative implements IColumnNameConvertor {

	@Override
	public String toFieldName(String dbKey) {
		return dbKey;
	}

	@Override
	public String toDbColumnName(String beanKey) {
		return beanKey;
	}
}
