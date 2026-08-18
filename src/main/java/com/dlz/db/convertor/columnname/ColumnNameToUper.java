package com.dlz.db.convertor.columnname;

import java.util.Locale;

public class ColumnNameToUper implements IColumnNameConvertor {


	@Override
	public String toFieldName(String dbKey) {
		return dbKey.toUpperCase(Locale.ROOT);
	}

	@Override
	public String toDbColumnName(String beanKey) {
		return beanKey;
	}
}
