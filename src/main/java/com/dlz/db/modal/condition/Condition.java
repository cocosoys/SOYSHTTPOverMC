package com.dlz.db.modal.condition;

import com.dlz.db.enums.DbBuildEnum;
import com.dlz.db.inf.ICondAddByFn;
import com.dlz.db.inf.ICondAddByKey;
import com.dlz.db.inf.ICondAndOr;
import com.dlz.db.modal.para.ParaMap;
import com.dlz.kit.json.JSONMap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Condition implements ICondAndOr<Condition>, ICondAddByKey<Condition>, ICondAddByFn<Condition> {
    boolean isMake = false;
    String runSql;
    JSONMap paras = new JSONMap();
    List<Condition> children = new ArrayList<>();
    private DbBuildEnum builder;
    private final String tableName;

    public Condition(DbBuildEnum builder, String tableName) {
        this(tableName);
        this.builder = builder;
    }

    public Condition(String tableName) {
        this.tableName = tableName;
    }

    private void make(ParaMap pm) {
        if (isMake) {
            pm.addParas(paras);
            return;
        }
        isMake = true;

        if (builder != null) {
//            if (builder == DbBuildEnum.sql || builder == DbBuildEnum.apply) {
            if (builder == DbBuildEnum.sql) {
                pm.addParas(paras);
                return;
            }
            if (children.isEmpty()) {
                runSql = "";
//                runsql = runsql.replace("sql", "false");
                return;
            }
            String join = builder == DbBuildEnum.muOr ? "or" : "and";
            String sub = children.stream()
                    .map(item -> item.getRunsql(pm))
                    .filter(item -> item != null && !item.isEmpty())
                    .collect(Collectors.joining(" " + join + " "));
            sub = sub.replaceAll(join + " and", "and").replaceAll(join + " or", "or");
            if (children.size() > 1 && builder != DbBuildEnum.where) {
                sub = "(" + sub + ")";
            }
            runSql = builder.buildSql(sub);
            return;
        }
        pm.addParas(paras);
    }

    public static Condition where(String tableName) {
        return DbBuildEnum.where.build(tableName);
    }
    public static Condition where() {
        return DbBuildEnum.where.build(null);
    }

    public Condition clone() {
        Condition condition = new Condition(tableName);
        condition.builder = builder;
        condition.paras.putAll(paras);
        condition.children.addAll(children);
        condition.isMake = false;
        condition.runSql = "";
        return condition;
    }

    public String getRunsql(ParaMap pm) {
        make(pm);
        return runSql;
    }

    public Condition setRunSql(String runSql) {
        isMake = false;
        this.runSql = runSql;
        return this;
    }

    public Condition addPara(String key, Object value) {
        isMake = false;
        paras.put(key, value);
        return this;
    }

    public Condition addParas(JSONMap paras) {
        isMake = false;
        this.paras.putAll(paras);
        return this;
    }

    @Override
    public Condition me() {
        return this;
    }

    public void addChildren(Condition child) {
        if (!child.children.isEmpty()) {
            children.addAll(child.children);
        } else {
            children.add(child);
        }
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    public boolean isContainCondition(String column) {
        return children.stream().anyMatch(item -> item.runSql.startsWith(column + " "));
    }
}
