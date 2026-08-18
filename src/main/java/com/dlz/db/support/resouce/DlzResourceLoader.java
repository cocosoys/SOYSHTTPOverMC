package com.dlz.db.support.resouce;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * 资源加载器统
 * 支持 {@code classpath*:} 跨 JAR 资源加载与类扫描。
 *
 * <p>所有框架（Spring/Solon 等）统一复用本类，无需各自实现。</p>
 *
 * @since 7.0.0
 */
@Slf4j
public class DlzResourceLoader {


    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private DlzResourceLoader() {
    }

    public static Set<Class<?>> scan(String basePackage, Class<? extends Annotation> annotationClass) throws Exception {
        Set<Class<?>> result = new HashSet<>();
        if (basePackage == null || basePackage.isEmpty()) {
            return result;
        }
        String basePath = basePackage.replace('.', '/');
        String pattern = "classpath*:" + basePath + "/**/*.class";
        List<URL> urls = getResources(pattern);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (URL url : urls) {
            String className = extractClassName(url, basePath);
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className, false, cl);
                if (annotationClass == null || clazz.isAnnotationPresent(annotationClass)) {
                    result.add(clazz);
                }
            } catch (Throwable e) {
                log.debug("加载类失败 {}: {}", className, e.getMessage());
            }
        }
        return result;
    }

    /** 从资源 URL 提取类全限定名。 */
    private static String extractClassName(URL url, String basePath) {
        String path = url.toString();
        int idx = path.indexOf(basePath);
        if (idx < 0) {
            return null;
        }
        String classPath = path.substring(idx);
        if (!classPath.endsWith(".class")) {
            return null;
        }
        classPath = classPath.substring(0, classPath.length() - ".class".length());
        return classPath.replace('/', '.');
    }


    /**
     * 根据 location 查找所有匹配的资源 URL。
     *
     * @param location classpath 路径，支持通配符
     * @return 匹配到的 URL 列表
     */
    public static List<URL> getResources(String location) throws IOException {
        if (location == null) {
            return new ArrayList<>();
        }
        boolean classpathAll;
        String path;
        if (location.startsWith(CLASSPATH_ALL_PREFIX)) {
            classpathAll = true;
            path = location.substring(CLASSPATH_ALL_PREFIX.length());
        } else if (location.startsWith(CLASSPATH_PREFIX)) {
            classpathAll = false;
            path = location.substring(CLASSPATH_PREFIX.length());
        } else {
            classpathAll = false;
            path = location;
        }
        // 去掉开头多余斜杠
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (!hasWildcard(path)) {
            return findExactResources(path, classpathAll);
        }
        return findPatternResources(path, classpathAll);
    }

    /**
     * 加载所有匹配资源的 InputStream。
     */
    public static InputStream[] getResourceStreams(String location) throws IOException {
        List<URL> urls = getResources(location);
        List<InputStream> streams = new ArrayList<>(urls.size());
        for (URL url : urls) {
            InputStream stream = tryOpenResourceStream(url);
            if (stream != null) {
                streams.add(stream);
            } else {
                log.warn("打开资源流失败: {}", url.toString());
            }
        }
        return streams.toArray(new InputStream[0]);
    }

    /**
     * 尝试打开资源流，Spring Boot 嵌套 JAR 兼容：
     * jar:jar:file:.../app.jar!/BOOT-INF/lib/lib.jar!/path 格式无法通过 {@link URL#openStream()} 直接打开，
     * 回退到通过 {@link ClassLoader#getResourceAsStream(String)} 加载。
     */
    private static InputStream tryOpenResourceStream(URL url) {
        try {
            return url.openStream();
        } catch (IOException e) {
            log.debug("直接打开 URL 失败，尝试通过 ClassLoader 加载: {}", url, e);
            String resourcePath = extractInnerResourcePath(url);
            if (resourcePath != null) {
                InputStream is = currentClassLoader().getResourceAsStream(resourcePath);
                if (is != null) {
                    return is;
                }
            }
            return null;
        }
    }

    /**
     * 从 URL 中提取最内层的资源路径。
     * 例如 jar:jar:file:/app.jar!/BOOT-INF/lib/lib.jar!/sql/sys/sys.sql → sql/sys/sys.sql
     */
    private static String extractInnerResourcePath(URL url) {
        String urlStr = url.toString();
        int lastSep = urlStr.lastIndexOf("!/");
        if (lastSep >= 0 && lastSep + 2 < urlStr.length()) {
            return urlStr.substring(lastSep + 2);
        }
        return null;
    }

    /**
     * 加载单个资源（取第一个匹配）。
     */
    public static InputStream getResourceStream(String location) throws IOException {
        List<URL> urls = getResources(location);
        if (urls.isEmpty()) {
            return null;
        }
        return tryOpenResourceStream(urls.get(0));
    }

    // ============== 内部实现 ==============

    private static boolean hasWildcard(String path) {
        return path.contains("*") || path.contains("?");
    }

    private static List<URL> findExactResources(String path, boolean all) throws IOException {
        List<URL> result = new ArrayList<>();
        ClassLoader cl = currentClassLoader();
        if (all) {
            Enumeration<URL> urls = cl.getResources(path);
            while (urls.hasMoreElements()) {
                result.add(urls.nextElement());
            }
        } else {
            URL url = cl.getResource(path);
            if (url != null) {
                result.add(url);
            }
        }
        return result;
    }

    private static List<URL> findPatternResources(String path, boolean all) throws IOException {
        // 拆分基础路径与模式部分
        int wildcardIdx = firstWildcardIndex(path);
        int slashIdx = path.lastIndexOf('/', wildcardIdx);
        String basePath = slashIdx >= 0 ? path.substring(0, slashIdx + 1) : "";
        String pattern = slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
        Pattern regex = wildcardToRegex(pattern);

        List<URL> result = new ArrayList<>();
        ClassLoader cl = currentClassLoader();
        Enumeration<URL> roots = all ? cl.getResources(basePath) : enumOf(cl.getResource(basePath));
        while (roots.hasMoreElements()) {
            URL rootUrl = roots.nextElement();
            String protocol = rootUrl.getProtocol();
            if ("file".equals(protocol)) {
                collectFromFile(rootUrl, basePath, regex, result);
            } else if ("jar".equals(protocol) || "zip".equals(protocol) || "wsjar".equals(protocol)) {
                collectFromJar(rootUrl, basePath, regex, result);
            } else {
                log.debug("不支持的资源协议: {}, 跳过 url={}", protocol, rootUrl);
            }
        }
        // Spring Boot JAR 兼容：标准方式找不到时，回退到扫描所有类路径根
        if (result.isEmpty() && all) {
            collectFromClasspathRoots(cl, basePath, regex, result);
        }
        return result;
    }

    private static int firstWildcardIndex(String path) {
        int starIdx = path.indexOf('*');
        int qIdx = path.indexOf('?');
        if (starIdx < 0) return qIdx;
        if (qIdx < 0) return starIdx;
        return Math.min(starIdx, qIdx);
    }

    /**
     * 将通配符模式转为正则。
     **/
    private static Pattern wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        int n = pattern.length();
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < n && pattern.charAt(i + 1) == '*';
                if (doubleStar) {
                    // **/ -> (?:.*/)?
                    if (i + 2 < n && pattern.charAt(i + 2) == '/') {
                        sb.append("(?:.*/)?");
                        i += 3;
                        continue;
                    }
                    // /** 已在上一轮吃掉的 '/' 之后 —— 不会走到这，但裸 ** 作兜底
                    sb.append(".*");
                    i += 2;
                    continue;
                }
                sb.append("[^/]*");
                i++;
            } else if (c == '/' && i + 2 < n && pattern.charAt(i + 1) == '*' && pattern.charAt(i + 2) == '*') {
                // /** (后面可能还有 /xxx 或直接结束)
                if (i + 3 < n && pattern.charAt(i + 3) == '/') {
                    // /**/ -> (?:/.*)?/  —— 但要让整体等价于 "/可选多层/"
                    sb.append("(?:/.*)?/");
                    i += 4;
                } else {
                    sb.append("(?:/.*)?");
                    i += 3;
                }
            } else if (c == '?') {
                sb.append(".");
                i++;
            } else if ("\\.[](){}+^$|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    private static void collectFromFile(URL rootUrl, String basePath, Pattern regex, List<URL> result) {
        try {
            // 验证 URL 协议，防止路径遍历攻击
            if (!"file".equals(rootUrl.getProtocol())) {
                log.debug("非文件协议，跳过: {}", rootUrl.getProtocol());
                return;
            }
            File rootDir = new File(rootUrl.toURI());
//            // 验证路径是否在预期范围内
//            String canonicalPath = rootDir.getCanonicalPath();
//            if (!canonicalPath.startsWith(new File(".").getCanonicalPath())) {
//                log.warn("检测到可疑路径，跳过: {}", canonicalPath);
//                return;
//            }
            if (!rootDir.isDirectory()) {
                return;
            }
            walkFile(rootDir, "", basePath, regex, result);
        } catch (Exception e) {
            log.warn("扫描文件资源失败: {}", rootUrl.toString(), e);
        }
    }

    private static void walkFile(File dir, String relative, String basePath, Pattern regex, List<URL> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String childRel = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
            if (child.isDirectory()) {
                walkFile(child, childRel, basePath, regex, result);
            } else {
                if (regex.matcher(childRel).matches()) {
                    try {
                        result.add(child.toURI().toURL());
                    } catch (Exception e) {
                        log.warn("转换文件URL失败: {}", child.getPath(), e);
                    }
                }
            }
        }
    }

    /**
     * Spring Boot JAR 兼容：扫描所有类路径根（用于标准方式查不到时的回退）。
     */
    private static void collectFromClasspathRoots(ClassLoader cl, String basePath, Pattern regex, List<URL> result) throws IOException {
        Enumeration<URL> roots = cl.getResources("");
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            String protocol = root.getProtocol();
            if ("file".equals(protocol)) {
                try {
                    File base = new File(root.toURI());
                    if (base.isDirectory()) {
                        File target = new File(base, basePath);
                        if (target.isDirectory()) {
                            walkFile(target, "", basePath, regex, result);
                        }
                    }
                } catch (URISyntaxException e) {
                    log.debug("转换文件 URL 失败: {}", root, e);
                }
            } else if ("jar".equals(protocol) || "zip".equals(protocol) || "wsjar".equals(protocol)) {
                // 打开外层 JAR 并扫描所有条目，处理 Spring Boot 路径前缀
                collectFromJarWithPrefix(root, basePath, regex, result);
            }
        }
    }

    /**
     * Spring Boot JAR 兼容：从 JAR 根 URL 扫描所有条目，处理 BOOT-INF/classes/ 前缀。
     */
    private static void collectFromJarWithPrefix(URL rootUrl, String basePath, Pattern regex, List<URL> result) {
        JarFile jarFile = null;
        try {
            String protocol = rootUrl.getProtocol();
            if (!"jar".equals(protocol) && !"zip".equals(protocol) && !"wsjar".equals(protocol)) {
                return;
            }
            URLConnection conn = rootUrl.openConnection();
            if (!(conn instanceof JarURLConnection)) {
                return;
            }
            JarURLConnection jarConn = (JarURLConnection) conn;
            jarConn.setUseCaches(false);
            jarFile = jarConn.getJarFile();
            String jarBaseUrl = jarConn.getJarFileURL().toString();
            // 确定路径前缀：如 jar:file:/app.jar!/BOOT-INF/classes!/ 的 entryName 是 BOOT-INF/classes!/
            String entryName = jarConn.getEntryName();
            String jaredPrefix = "";
            if (entryName != null && entryName.endsWith("!/")) {
                jaredPrefix = entryName.substring(0, entryName.length() - 2); // 去掉 "!/"
            } else if (entryName != null && !entryName.isEmpty()) {
                jaredPrefix = entryName.endsWith("/") ? entryName.substring(0, entryName.length() - 1) : entryName;
            }
            // 扫描 JAR 所有条目
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // 尝试去掉 jar 内路径前缀（如 BOOT-INF/classes）后匹配
                String matchName = name;
                if (!jaredPrefix.isEmpty() && name.startsWith(jaredPrefix + "/")) {
                    matchName = name.substring(jaredPrefix.length() + 1);
                }
                if (matchName.startsWith(basePath)) {
                    String relative = matchName.substring(basePath.length());
                    if (regex.matcher(relative).matches()) {
                        result.add(new URL("jar:" + jarBaseUrl + "!/" + name));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("扫描 JAR 根资源失败: {}", rootUrl.toString(), e);
        } finally {
            if (jarFile != null) {
                try { jarFile.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    private static void collectFromJar(URL rootUrl, String basePath, Pattern regex, List<URL> result) {
        JarFile jarFile = null;
        try {
            // 验证 URL 协议，防止 SSRF 攻击
            String protocol = rootUrl.getProtocol();
            if (!"jar".equals(protocol) && !"zip".equals(protocol) && !"wsjar".equals(protocol)) {
                log.debug("不支持的 jar 协议，跳过: {}", protocol);
                return;
            }

            URLConnection conn = rootUrl.openConnection();
            // 禁用自动跟随重定向
            if (conn instanceof java.net.HttpURLConnection) {
                ((java.net.HttpURLConnection) conn).setInstanceFollowRedirects(false);
            }

            String jarBaseUrl;
            if (conn instanceof JarURLConnection) {
                JarURLConnection jarConn = (JarURLConnection) conn;
                jarConn.setUseCaches(false);
                jarFile = jarConn.getJarFile();
                jarBaseUrl = jarConn.getJarFileURL().toString();
            } else {
                // 回退处理（罕见）
                String urlFile = rootUrl.getFile();
                int sep = urlFile.indexOf("!/");
                if (sep < 0) return;
                String jarPath = urlFile.substring(0, sep);
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring("file:".length());
                }
                jarFile = new JarFile(jarPath);
                jarBaseUrl = "file:" + jarPath;
            }

            String basePathInJar = basePath; // jar 内的路径不会有前导斜杠
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (basePathInJar.isEmpty() || name.startsWith(basePathInJar)) {
                    String relative = name.substring(basePathInJar.length());
                    if (regex.matcher(relative).matches()) {
                        URL entryUrl = new URL("jar:" + jarBaseUrl + "!/" + name);
                        result.add(entryUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("扫描 jar 资源失败: {}", rootUrl.toString(), e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    log.warn("关闭 JarFile 失败", e);
                }
            }
        }
    }

    private static ClassLoader currentClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : DlzResourceLoader.class.getClassLoader();
    }

    private static Enumeration<URL> enumOf(final URL url) {
        return new Enumeration<URL>() {
            boolean served = url == null;
            public boolean hasMoreElements() { return !served; }
            public URL nextElement() { served = true; return url; }
        };
    }
}
