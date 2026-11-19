package plus.junlong.appfork;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import plus.junlong.appfork.script.ScriptUpdater;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/**
 * @author Junlong
 */
@Component
@Slf4j
public final class Updater implements CommandLineRunner {

    // 平台列表
    static final Map<String, String> PLATFORMS = new LinkedHashMap<>();
    // 分类列表
    static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        PLATFORMS.put("windows", "Windows");
        PLATFORMS.put("linux", "Linux");
        PLATFORMS.put("mac", "macOS");
        PLATFORMS.put("android", "Android");
        PLATFORMS.put("extensions", "浏览器扩展");
        PLATFORMS.put("other", "Other");

        CATEGORIES.put("network", "网络应用");
        CATEGORIES.put("chat", "社交沟通");
        CATEGORIES.put("music", "音乐欣赏");
        CATEGORIES.put("video", "视频播放");
        CATEGORIES.put("graphics", "图形图像");
        CATEGORIES.put("games", "游戏娱乐");
        CATEGORIES.put("office", "办公学习");
        CATEGORIES.put("reading", "阅读翻译");
        CATEGORIES.put("development", "编程开发");
        CATEGORIES.put("tools", "系统工具");
        CATEGORIES.put("beautify", "主题美化");
        CATEGORIES.put("others", "其他应用");
        CATEGORIES.put("image", "系统镜像");
    }

    // 正常完成同步但未更新
    private final int SYNC_NONE = 0;
    // 正常完成同步并更新了清单文件信息
    private final int SYNC_UPDATE = 1;
    // 未完成同步，失败
    private final int SYNC_ERROR = 2;

    // 缓存已加载的脚本
    private final Map<String, Class<? extends ScriptUpdater>> scriptCache = new ConcurrentHashMap<>();
    // 脚本解析锁
    private final Object groovyParseLock = new Object();

    // 匹配版本号 x.y.z
    private final Pattern versionPattern = Pattern.compile("^(?<version>[\\d.]+)$");
    // 匹配链接地址格式
    private final Pattern urlPattern = Pattern.compile("^((https?|ftp):)?//[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    // 程序需要同步的软件库根目录
    @Value("${config.repo-path}")
    private String repoPath;

    // 当前环境 profile
    @Value("${spring.profiles.active}")
    private String activeProfile;

    /**
     * 运行Updater
     */
    @Override
    public void run(String... args) {
        log.info("同步软件库开始...");

        // 清单文件目录 test环境下使用manifests-test目录
        Path manifestsDir = Path.of(repoPath, "manifests" + ("test".equals(activeProfile) ? "-" + activeProfile : ""));
        log.info("扫描清单文件目录 {}", manifestsDir.toAbsolutePath());
        if (!Files.exists(manifestsDir) || !Files.isDirectory(manifestsDir)) {
            log.error("清单文件目录不存在");
            return;
        }
        // 获取清单文件列表，目录下所有层级文件夹内的文件
        List<File> manifests = FileUtil.loopFiles(manifestsDir, pathname -> pathname.isFile() &&
                (pathname.getName().toLowerCase().endsWith(".json") || pathname.getName().toLowerCase().endsWith(".yaml")) &&
                pathname.canRead() && pathname.canWrite() && pathname.length() > 0);
        if (manifests.isEmpty()) {
            log.error("目录内无清单文件，同步取消");
            return;
        }
        log.info("共扫描到清单文件 {} 个", manifests.size());

        // 使用 LongAdder 优化多线程计数
        LongAdder[] counters = {new LongAdder(), new LongAdder(), new LongAdder()};
        // 信号量控制最大并发量，防止瞬间撑爆 I/O 或内存
        Semaphore semaphore = new Semaphore(20);

        long startTime = System.currentTimeMillis();
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> futures = manifests.stream()
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            // 获取许可前被中断，未持有许可，直接退出且不释放
                            Thread.currentThread().interrupt();
                            return;
                        }
                        try {
                            counters[sync(file, groovyClassLoader)].increment();
                        } catch (Exception e) {
                            log.error("处理文件 [{}] 失败: {}", file.getName(), getBetterExceptionMessage(e));
                            counters[SYNC_ERROR].increment();
                        } finally {
                            semaphore.release();
                        }
                    }, executor))
                    .toList();

            // 等待所有异步任务结束，并设置总超时保护
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(3, TimeUnit.HOURS);
        } catch (TimeoutException e) {
            log.error("同步超时（3小时），强制终止未完成的任务");
        } catch (Exception e) {
            log.error("同步出错: {}", getBetterExceptionMessage(e));
        } finally {
            // 主动关闭：shutdownNow 会中断仍在运行的虚拟线程，避免卡死任务导致迟迟无法退出
            executor.shutdownNow();
            try {
                groovyClassLoader.close();
            } catch (IOException e) {
                log.error("关闭 GroovyClassLoader 失败: {}", getBetterExceptionMessage(e));
            }
        }
        log.info("同步完成: 更新 {} 个, 失败 {} 个, 未更新 {} 个",
                counters[SYNC_UPDATE].sum(), counters[SYNC_ERROR].sum(), counters[SYNC_NONE].sum());
        log.info("同步耗时: {}", DateUtil.formatBetween(System.currentTimeMillis() - startTime, BetweenFormatter.Level.MILLISECOND));
    }

    /**
     * 同步指定软件清单文件
     *
     * @param groovyClassLoader 脚本类加载器
     * @return 同步状态
     */
    private int sync(File manifest, GroovyClassLoader groovyClassLoader) {
        try {
            // 清单文件格式
            final String format = FileUtil.extName(manifest).toLowerCase();
            final String read = FileUtil.readUtf8String(manifest);
            final JSONObject manifestJson = switch (format) {
                case "json" -> JSON.parseObject(read);
                case "yaml" -> new Yaml().loadAs(read, JSONObject.class);
                default -> null;
            };
            if (manifestJson == null || manifestJson.isEmpty()) {
                log.error("manifest [{}] parsed is null or empty", manifest.getName());
                return SYNC_ERROR;
            }

            final String name = manifestJson.getString("name");
            final String homepage = manifestJson.getString("homepage");
            final String author = manifestJson.getString("author");
            final String description = manifestJson.getString("description");
            if (StrUtil.isBlank(name) || StrUtil.isBlank(homepage)
                    || StrUtil.isBlank(author) || StrUtil.isBlank(description)) {
                log.error("manifest [{}] has illegal attribute", manifest.getName());
                return SYNC_ERROR;
            }

            final List<String> categories = parseCategories(manifestJson.get("categories"));
            if (categories == null) {
                log.error("manifest [{}] has illegal categories", manifest.getName());
                return SYNC_ERROR;
            }

            final Map<String, JSONObject> platforms = parsePlatforms(manifestJson.get("platforms"));
            if (platforms == null) {
                log.error("manifest [{}] has illegal platforms", manifest.getName());
                return SYNC_ERROR;
            }

            // 清单文件对应的脚本名默认为清单文件名
            final String code = FileUtil.mainName(manifest).toLowerCase();
            final ScriptConfig defaultScript = parseScriptConfig(manifestJson.get("script"), code);

            boolean changed = false;
            for (Map.Entry<String, JSONObject> entry : platforms.entrySet()) {
                final String platform = entry.getKey();
                final JSONObject block = entry.getValue();
                // 平台级 script 覆盖顶层配置
                final ScriptConfig scriptConfig = block.containsKey("script")
                        ? parseScriptConfig(block.get("script"), code)
                        : defaultScript;

                // 检查更新脚本
                final File script = new File(repoPath, "scripts" + File.separator + scriptConfig.name().toLowerCase() + ".groovy");
                if (!script.exists() || !script.isFile()) {
                    continue;
                }

                final Class<? extends ScriptUpdater> scriptClass = loadScript(script, groovyClassLoader);

                // 构造单平台视图，脚本契约保持不变
                final JSONObject view = manifestJson.clone();
                view.remove("script");
                view.remove("categories");
                view.remove("platforms");
                view.put("platform", platform);
                view.put("version", block.getString("version"));
                view.put("url", block.get("url"));

                Object checkUpdateObj = scriptClass.getDeclaredConstructor().newInstance()
                        .checkUpdate(view, scriptConfig.args());
                if (!(checkUpdateObj instanceof Map<?, ?> checkUpdate)) {
                    continue;
                }
                if (checkUpdate.containsKey("error")) {
                    log.error("exec [{}] script [{}] platform [{}] return error: {}",
                            manifest.getName(), script.getName(), platform, checkUpdate.get("error"));
                    return SYNC_ERROR;
                }
                if (applyUpdate(manifest, manifestJson, block, platform, checkUpdate)) {
                    changed = true;
                }
            }

            if (!changed) {
                return SYNC_NONE;
            }

            // 将新版清单内容写入文件
            final String write = switch (format) {
                case "json" -> JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat);
                case "yaml" -> {
                    DumperOptions options = new DumperOptions();
                    options.setIndent(2);
                    options.setIndicatorIndent(2);
                    options.setIndentWithIndicator(true);
                    options.setSplitLines(false);
                    yield new Yaml(options).dumpAsMap(manifestJson);
                }
                default -> read;
            };
            FileUtil.writeUtf8String(write, manifest);
            return SYNC_UPDATE;
        } catch (Exception e) {
            log.error("sync [{}] error: {}", manifest.getName(), getBetterExceptionMessage(e));
            return SYNC_ERROR;
        }
    }

    /**
     * 将脚本返回值写入单平台数据块
     *
     * @return 是否有属性变更
     */
    boolean applyUpdate(File manifest, JSONObject manifestJson, JSONObject block,
                        String platform, Map<?, ?> checkUpdate) {
        boolean changed = false;

        // 版本号变更，x.y.z 类型版本号不允许降级
        final String oldVersion = block.getString("version");
        final String newVersion = checkUpdate.get("version") instanceof String value ? value : null;
        boolean validVersion = true;
        boolean updateVersion = false;
        if (newVersion != null && !newVersion.equals(oldVersion)) {
            if (ReUtil.isMatch(versionPattern, newVersion) && ReUtil.isMatch(versionPattern, oldVersion)
                    && StrUtil.compareVersion(newVersion, oldVersion) < 0) {
                log.warn("manifest [{}] platform [{}] version [{}] is greater than check [{}]",
                        manifest.getName(), platform, oldVersion, newVersion);
                validVersion = false;
            } else {
                updateVersion = true;
            }
        }

        // 下载地址校验：版本降级时不更新地址，地址非法时版本也不更新
        final Object newUrl = checkUpdate.get("url");
        boolean updateUrl = false;
        if (newUrl != null) {
            if (!validVersion) {
                updateUrl = false;
            } else if (!isCorrectUrl(newUrl)) {
                log.warn("manifest [{}] platform [{}] url [{}] is not a valid url",
                        manifest.getName(), platform, newUrl);
                updateVersion = false;
            } else {
                updateUrl = true;
            }
        }

        if (updateVersion) {
            block.put("version", newVersion);
            changed = true;
            log.info("manifest [{}] platform [{}] upgraded: {}->{}",
                    manifest.getName(), platform, oldVersion, newVersion);
        }
        if (updateUrl && !jsonEquals(block.get("url"), newUrl)) {
            block.put("url", newUrl);
            changed = true;
        }

        // 其余属性写入清单顶层
        for (Map.Entry<?, ?> entry : checkUpdate.entrySet()) {
            final String key = ((String) entry.getKey()).toLowerCase();
            final Object value = entry.getValue();
            if (key.isEmpty() || "version".equals(key) || "url".equals(key) || value == null) {
                continue;
            }
            if (!jsonEquals(manifestJson.get(key), value)) {
                manifestJson.put(key, value);
                changed = true;
                log.info("manifest [{}] attribute [{}] updated", manifest.getName(), key);
            }
        }
        return changed;
    }

    /**
     * 解析并校验分类，按字典顺序返回
     */
    private List<String> parseCategories(Object value) {
        if (!(value instanceof Collection<?> raw) || raw.isEmpty()) {
            return null;
        }
        Set<String> selected = new HashSet<>();
        for (Object item : raw) {
            if (!(item instanceof String category) || !CATEGORIES.containsKey(category.toLowerCase())) {
                return null;
            }
            selected.add(category.toLowerCase());
        }
        return CATEGORIES.keySet().stream().filter(selected::contains).toList();
    }

    /**
     * 解析并校验平台，按字典顺序返回
     */
    @SuppressWarnings("unchecked")
    Map<String, JSONObject> parsePlatforms(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return null;
        }
        // 不允许未知平台
        for (Object key : raw.keySet()) {
            if (!(key instanceof String platform) || !PLATFORMS.containsKey(platform)) {
                return null;
            }
        }
        // 保持对原始 Map 的引用，转换后的数据块需要写回，否则修改无法体现在清单结构中
        Map<String, Object> rawMap = (Map<String, Object>) raw;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (String platform : PLATFORMS.keySet()) {
            Object blockValue = rawMap.get(platform);
            if (blockValue == null) {
                continue;
            }
            JSONObject block = toJsonObject(blockValue);
            if (block == null) {
                return null;
            }
            // YAML 解析出的嵌套 Map 是 LinkedHashMap，JSONObject 构造时会复制一份，
            // 必须写回原结构，否则后续更新版本/地址只会改到副本，落盘内容不会变化
            rawMap.put(platform, block);
            String version = block.getString("version");
            Object url = block.get("url");
            if (StrUtil.isBlank(version) || !isCorrectUrl(url)) {
                return null;
            }
            result.put(platform, block);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 解析脚本配置，未配置时使用清单文件名作为脚本名
     */
    private ScriptConfig parseScriptConfig(Object scriptValue, String defaultName) {
        String scriptName = defaultName;
        JSONObject scriptArgs = null;
        if (scriptValue instanceof String value) {
            scriptName = value;
        } else if (scriptValue instanceof Map<?, ?> map) {
            if (map.get("name") instanceof String value) {
                scriptName = value;
            }
            if (map.get("args") instanceof Map<?, ?> argsMap) {
                scriptArgs = new JSONObject(argsMap);
            }
        }
        return new ScriptConfig(scriptName, scriptArgs);
    }

    /**
     * 加载 Groovy 更新脚本
     */
    private Class<? extends ScriptUpdater> loadScript(File script, GroovyClassLoader groovyClassLoader) {
        return scriptCache.computeIfAbsent(script.getAbsolutePath(), key -> {
            synchronized (groovyParseLock) {
                // 构建脚本独有包名，避免类名重复导致的解析问题
                String original = FileUtil.readUtf8String(script);
                if (!original.trim().startsWith("package ")) {
                    original = "package scripts.auto.p" + DigestUtil.md5Hex(key) + ";" + original;
                }
                Class<?> clazz = groovyClassLoader.parseClass(original, script.getName());
                if (!ScriptUpdater.class.isAssignableFrom(clazz)) {
                    throw new RuntimeException("脚本文件 [" + script.getName() + "] 类必须实现 " + ScriptUpdater.class.getName() + " 接口");
                }
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    throw new RuntimeException("脚本文件 [" + script.getName() + "] 类不能是抽象类");
                }
                try {
                    clazz.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("脚本文件 [" + script.getName() + "] 必须提供 public 无参构造器");
                }
                return clazz.asSubclass(ScriptUpdater.class);
            }
        });
    }

    /**
     * 将 Map 转为 JSONObject
     */
    private JSONObject toJsonObject(Object value) {
        if (value instanceof JSONObject json) {
            return json;
        }
        if (value instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        return null;
    }

    /**
     * 比较两个 JSON 值是否一致，Map 忽略键顺序
     */
    private boolean jsonEquals(Object oldValue, Object newValue) {
        return JSON.toJSONString(oldValue, JSONWriter.Feature.SortMapEntriesByKeys)
                .equals(JSON.toJSONString(newValue, JSONWriter.Feature.SortMapEntriesByKeys));
    }

    /**
     * 判断下载地址是否合法，支持字符串 / 列表 / 键值映射
     */
    private boolean isCorrectUrl(Object value) {
        return switch (value) {
            case String url -> isUrl(url);
            case Map<?, ?> urlMap -> allUrls(urlMap.values());
            case List<?> urlList -> allUrls(urlList);
            default -> false;
        };
    }

    /**
     * 判断集合内的值是否全部为合法url地址（集合为空时返回 false）
     */
    private boolean allUrls(Collection<?> values) {
        return !values.isEmpty() && values.stream().allMatch(v -> v instanceof String str && isUrl(str));
    }

    /**
     * 判断是否是合法url地址，不匹配 localhost
     */
    public boolean isUrl(String text) {
        return ReUtil.isMatch(urlPattern, text);
    }

    /**
     * 获取更好的异常信息
     */
    private String getBetterExceptionMessage(Exception e) {
        return ExceptionUtil.getRootCause(e).toString();
    }

    /**
     * 脚本配置
     */
    private record ScriptConfig(String name, JSONObject args) {
    }

}
