package com.dlz.db.convertor.columnname;

public interface IConvertorToFieldName {
	/**
	 * 数据库字段名转为bean字段名
	 * @param dbKey
	 	 */
	String toFieldName(String dbKey);
}
