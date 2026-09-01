package com.github.cocosoys.mc.soyshttpovermc.i18n;

/**
 * 语言包抽象接口：把「翻译文本」从业务代码中剥离，通过键值缓存到内存中读取。
 * <p>实现类应：</p>
 * <ul>
 *   <li>把语言文件（如 {@code language/zh_cn.yml}）在初始化时整体加载进内存 {@code Map}；</li>
 *   <li>运行期通过 {@link #get} / {@link #format} 纯内存查表，避免每次 I/O。</li>
 * </ul>
 */
public interface ILanguageBundle {

    /**
     * 语言标识（小写，如 zh_cn / en_us）。
     */
    String languageCode();

    /**
     * 取指定 key 的翻译文本；不存在返回 {@code defaultText}。
     *
     * @param key         翻译键（支持嵌套点路径，如 {@code gift.claim.success}）
     * @param defaultText 兜底文本（key 缺失或 bundle 未加载时返回）
     * @return 翻译文本
     */
    String get(String key, String defaultText);

    /**
     * 取指定 key 的翻译文本并替换 {@code {0} {1}...} 占位符；不存在返回 {@code defaultText}。
     *
     * @param key         翻译键
     * @param defaultText 兜底模板
     * @param args        依次替换 {@code {0}、{1}...} 的参数
     * @return 翻译后的文本
     */
    String format(String key, String defaultText, Object... args);

    /**
     * 是否已成功加载到内存中的翻译文本。
     */
    boolean isLoaded();
}