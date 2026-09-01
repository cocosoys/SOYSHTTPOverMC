package com.github.cocosoys.mc.soyshttpovermc.spring.entity;

import com.github.cocosoys.mc.soyshttpovermc.util.JsonWriter;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础实体类（仿 RuoYi 的 BaseEntity）：所有 API 实体继承本类，获得对实体类的基本控制。
 * <ul>
 *   <li><b>序列化控制</b>：实现 {@link Serializable} + 固定 {@code serialVersionUID}，
 *       跨版本反序列化时版本号不匹配即安全报错，避免脏数据；</li>
 *   <li><b>公共审计字段</b>：createBy / createTime / updateBy / updateTime / remark；</li>
 *   <li><b>附加参数</b>：params（Map），承载查询条件/扩展数据；</li>
 *   <li><b>便捷转换</b>：{@link #toMap()} 反射转 Map，可直接放入 {@code AjaxResult.success(data)}。</li>
 * </ul>
 * getter/setter 由 Lombok {@link Data} 自动生成。
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String remark;
    private Map<String, Object> params;

    /**
     * 自定义实现（Lombok 检测到同名方法不会重复生成）：惰性初始化，保持 getParams().put() 可用
     */
    public Map<String, Object> getParams() {
        if (params == null) {
            params = new HashMap<>();
        }
        return params;
    }

    /**
     * 反射转换为 Map（用于 AjaxResult 数据序列化 / 调试输出）
     */
    public Map<String, Object> toMap() {
        return JsonWriter.beanToMap(this);
    }
}
