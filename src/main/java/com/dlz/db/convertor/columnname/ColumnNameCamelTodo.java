package com.dlz.db.convertor.columnname;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 列名 / 字段名 双向转换。
 * <p>
 * 该类调用极其频繁（每次 ResultSet→Bean、参数绑定、SQL 渲染都会触发），
 * 性能优化要点：
 * <ol>
 *   <li>结果缓存：列名集合有界，使用 ConcurrentHashMap 直接命中，避免重复计算。</li>
 *   <li>单遍 char 扫描：消除 Pattern/Matcher、String.replace、toCharArray 等中间分配。</li>
 *   <li>零分配快速路径：输入已符合输出格式时（最常见情况）直接返回原串。</li>
 * </ol>
 */
public class ColumnNameCamelTodo implements IColumnNameConvertor {

	/** db 列名 → java 字段名 缓存 */
	private static final ConcurrentMap<String, String> FIELD_CACHE = new ConcurrentHashMap<>(256);
	/** java 字段名 → db 列名 缓存 */
	private static final ConcurrentMap<String, String> COLUMN_CACHE = new ConcurrentHashMap<>(256);

	@Override
	public String toFieldName(String dbKey) {
		if (dbKey == null) {
			return "";
		}
		String cached = FIELD_CACHE.get(dbKey);
		if (cached != null) {
			return cached;
		}
		String result = doToFieldName(dbKey);
		// 防止极端情况无限增长（一般不会触发）
		if (FIELD_CACHE.size() < 100_000) {
			FIELD_CACHE.putIfAbsent(dbKey, result);
		}
		return result;
	}

	private static String doToFieldName(String dbKey) {
		final int len = dbKey.length();
		if (len == 0) {
			return dbKey;
		}

		// 一遍扫描：判断是否需要修改（含大写或下划线）
		boolean needConvert = false;
		for (int i = 0; i < len; i++) {
			char c = dbKey.charAt(i);
			if (c == '_' || (c >= 'A' && c <= 'Z')) {
				needConvert = true;
				break;
			}
		}
		if (!needConvert) {
			return dbKey;
		}

		// 单遍生成：遇 '_' 把下一个字符变大写，其余字符一律小写
		char[] buf = new char[len];
		int j = 0;
		boolean upperNext = false;
		for (int i = 0; i < len; i++) {
			char c = dbKey.charAt(i);
			if (c == '_') {
				upperNext = true;
				continue;
			}
			// ASCII 大写 → 小写（避免 Character.toLowerCase 的 locale 处理开销）
			if (c >= 'A' && c <= 'Z') {
				c = (char) (c + 32);
			}
			if (upperNext) {
				if (c >= 'a' && c <= 'z') {
					c = (char) (c - 32);
				} else {
					// 非字母时（数字等）按原行为：跳过下划线，下一个字母仍尝试大写
					// 这里数字保持原样，并把 upperNext 留到下一个字母
					buf[j++] = c;
					continue;
				}
				upperNext = false;
			}
			buf[j++] = c;
		}
		return new String(buf, 0, j);
	}

	/**
	 * 字段转换成数据库键名 aaBbCc→AA_BB_CC<br>
	 * 如果参数含有_则不做转换<br>
	 * 会自动去除各种特殊符号，只保留字母、数字和下划线、点号、美元符号
	 * @param beanKey
	 * @author dk 2015-04-10
	 */
	@Override
	public String toDbColumnName(String beanKey) {
		if (beanKey == null) {
			return null;
		}
		String cached = COLUMN_CACHE.get(beanKey);
		if (cached != null) {
			return cached;
		}
		String result = doToDbColumnName(beanKey);
		if (COLUMN_CACHE.size() < 100_000) {
			COLUMN_CACHE.putIfAbsent(beanKey, result);
		}
		return result;
	}

	private static String doToDbColumnName(String beanKey) {
		final int len = beanKey.length();
		if (len == 0) {
			return beanKey;
		}

		// 单遍扫描：清洗特殊字符 + 统计 hasUnderscore / hasLower / hasSpecial
		char[] cleanedBuf = null; // 仅在出现特殊字符时分配
		int cleanLen = 0;
		boolean hasUnderscore = false;
		boolean hasLower = false;
		for (int i = 0; i < len; i++) {
			char c = beanKey.charAt(i);
			boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '$';
			if (!keep) {
				if (cleanedBuf == null) {
					cleanedBuf = new char[len];
					beanKey.getChars(0, i, cleanedBuf, 0);
					cleanLen = i;
				}
				continue;
			}
			if (c == '_') {
				hasUnderscore = true;
			} else if (c >= 'a' && c <= 'z') {
				hasLower = true;
			}
			if (cleanedBuf != null) {
				cleanedBuf[cleanLen++] = c;
			} else {
				cleanLen = i + 1;
			}
		}

		// 含下划线：直接返回清洗后的串
		if (hasUnderscore) {
			if (cleanedBuf == null) {
				return beanKey;
			}
			return new String(cleanedBuf, 0, cleanLen);
		}
		// 全大写（无小写字母）：直接返回清洗后的串
		if (!hasLower) {
			if (cleanedBuf == null) {
				return beanKey;
			}
			return new String(cleanedBuf, 0, cleanLen);
		}

		// 驼峰 → 下划线 + 大写：单遍生成
		// 最大长度 = cleanLen * 2（每个字符前都可能插入 '_'），实际通常远小于
		char[] out = new char[cleanLen * 2];
		int o = 0;
		for (int i = 0; i < cleanLen; i++) {
			char c = cleanedBuf == null ? beanKey.charAt(i) : cleanedBuf[i];
			if (c >= 'A' && c <= 'Z') {
				if (i > 0) {
					out[o++] = '_';
				}
				out[o++] = c;
			} else if (c >= 'a' && c <= 'z') {
				out[o++] = (char) (c - 32);
			} else {
				out[o++] = c;
			}
		}
		return new String(out, 0, o);
	}

}
