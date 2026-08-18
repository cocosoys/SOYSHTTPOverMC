package com.dlz.db.annotation.proxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class MybatisPlusTableName {
    private final Class<Annotation> idTypeAnnotation;
    private final Method valueMethod;
    protected MybatisPlusTableName() {
        Class<Annotation> idType1;
        Method valueMethodTmp;
        try {
            idType1 = (Class<Annotation>) Class.forName("com.baomidou.mybatisplus.annotation.TableName");
            valueMethodTmp = idType1.getMethod("value");
        } catch (ClassNotFoundException ex) {
            // MyBatis-Plus 未引入，设为 null
            idType1 = null;
            valueMethodTmp = null;
        } catch (NoSuchMethodException ex) {
            // 方法不存在
            idType1 = null;
            valueMethodTmp = null;
        }
        idTypeAnnotation = idType1;
        valueMethod = valueMethodTmp;
    }

    public String value(Class<?> field){
        if (field == null||idTypeAnnotation==null){
            return null;
        }
        if (field.isAnnotationPresent(idTypeAnnotation)) {
            Annotation mpAnnotation = field.getAnnotation(idTypeAnnotation);
            try {
                String value = (String) valueMethod.invoke(mpAnnotation);
                if (!value.isEmpty()) {
                    return value;
                }
            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                return null;
            }
        }
        return null;
    }
}
