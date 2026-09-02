package com.dlz.db.support;

import com.dlz.db.enums.DbTypeEnum;
import com.dlz.db.exception.DbException;
import com.dlz.db.modal.DB;
import com.dlz.db.modal.dto.ResultMap;
import com.dlz.db.support.resouce.DlzResourceLoader;
import com.dlz.kit.util.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据库配置信息
 *
 * @author dingkui 2011-08-12 v1.0
 * history dingkui 2012-05-07 v1.1
 * 修改sql文件路径取得方式，以便执行init时可以刷新内存
 */
@Slf4j
public class SqlHolder {
    private final static String STR_SQL_FILE = "file:";
    // 通用的sql
    static final Map<String, String> m_comm_sql = new ConcurrentHashMap<>();
    // 方言sql
    static final Map<String, Map<String, String>> m_dialect_sql = new ConcurrentHashMap<>(DbTypeEnum.values().length);
    private static boolean initIng = false;

    static {
        DbTypeEnum[] values = DbTypeEnum.values();
        for (DbTypeEnum value : values) {
            m_dialect_sql.put(value.getEnd(), new ConcurrentHashMap<>());
        }
    }

    public static void init() {
        load();
    }


    private static void readSqlPath(File file) {
        try {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File fi : files) {
                    readSqlPath(fi);
                }
            } else {
                if (file.getAbsolutePath().endsWith(".sql")) {
                    log.info(file.getPath());
                    readSqlXml(new FileInputStream(file));
                }
            }
        } catch (FileNotFoundException e) {
            log.error(ExceptionUtils.getStackTrace(file.getAbsolutePath() + " 文件找不到！", e));
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(file.getAbsolutePath() + " 加载异常！", e));
        }
    }

    private static void readSqlXml(InputStream is) {
        try {
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            // dom4j 1.6.1 的 elements() 返回 raw List，需显式转换（1.6+ 返回 List<Element> 才免 cast）
            for (Object o : doc.getRootElement().elements()) {
                Element sql = (Element) o;
                addSqlSetting(sql.attributeValue("sqlId"), sql.getData().toString(), false);
            }
        } catch (DocumentException e) {
            log.error(ExceptionUtils.getStackTrace(" 文件读取异常！", e));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error(ExceptionUtils.getStackTrace(" 文件关闭异常！", e));
                }
            }
        }
    }

    public static void addSqlSetting(String sqlId, String sqlStr, boolean force) {
        String sqlDB = sqlId.substring(sqlId.lastIndexOf(".") + 1);
        Map<String, String> m_sqlList = m_dialect_sql.get(sqlDB);
        if (m_sqlList != null) {
            //表示带数据库，key中删除数据库标记
            sqlId = sqlId.substring(0, sqlId.length() - sqlDB.length() - 1);
        } else {
            m_sqlList = m_comm_sql;
        }

        if (sqlId == null || sqlStr == null) {
            return;
        }
        if (!force && m_sqlList.containsKey(sqlId)) {
            return;
        }
        sqlStr = clearSql(sqlStr);
        m_sqlList.put(sqlId, sqlStr);
        if (log.isDebugEnabled()) {
            log.debug(sqlId + ":" + sqlStr);
        }
    }

    private static void loadResources(String path) throws Exception {
        InputStream[] inputStreams = DlzResourceLoader.getResourceStreams("classpath*:sql/" + path + ".sql");
        for (InputStream is : inputStreams) {
            if (log.isDebugEnabled()) {
                log.debug("Loading SQL resource: " + path);
            }
            readSqlXml(is);
        }
    }

    private static String sql(String key) {
        Map<String, String> m_sqlList = m_dialect_sql.get(DB.Dynamic.getDbType().getEnd());
        final String sql = m_sqlList.get(key);
        if (sql != null) {
            return sql;
        }

        return m_comm_sql.get(key);
    }

    private static void load() {
        if (initIng) {
            return;
        }
        initIng = true;

        try {
            loadResources("framework/*");
            loadResources("sys/*");
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
        }
        DBHolder.getSqlConfig().getSqllist().forEach(name -> {
            if (name.startsWith(STR_SQL_FILE)) {
                final String sqlRoot = SqlHolder.class.getClassLoader().getResource("sql/").getPath();
                String path = name.substring(STR_SQL_FILE.length());
                readSqlPath(new File(sqlRoot + path));
                return;
            }
            try {
                loadResources(name);
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            }
        });
        initIng = false;
    }

    public static void loadDbSql() {
        if (DBHolder.getSqlConfig().isUseDbSql()) {
            String sql = clearSql(DBHolder.getSqlConfig().getSql());
            try {
                List<ResultMap> mapList = DBHolder.getSqlExecutor().getList(sql);
                mapList.forEach(item -> addSqlSetting("key." + item.getStr("k"), item.getStr("s"), true));
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                log.warn("取得数据库配置无效：sql=" + sql);
                throw e;
            }
        }
    }

    public static void reLoad() {
        m_comm_sql.clear();
        m_dialect_sql.values().forEach(Map::clear);
        load();
        loadDbSql();
    }

    private static String clearSql(String sqlStr) {
        return sqlStr.replaceAll("--.*", "").replaceAll("[\\s]+", " ");
    }

    private static final Pattern sqlRegex = Pattern.compile("[\\s]*(?i)(select|update|delete|insert).*");

    public static String getSql(String key) {
        if (key == null) {
            throw new DbException("输入的sql为空！", 1002);
        }
        if (sqlRegex.matcher(key).matches()) {
            return key;
        }
        if (!key.startsWith("key.")) {
            throw new DbException("sqlKey格式无效:" + key, 1002);
        }
        String sql = sql(key);
        if (sql == null) {
            throw new DbException("sqlKey未配置：" + key, 1002);
        }
        return sql;
    }
}
