package com.github.cocosoys.mc.soyshttpovermc.orm.query;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * Lambda 字段名提取（JDK8 SerializedLambda 反序列化）：
 * {@code User::getName} → {@code name}（去掉 get/is 前缀、首字母小写）。
 */
public final class LambdaUtils {

    private LambdaUtils() {
    }

    /**
     * 从可序列化 Lambda 提取字段名；无法解析时抛 IllegalArgumentException。
     */
    public static String resolve(SFunction<?, ?> fn) {
        if (fn == null) return null;
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replaced = writeReplace.invoke(fn);
            if (!(replaced instanceof SerializedLambda)) {
                return null;
            }
            SerializedLambda lambda = (SerializedLambda) replaced;
            String implMethod = lambda.getImplMethodName();
            String field = methodToField(implMethod);
            if (field == null) {
                throw new IllegalArgumentException(I18n.t("exception.orm.lambda-extract-fail",
                        "无法从 Lambda 提取字段名: {0}（请使用 getter/is 方法引用，如 User::getName）", implMethod));
            }
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(I18n.t("exception.orm.lambda-extract-error", "Lambda 字段名提取失败: {0}", e.getMessage()), e);
        }
    }

    /**
     * getXxx/isXxx → xxx（首字母小写）。
     */
    private static String methodToField(String method) {
        if (method == null || method.isEmpty()) return null;
        String name = method;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        } else {
            return null; // 非 getter/is 方法引用
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
