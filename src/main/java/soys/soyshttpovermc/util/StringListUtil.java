package soys.soyshttpovermc.util;

import java.util.ArrayList;
import java.util.List;

public class StringListUtil {
    /**
     * 根据输入前缀，匹配list中以前缀开头的字符串（大小写忽略）
     * @param inputStr 输入的前缀字符
     * @param sourceList 候选数据源list
     * @return 匹配结果集合
     */
    public static List<String> matchByPrefix(String inputStr, List<String> sourceList) {
        List<String> result = new ArrayList<>();
        // 入参判空
        if (inputStr == null || inputStr.isEmpty() || sourceList == null || sourceList.isEmpty()) {
            return result;
        }
        String lowerInput = inputStr.toLowerCase();

        for (String item : sourceList) {
            if (item == null) {
                continue;
            }
            if (item.toLowerCase().startsWith(lowerInput)) {
                result.add(item);
            }
        }
        return result;
    }
}
