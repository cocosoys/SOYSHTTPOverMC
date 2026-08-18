package soys.soyshttpovermc.orm.query;

/**
 * 条件操作符（双后端通解：YAML 端内存求值 / SQL 端翻译 WHERE）。
 */
public enum Op {
    EQ("="),
    NE("<>"),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    LIKE("LIKE"),
    IN("IN"),
    NOT_IN("NOT IN"),
    IS_NULL("IS NULL"),
    NOT_NULL("IS NOT NULL");

    private final String symbol;

    Op(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
