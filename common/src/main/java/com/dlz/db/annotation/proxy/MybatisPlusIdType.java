package com.dlz.db.annotation.proxy;

import com.dlz.db.annotation.IdType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MybatisPlusIdType {
    private final Class<Annotation> idTypeAnnotation;
    private final Method valueMethod;
    private final Method typeMethod;

    protected MybatisPlusIdType() {
        Class<Annotation> idType1;
        Method valueMethodTmp;
        Method typeMethodTmp;
        try {
            idType1 = (Class<Annotation>) Class.forName("com.baomidou.mybatisplus.annotation.TableId");
            valueMethodTmp = idType1.getMethod("value");
            typeMethodTmp = idType1.getMethod("type");
        } catch (ClassNotFoundException ex) {
            // MyBatis-Plus 未引入，设为 null
            idType1 = null;
            valueMethodTmp = null;
            typeMethodTmp = null;
        } catch (NoSuchMethodException ex) {
            // 方法不存在
            idType1 = null;
            valueMethodTmp = null;
            typeMethodTmp = null;
        }
        idTypeAnnotation = idType1;
        valueMethod = valueMethodTmp;
        typeMethod = typeMethodTmp;
    }

    public String value(Field field) {
        if (field == null || idTypeAnnotation == null) {
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

    public IdType type(Field field) {
        if (field == null || idTypeAnnotation == null) {
            return null;
        }
        if (field.isAnnotationPresent(idTypeAnnotation)) {
            Annotation mpAnnotation = field.getAnnotation(idTypeAnnotation);
            try {
                String value = typeMethod.invoke(mpAnnotation).toString();
                switch (value) {
                    case "AUTO":
                        return IdType.AUTO;
                    case "INPUT":
                        return IdType.INPUT;
                    case "NONE":
                        return IdType.SEQ;
                    case "ASSIGN_ID":
                        return IdType.ASSIGN_ID;
                    case "ASSIGN_UUID":
                        return IdType.ASSIGN_UUID;
                }
            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                return null;
            }
        }
        return null;
    }
}
