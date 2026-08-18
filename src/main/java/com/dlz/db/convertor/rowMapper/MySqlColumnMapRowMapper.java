package com.dlz.db.convertor.rowMapper;


import com.dlz.db.modal.dto.ResultMap;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

public class MySqlColumnMapRowMapper extends ResultMapRowMapper{
	@Override
	public ResultMap  mapRow(ResultSet rs, int rowNum) throws SQLException {
		ResultSetMetaData rsmd = rs.getMetaData();
		int columnCount = rsmd.getColumnCount();
		ResultMap mapOfColValues = new ResultMap();
		for (int i = 1; i <= columnCount; i++) {
			String key = toFieldName(JdbcValueUtils.lookupColumnName(rsmd, i));
			Object obj;
			String typename= rsmd.getColumnTypeName(i).toUpperCase(Locale.ROOT);
			if("DECIMAL".equals(typename)){
				obj = rs.getDouble(i);
			}else{
				obj = getColumnValue(rs, i);
			}
			 
			mapOfColValues.put(key, obj);
		}
		return mapOfColValues;
	}
}
