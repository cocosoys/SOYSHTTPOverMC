package soys.ihomepages.log;

import soys.soyshttpovermc.log.LogKit;

public class Log extends LogKit {

    public Log(String prefix, Class<?> sourceClass) {
        super(prefix, sourceClass);
    }

    // ========= Lombok 两个重载工厂 =========
    public static LogKit getLogger(Class<?> clazz) {
        return new LogKit("[iHomePage]", clazz);
    }
}
