package plus.junlong.appfork;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
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
import java.io.Serial;
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
    private final Map<String, String> platforms = new LinkedHashMap<>() {
        @Serial
        private static final long serialVersionUID = 1L;

        {
            this.put("windows", "Windows");
            this.put("linux", "Linux");
            this.put("mac", "macOS");
            this.put("android", "Android");
            this.put("extensions", "浏览器扩展");
            this.put("other", "Other");
        }
    };

    // 分类列表
    private final Map<String, String> categories = new LinkedHashMap<>() {
        @Serial
        private static final long serialVersionUID = 1L;

        {
            this.put("network", "网络应用");
            this.put("chat", "社交沟通");
            this.put("music", "音乐欣赏");
            this.put("video", "视频播放");
            this.put("graphics", "图形图像");
            this.put("games", "游戏娱乐");
            this.put("office", "办公学习");
            this.put("reading", "阅读翻译");
            this.put("development", "编程开发");
            this.put("tools", "系统工具");
            this.put("beautify", "主题美化");
            this.put("others", "其他应用");
            this.put("image", "系统镜像");
        }
    };

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
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = manifests.stream()
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            int result = sync(file, groovyClassLoader);
                            counters[result].increment();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
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
        } catch (Exception e) {
            log.error("同步出错: {}", getBetterExceptionMessage(e));
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

            final String code = FileUtil.mainName(manifest).toLowerCase();
            final String name = manifestJson.getString("name");
            final String homepage = manifestJson.getString("homepage");
            // String logo = manifestJson.getString("logo");
            final String author = manifestJson.getString("author");
            final String description = manifestJson.getString("description");
            final String category = manifestJson.getString("category");
            final String platform = manifestJson.getString("platform");
            final String version = manifestJson.getString("version");
            // Object url = manifestJson.get("url");
            final Object scriptValue = manifestJson.get("script");

            if (StrUtil.isBlank(name) ||
                    StrUtil.isBlank(homepage) ||
                    StrUtil.isBlank(author) ||
                    StrUtil.isBlank(description) ||
                    StrUtil.isBlank(category) || !categories.containsKey(category.toLowerCase()) ||
                    StrUtil.isBlank(platform) || !platforms.containsKey(platform.toLowerCase())) {
                log.error("manifest [{}] has illegal attribute", manifest.getName());
                return SYNC_ERROR;
            }

            // 清单文件对应的script脚本文件名 默认为清单文件名一致
            String scriptName = code;
            // 脚本文件执行时的额外参数
            JSONObject scriptArgs = null;
            if (scriptValue instanceof String s) {
                // 如果脚本值是字符串，那么直接作为脚本文件名
                scriptName = s;
            } else if (scriptValue instanceof Map<?, ?> m) {
                // 如果脚本值是Map，那么获取脚本文件名和脚本参数
                if (m.get("name") instanceof String s) {
                    scriptName = s;
                }
                if (m.get("args") instanceof Map<?, ?> argsMap) {
                    scriptArgs = new JSONObject(argsMap);
                }
            }

            // 检查更新脚本
            final File script = new File(repoPath, "scripts" + File.separator + scriptName.toLowerCase() + ".groovy");
            if (!script.exists() || !script.isFile()) {
                return SYNC_NONE;
            }

            // groovy脚本运行
            Class<? extends ScriptUpdater> scriptClass = scriptCache.computeIfAbsent(script.getAbsolutePath(), key -> {
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
            // clone清单文件，避免脚本修改原清单文件内容
            final JSONObject manifestClone = manifestJson.clone();
            // 清除脚本属性
            manifestClone.remove("script");
            // 反射获取执行检测App更新的脚本指定方法并执行
            Object checkUpdateObj = scriptClass.getDeclaredConstructor().newInstance().checkUpdate(manifestClone, scriptArgs);
            if (!(checkUpdateObj instanceof Map<?, ?> checkUpdate)) {
                return SYNC_NONE;
            }

            // 获取脚本返回的错误信息
            if (checkUpdate.containsKey("error")) {
                log.error("exec [{}] script [{}] return error: {}", manifest.getName(), script.getName(), checkUpdate.get("error"));
                return SYNC_ERROR;
            }

            // 有更新的属性名称集合
            Map<String, Object> changedAttrs = new HashMap<>();

            boolean isValidVersion = true;
            if (checkUpdate.get("version") instanceof String checkVersion && !checkVersion.equals(version)) {
                // 版本号有变更
                if (ReUtil.isMatch(versionPattern, checkVersion) && ReUtil.isMatch(versionPattern, version)
                        && StrUtil.compareVersion(checkVersion, version) < 0) {
                    // x.y.z类型版本号比较，如果脚本返回的版本号小于清单文件内的版本号，则跳过
                    log.warn("manifest [{}] version [{}] is greater than check [{}]", manifest.getName(), version, checkVersion);
                    isValidVersion = false;
                } else {
                    // 非语义化版本号有变更，直接更新清单文件属性
                    changedAttrs.put("version", checkVersion);
                }
            }

            // 通过循环获取脚本返回的属性值是否跟已有清单文件内属性值一致
            for (Map.Entry<?, ?> entry : checkUpdate.entrySet()) {
                String key = ((String) entry.getKey()).toLowerCase();
                Object value = entry.getValue();

                if (key.isEmpty() || "version".equals(key) || value == null) {
                    continue;
                }

                // // 比较 JSON 序列化后的值，处理复杂的 Map/List 比较
                String newValJson = JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
                String oldValJson = JSON.toJSONString(manifestJson.get(key), JSONWriter.Feature.SortMapEntriesByKeys);
                if (!newValJson.equals(oldValJson)) {
                    if ("url".equals(key)) {
                        if (!isValidVersion) {
                            // 合法的版本号才去更新 url
                            continue;
                        }
                        // 开始检查链接是否合法
                        boolean correctUrl = switch (value) {
                            // 只有一个链接，直接检查是否是合法链接形式
                            case String updateUrlStr -> isUrl(updateUrlStr);
                            // 有多个链接，循环检查是否是合法链接形式
                            case Map<?, ?> updateUrlMap -> {
                                if (updateUrlMap.isEmpty()) {
                                    yield false;
                                }
                                for (Object v : updateUrlMap.values()) {
                                    if (!(v instanceof String str) || !isUrl(str)) {
                                        yield false;
                                    }
                                }
                                yield true;
                            }
                            // 有多个链接，循环检查是否是合法链接形式
                            case List<?> updateUrlList -> {
                                if (updateUrlList.isEmpty()) {
                                    yield false;
                                }
                                for (Object v : updateUrlList) {
                                    if (!(v instanceof String str) || !isUrl(str)) {
                                        yield false;
                                    }
                                }
                                yield true;
                            }
                            default -> false;
                        };
                        if (!correctUrl) {
                            // 如果链接不合法，那么不更新清单文件内的属性值
                            log.warn("manifest [{}] url [{}] is not a valid url", manifest.getName(), value);
                            // 链接不合法，版本号也不能更新，移除掉
                            changedAttrs.remove("version");
                            continue;
                        }
                    }
                    // 如果脚本返回的属性值跟清单文件内的属性值不一致，那么更新清单文件内的属性值
                    changedAttrs.put(key, value);
                }
            }

            if (changedAttrs.isEmpty()) {
                return SYNC_NONE;
            }

            // 将新版清单内容写入文件
            manifestJson.putAll(changedAttrs);
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
            String manifestVersion = manifestJson.getString("version");
            if (!StrUtil.equals(manifestVersion, version)) {
                // 版本有变更
                log.info("manifest [{}] upgraded: {}->{}", manifest.getName(), version, manifestVersion);
            } else {
                log.info("manifest [{}] attributes updated: {}", manifest.getName(), changedAttrs);
            }
            return SYNC_UPDATE;
        } catch (Exception e) {
            log.error("sync [{}] error: {}", manifest.getName(), getBetterExceptionMessage(e));
            return SYNC_ERROR;
        }
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
        return Optional.ofNullable(e.getCause()).orElse(e).toString();
    }

}
