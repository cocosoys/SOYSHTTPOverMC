package soys.thismyhomepages.config;

import soys.thismyhomepages.spring.entity.GiftCommand;
import soys.thismyhomepages.spring.entity.GiftConfigEntity;
import soys.thismyhomepages.spring.entity.GiftItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置导出实现：构建「仅展示字段」的安全 JSON Map。
 * 关键安全点：{@code gift.items[].material/amount} 与 {@code gift.commands[].cmd} 属服务端执行参数，
 * <b>绝不</b>进入前端 JSON；前端仅拿到 {@code name} 等展示字段。
 */
public class JsonHomeConfigExporter implements IHomeConfigExporter {

    @Override
    public Map<String, Object> export(HomeConfigEntity cfg) {
        Map<String, Object> m = new LinkedHashMap<>(cfg.toDisplayMap());
        // 用安全礼包视图替换原始 gift 段
        m.put("gift", safeGift(cfg.getGift()));
        return m;
    }

    private GiftConfigEntity safeGift(GiftConfigEntity g) {
        GiftConfigEntity out = new GiftConfigEntity();
        if (g == null) {
            out.setEnabled(false);
            return out;
        }

        // 顶层字段 setXX
        out.setEnabled(g.isEnabled());
        out.setTitle(g.getTitle());
        out.setDescription(g.getDescription());
        out.setShowButton(g.isShowButton());

        // period嵌套对象，替换原来的LinkedHashMap
        out.setPeriodMode(g.getPeriodMode());

        // items列表，直接拷贝GiftItem，不再包装Map
        List<GiftItem> items = new ArrayList<>();
        if (g.getItems() != null) {
            for (GiftItem it : g.getItems()) {
                GiftItem item = new GiftItem();
                item.setName(it.getName());
                item.setIcon(it.getIcon());
                items.add(item);
            }
        }
        out.setItems(items);

        return out;
    }
}
