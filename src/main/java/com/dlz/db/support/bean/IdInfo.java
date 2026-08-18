package com.dlz.db.support.bean;

import com.dlz.db.annotation.IdType;
import com.dlz.kit.exception.SystemException;
import com.dlz.kit.util.ValUtil;
import com.dlz.kit.util.system.FieldReflections;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 主键信息封装类
 */
@Getter
@Setter
public class IdInfo {
    private Field field;
    private String name;
    private IdType type;
    private Type genericType;

    public IdInfo(Field field, String name) {
        this.field = field;
        this.name = name;
    }

    public void setId(Object o, Object id) {
        if (genericType == null) {
            genericType = field.getGenericType();
        }
        try {
            field.set(o, ValUtil.toObj(id, genericType));
        } catch (IllegalAccessException e) {
            throw new SystemException("设置id异常", e);
        }
    }

    public Object getValue(Object o) {
        return FieldReflections.getValue(o, field);
    }
}