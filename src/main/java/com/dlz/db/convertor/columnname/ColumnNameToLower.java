package com.dlz.db.convertor.columnname;

import java.util.Locale;

public class ColumnNameToLower implements IColumnNameConvertor {

	@Override
	public String toFieldName(String dbKey) {
		return dbKey.toLowerCase(Locale.ROOT);
	}

	@Override
	public String toDbColumnName(String beanKey) {
		return beanKey;
	}
}
