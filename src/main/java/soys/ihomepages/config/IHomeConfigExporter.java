package soys.ihomepages.config;

import java.util.Map;

/**
 * 配置导出接口：将 {@link HomeConfigEntity} 转换为「仅展示字段」的安全 JSON Map（供前端消费）。
 */
public interface IHomeConfigExporter {

    /** 导出安全配置 Map（礼包仅含 name 等展示字段，material/cmd 等执行参数不出现）。 */
    Map<String, Object> export(HomeConfigEntity cfg);
}
